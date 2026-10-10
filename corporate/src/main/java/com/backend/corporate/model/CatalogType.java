package com.backend.corporate.model;

import jakarta.persistence.*;

@Entity
@Table(name = "catalog_types")
public class CatalogType extends AuditedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "catalog_name", nullable = false, length = 100)
  public String name;
}
