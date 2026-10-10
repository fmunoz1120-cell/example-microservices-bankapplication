package com.backend.corporate.dto;

import jakarta.validation.constraints.*;

public record CatalogValueRequest(
    @NotNull @Positive Long catalogTypeId,
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 100) String name) {}
