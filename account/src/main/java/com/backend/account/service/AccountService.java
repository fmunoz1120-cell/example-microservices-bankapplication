package com.backend.account.service;

import com.backend.account.dto.*;
import com.backend.account.exception.*;
import com.backend.account.model.*;
import com.backend.account.repository.*;
import jakarta.validation.Validator;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class AccountService {
  private final AccountRepository accounts;
  private final TransactionRepository transactions;
  private final ClientProjectionRepository clients;
  private final Validator validator;
  private final ObjectMapper json;

  public AccountService(
      AccountRepository a,
      TransactionRepository t,
      ClientProjectionRepository c,
      Validator v,
      ObjectMapper j) {
    accounts = a;
    transactions = t;
    clients = c;
    validator = v;
    json = j;
  }

  public Account get(Long id) {
    return org.hibernate.Hibernate.unproxy(
        accounts.findById(id).orElseThrow(() -> new NotFoundException("Cuenta no encontrada")),
        Account.class);
  }

  public Account lock(Long id) {
    return org.hibernate.Hibernate.unproxy(
        accounts.lock(id).orElseThrow(() -> new NotFoundException("Cuenta no encontrada")),
        Account.class);
  }

  public void activeClient(Long id) {
    var c =
        clients
            .findById(id)
            .orElseThrow(() -> new BusinessException("Cliente todavía no sincronizado"));
    if (!c.active) throw new BusinessException("Cliente inactivo");
  }

  public Page<AccountView> list(Pageable p) {
    return accounts.findAll(p).map(AccountView::of);
  }

  public AccountView create(AccountRequest r) {
    return save(new Account(), r);
  }

  public AccountView update(Long id, AccountRequest r) {
    return save(lock(id), r);
  }

  public AccountView patch(Long id, Map<String, Object> patch) {
    if (!Set.of("number", "accountTypeId", "clientId", "initialAmount", "isActive", "workAreaId")
            .containsAll(patch.keySet())
        || patch.values().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException();
    Account a = lock(id);
    var data = new HashMap<String, Object>();
    data.put("number", a.number);
    data.put("accountTypeId", a.accountTypeId);
    data.put("clientId", a.clientId);
    data.put("initialAmount", a.initialAmount);
    data.put("isActive", a.isActive);
    data.put("workAreaId", a.workAreaId);
    data.putAll(patch);
    return save(a, json.convertValue(data, AccountRequest.class));
  }

  private AccountView save(Account a, AccountRequest r) {
    if (!validator.validate(r).isEmpty()) throw new IllegalArgumentException();
    activeClient(r.clientId());
    if (a.id != null && !a.clientId.equals(r.clientId()))
      throw new BusinessException("No se permite cambiar el titular");
    boolean hasTransactions = a.id != null && transactions.existsByAccountId(a.id);
    if (hasTransactions && a.initialAmount.compareTo(r.initialAmount()) != 0)
      throw new BusinessException("Importe inicial inmutable después del primer movimiento");
    a.number = r.number();
    a.accountTypeId = r.accountTypeId();
    a.clientId = r.clientId();
    a.initialAmount = r.initialAmount();
    a.updateActiveState(r.isActive());
    a.workAreaId = r.workAreaId();
    if (a.accountCreatedAt == null) a.accountCreatedAt = java.time.Instant.now();
    if (!hasTransactions) a.balance = r.initialAmount();
    return AccountView.of(accounts.save(a));
  }

  public void delete(Long id) {
    Account a = lock(id);
    a.updateActiveState(false);
  }
}
