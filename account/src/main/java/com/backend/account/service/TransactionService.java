package com.backend.account.service;

import com.backend.account.dto.*;
import com.backend.account.exception.*;
import com.backend.account.model.*;
import com.backend.account.repository.*;
import jakarta.validation.Validator;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class TransactionService {
  private static final long DEPOSIT_TYPE_ID = 301L;
  private static final long WITHDRAWAL_TYPE_ID = 302L;
  private final AccountService accounts;
  private final AccountRepository accountRepo;
  private final TransactionRepository repo;
  private final ClientProjectionRepository clients;
  private final Validator validator;
  private final ObjectMapper json;

  public TransactionService(
      AccountService a,
      AccountRepository ar,
      TransactionRepository r,
      ClientProjectionRepository c,
      Validator v,
      ObjectMapper j) {
    accounts = a;
    accountRepo = ar;
    repo = r;
    clients = c;
    validator = v;
    json = j;
  }

  private Transaction find(Long id) {
    return repo.findById(id).orElseThrow(() -> new NotFoundException("Movimiento no encontrado"));
  }

  public TransactionView get(Long id) {
    return TransactionView.of(find(id));
  }

  public Page<TransactionView> list(Long accountId, Pageable p) {
    return (accountId == null ? repo.findAll(p) : repo.findByAccountId(accountId, p))
        .map(TransactionView::of);
  }

  public TransactionView create(TransactionRequest r) {
    validate(r);
    Account a = accounts.lock(r.accountId());
    checkActive(a);
    return TransactionView.of(
        append(a, r.transactionTypeId(), r.amount(), r.workAreaId(), null, null));
  }

  public TransactionView update(Long id, TransactionRequest r) {
    validate(r);
    return replace(id, r);
  }

  public TransactionView patch(Long id, Map<String, Object> patch) {
    if (!Set.of("accountId", "transactionTypeId", "amount", "workAreaId")
            .containsAll(patch.keySet())
        || patch.values().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException();
    // Read only the account id before locking; refresh the original after locking below.
    Long accountId = find(id).account.getId();
    accounts.lock(accountId);
    Transaction t = refresh(id);
    var data = new HashMap<String, Object>();
    data.put("accountId", accountId);
    data.put("transactionTypeId", t.transactionTypeId);
    data.put("amount", t.amount);
    data.put("workAreaId", t.workAreaId);
    data.putAll(patch);
    return update(id, json.convertValue(data, TransactionRequest.class));
  }

  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

  private Transaction refresh(Long id) {
    Transaction t = find(id);
    em.refresh(t);
    return t;
  }

  private TransactionView replace(Long id, TransactionRequest r) {
    Long accountId = find(id).account.getId();
    if (!accountId.equals(r.accountId()))
      throw new BusinessException("No se permite cambiar la cuenta");
    Account a = accounts.lock(accountId);
    Transaction t = refresh(id);
    checkActive(a);
    checkReversible(t);
    // Both ledger entries are atomic, and neither may leave a negative intermediate balance.
    append(
        a,
        oppositeType(t.amount),
        t.amount.negate(),
        t.workAreaId,
        t.id,
        null);
    t.reversed = true;
    return TransactionView.of(
        append(a, r.transactionTypeId(), r.amount(), r.workAreaId(), null, t.id));
  }

  public TransactionView delete(Long id) {
    Long accountId = find(id).account.getId();
    Account a = accounts.lock(accountId);
    Transaction t = refresh(id);
    checkActive(a);
    checkReversible(t);
    Transaction reversal =
        append(a, oppositeType(t.amount), t.amount.negate(), t.workAreaId, t.id, null);
    t.reversed = true;
    return TransactionView.of(reversal);
  }

  private void checkReversible(Transaction t) {
    if (t.reversed || t.reversalOf != null)
      throw new BusinessException("Movimiento ya reversado o compensatorio");
  }

  private void checkActive(Account a) {
    if (!a.isActive) throw new BusinessException("Cuenta inactiva");
    accounts.activeClient(a.clientId);
  }

  private void validate(TransactionRequest r) {
    if (!validator.validate(r).isEmpty()
        || r.amount().signum() == 0
        || !isKnownType(r.transactionTypeId())
        || ((r.transactionTypeId() == DEPOSIT_TYPE_ID) != (r.amount().signum() > 0)))
      throw new IllegalArgumentException();
  }

  private Transaction append(
      Account a,
      Long transactionTypeId,
      BigDecimal amount,
      Long workAreaId,
      Long reversalOf,
      Long replacementOf) {
    BigDecimal balance = a.balance.add(amount).setScale(2, RoundingMode.UNNECESSARY);
    if (balance.signum() < 0) throw new BusinessException("Saldo no disponible");
    if (balance.precision() > 19) throw new BusinessException("Saldo fuera del límite permitido");
    Transaction t = new Transaction();
    t.account = a;
    t.date = Instant.now();
    t.transactionTypeId = transactionTypeId;
    t.workAreaId = workAreaId;
    t.amount = amount;
    t.balance = balance;
    t.reversalOf = reversalOf;
    t.replacementOf = replacementOf;
    a.balance = balance;
    return repo.saveAndFlush(t);
  }

  private static boolean isKnownType(Long typeId) {
    return typeId == DEPOSIT_TYPE_ID || typeId == WITHDRAWAL_TYPE_ID;
  }

  private static long oppositeType(BigDecimal amount) {
    return amount.signum() > 0 ? WITHDRAWAL_TYPE_ID : DEPOSIT_TYPE_ID;
  }

  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public AccountReport report(Long clientId, LocalDate start, LocalDate end) {
    if (start == null || end == null || end.isBefore(start)) throw new IllegalArgumentException();
    var client =
        clients
            .findById(clientId)
            .orElseThrow(() -> new NotFoundException("Cliente no encontrado"));
    Instant from = start.atStartOfDay(ZoneOffset.UTC).toInstant(),
        until = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    var entries =
        accountRepo.findByClientIdOrderById(clientId).stream()
            .map(
                a -> {
                  var movements =
                      repo
                          .findByAccountIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAscIdAsc(
                              a.id, from, until)
                          .stream()
                          .map(TransactionView::of)
                          .toList();
                  BigDecimal opening =
                      repo.findFirstByAccountIdAndDateLessThanOrderByDateDescIdDesc(a.id, from)
                          .map(t -> t.balance)
                          .orElse(
                              a.createdAt.isBefore(from)
                                  ? a.initialAmount
                                  : BigDecimal.ZERO.setScale(2));
                  BigDecimal closing =
                      repo.findFirstByAccountIdAndDateLessThanOrderByDateDescIdDesc(a.id, until)
                          .map(t -> t.balance)
                          .orElse(
                              a.createdAt.isBefore(until)
                                  ? a.initialAmount
                                  : BigDecimal.ZERO.setScale(2));
                  return new AccountReport.Entry(
                      a.id,
                      a.number,
                      a.accountTypeId,
                      a.workAreaId,
                      a.accountCreatedAt,
                      a.isActive,
                      a.balance,
                      opening,
                      closing,
                      movements);
                })
            .toList();
    return new AccountReport(clientId, client.name, start, end, entries);
  }
}
