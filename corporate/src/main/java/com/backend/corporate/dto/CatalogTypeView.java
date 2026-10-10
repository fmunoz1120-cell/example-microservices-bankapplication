package com.backend.corporate.dto;

import com.backend.corporate.model.CatalogType;
import java.time.Instant;

public record CatalogTypeView(Long id, String name, Instant createdAt, Instant updatedAt) {
  public static CatalogTypeView of(CatalogType value) {
    return new CatalogTypeView(value.id, value.name, value.createdAt, value.updatedAt);
  }
}
