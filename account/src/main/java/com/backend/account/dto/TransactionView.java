package com.backend.account.dto;

import com.backend.account.model.Transaction;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionView(
    Long id,
    Long accountId,
    Instant date,
    Long transactionTypeId,
    BigDecimal amount,
    BigDecimal balance,
    Long workAreaId,
    Long reversalOf,
    Long replacementOf,
    boolean reversed) {
  public static TransactionView of(Transaction t) {
    return new TransactionView(
        t.id,
        t.account.getId(),
        t.date,
        t.transactionTypeId,
        t.amount,
        t.balance,
        t.workAreaId,
        t.reversalOf,
        t.replacementOf,
        t.reversed);
  }
}
