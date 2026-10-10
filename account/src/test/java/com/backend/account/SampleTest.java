package com.backend.account;

import static org.assertj.core.api.Assertions.*;

import com.backend.account.dto.*;
import com.backend.account.model.*;
import com.backend.account.repository.*;
import com.backend.account.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
class SampleTest {
  @Autowired AccountService accounts;
  @Autowired TransactionService transactions;
  @Autowired ClientProjectionRepository clients;
  @Autowired TransactionRepository movements;
  @Autowired JdbcTemplate jdbc;
  Long accountId;

  @BeforeEach
  void setup() {
    var c = new ClientProjection();
    c.id = Math.abs(UUID.randomUUID().getMostSignificantBits());
    c.name = "Integration";
    c.active = true;
    clients.saveAndFlush(c);
    accountId =
        accounts
            .create(
                new AccountRequest(
                    UUID.randomUUID().toString().substring(0, 20),
                    201L,
                    c.id,
                    new BigDecimal("100.00"),
                    true,
                    1L))
            .id();
  }

  TransactionView deposit(String amount) {
    return transactions.create(
        new TransactionRequest(accountId, 301L, new BigDecimal(amount), 1L));
  }

  @Test
  void depositWithdrawalReversalAndDuplicateProtection() {
    var d = deposit("50");
    transactions.create(new TransactionRequest(accountId, 302L, new BigDecimal("-20"), 1L));
    var reversal = transactions.delete(d.id());
    assertThat(reversal.reversalOf()).isEqualTo(d.id());
    assertThat(accounts.get(accountId).balance).isEqualByComparingTo("80");
    assertThatThrownBy(() -> transactions.delete(d.id())).hasMessageContaining("ya reversado");
  }

  @Test
  void replacementRollsBackWhenInvalid() {
    var d = deposit("50");
    long before = movements.count();
    assertThatThrownBy(
            () ->
                transactions.update(
                    d.id(),
                    new TransactionRequest(accountId, 302L, new BigDecimal("-101"), 1L)))
        .hasMessage("Saldo no disponible");
    assertThat(accounts.get(accountId).balance).isEqualByComparingTo("150");
    assertThat(movements.count()).isEqualTo(before);
    assertThat(transactions.get(d.id()).reversed()).isFalse();
  }

  @Test
  void validReplacementMaintainsAudit() {
    var d = deposit("50");
    var updated = transactions.patch(d.id(), Map.of("amount", 25));
    assertThat(updated.replacementOf()).isEqualTo(d.id());
    assertThat(transactions.get(d.id()).reversed()).isTrue();
    assertThat(accounts.get(accountId).balance).isEqualByComparingTo("125");
  }

  @Test
  void concurrentWithdrawalsCannotOverdraw() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var ready = new CountDownLatch(2);
      var start = new CountDownLatch(1);
      Callable<Boolean> task =
          () -> {
            ready.countDown();
            start.await();
            try {
              transactions.create(
                  new TransactionRequest(accountId, 302L, new BigDecimal("-80"), 1L));
              return true;
            } catch (com.backend.account.exception.BusinessException e) {
              return false;
            }
          };
      var a = executor.submit(task);
      var b = executor.submit(task);
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(accounts.get(accountId).balance).isEqualByComparingTo("20");
    }
  }

  @Test
  void reportDatesAreInclusiveAndAccountsRemainVisible() {
    var d = deposit("20");
    Long clientId = accounts.get(accountId).clientId;
    accounts.create(
        new AccountRequest(
            UUID.randomUUID().toString().substring(0, 20),
            202L,
            clientId,
            new BigDecimal("30"),
            true,
            1L));
    jdbc.update(
        "UPDATE accounts SET created_at=? WHERE id=?",
        OffsetDateTime.parse("2020-01-01T00:00:00Z"),
        accountId);
    jdbc.update(
        "UPDATE transactions SET transaction_date=? WHERE id=?",
        OffsetDateTime.parse("2020-02-01T23:59:59Z"),
        d.id());
    var report =
        transactions.report(clientId, LocalDate.parse("2020-02-01"), LocalDate.parse("2020-02-01"));
    assertThat(report.accounts()).hasSize(2);
    var entry =
        report.accounts().stream()
            .filter(a -> a.accountId().equals(accountId))
            .findFirst()
            .orElseThrow();
    assertThat(entry.transactions()).hasSize(1);
    assertThat(entry.openingBalance()).isEqualByComparingTo("100");
    assertThat(entry.closingBalance()).isEqualByComparingTo("120");
    var empty =
        transactions.report(clientId, LocalDate.parse("2020-02-02"), LocalDate.parse("2020-02-02"));
    assertThat(empty.accounts().getFirst().transactions()).isEmpty();
  }

  @Test
  void accountInitialAmountAndOwnershipAreProtected() {
    deposit("1");
    accounts.get(accountId);
    assertThatThrownBy(() -> accounts.patch(accountId, Map.of("initialAmount", 200)))
        .hasMessageContaining("inmutable");
    accounts.delete(accountId);
    assertThat(accounts.get(accountId).isActive).isFalse();
    assertThat(accounts.get(accountId).deletedAt).isNotNull();
    assertThat(accounts.get(accountId).createdAt).isNotNull();
    assertThat(accounts.get(accountId).updatedAt).isNotNull();
    assertThatThrownBy(() -> deposit("1")).hasMessage("Cuenta inactiva");
  }
}
