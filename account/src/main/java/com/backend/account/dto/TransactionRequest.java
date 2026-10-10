package com.backend.account.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TransactionRequest(
    @NotNull @Positive Long accountId,
    @NotNull @Positive Long transactionTypeId,
    @NotNull @Digits(integer = 17, fraction = 2) BigDecimal amount,
    @NotNull @Positive Long workAreaId) {}
