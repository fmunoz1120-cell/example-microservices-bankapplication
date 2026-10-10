package com.backend.account.dto;

import com.backend.account.model.Account;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountView(
    Long id,
    String number,
    Long accountTypeId,
    Long clientId,
    BigDecimal initialAmount,
    BigDecimal balance,
    boolean isActive,
    Instant accountCreatedAt,
    Long workAreaId) {
  public static AccountView of(Account a) {
    return new AccountView(
        a.id,
        a.number,
        a.accountTypeId,
        a.clientId,
        a.initialAmount,
        a.balance,
        a.isActive,
        a.accountCreatedAt,
        a.workAreaId);
  }
}
