package com.backend.corporate.dto;

import jakarta.validation.constraints.*;

public record CatalogTypeRequest(@NotBlank @Size(max = 100) String name) {}
