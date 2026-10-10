package com.backend.client.dto;

import com.backend.client.model.Client;

public record ClientView(
    Long id,
    String firstName,
    String lastName,
    String completeName,
    Long dniTypeId,
    String dni,
    String gender,
    Integer age,
    String address,
    String phone,
    boolean isActive) {
  public static ClientView of(Client c) {
    return new ClientView(
        c.id,
        c.firstName,
        c.lastName,
        c.completeName,
        c.dniTypeId,
        c.dni,
        c.gender,
        c.age,
        c.address,
        c.phone,
        c.isActive);
  }
}
