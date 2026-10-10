package com.backend.account.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record AccountRequest(
    @NotBlank @Size(max = 20) String number,
    @NotNull @Positive Long accountTypeId,
    @NotNull @Positive Long clientId,
    @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal initialAmount,
    @NotNull Boolean isActive,
    @NotNull @Positive Long workAreaId) {}
