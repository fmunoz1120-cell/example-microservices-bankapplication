package com.backend.client.dto;

import jakarta.validation.constraints.*;

public record ClientRequest(
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @NotBlank @Size(max = 200) String completeName,
    @NotNull @Positive Long dniTypeId,
    @NotBlank @Size(max = 20) String dni,
    @NotBlank @Size(max = 3) String gender,
    @NotNull @Min(0) @Max(150) Integer age,
    @NotBlank @Size(max = 255) String address,
    @NotBlank @Size(max = 20) String phone,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotNull Boolean isActive) {}
