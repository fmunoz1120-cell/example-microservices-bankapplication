package com.backend.corporate.dto;

import com.backend.corporate.model.CatalogValue;
import java.time.Instant;

public record CatalogValueView(
    Long id,
    Long catalogTypeId,
    String code,
    String name,
    Instant createdAt,
    Instant updatedAt) {
  public static CatalogValueView of(CatalogValue value) {
    return new CatalogValueView(
        value.id,
        value.catalogType.id,
        value.code,
        value.name,
        value.createdAt,
        value.updatedAt);
  }
}
