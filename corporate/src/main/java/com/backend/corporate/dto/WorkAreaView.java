package com.backend.corporate.dto;

import com.backend.corporate.model.WorkArea;
import java.time.Instant;

public record WorkAreaView(
    Long id,
    String name,
    String address,
    String city,
    Long areaTypeId,
    Instant createdAt,
    Instant updatedAt) {
  public static WorkAreaView of(WorkArea area) {
    return new WorkAreaView(
        area.id,
        area.name,
        area.address,
        area.city,
        area.areaType.id,
        area.createdAt,
        area.updatedAt);
  }
}
