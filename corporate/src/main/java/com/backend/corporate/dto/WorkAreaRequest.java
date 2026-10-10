package com.backend.corporate.dto;

import jakarta.validation.constraints.*;

public record WorkAreaRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 255) String address,
    @NotBlank @Size(max = 100) String city,
    @NotNull @Positive Long areaTypeId) {}
