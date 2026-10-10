package com.backend.account.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

public record AccountReport(
    Long clientId,
    String clientName,
    LocalDate dateTransactionStart,
    LocalDate dateTransactionEnd,
    List<Entry> accounts) {
  public record Entry(
      Long accountId,
      String number,
      Long accountTypeId,
      Long workAreaId,
      Instant accountCreatedAt,
      boolean isActive,
      BigDecimal currentBalance,
      BigDecimal openingBalance,
      BigDecimal closingBalance,
      List<TransactionView> transactions) {}
}
