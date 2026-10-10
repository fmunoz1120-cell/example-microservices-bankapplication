package com.backend.corporate.model;

import jakarta.persistence.*;

@Entity
@Table(name = "catalog_values")
public class CatalogValue extends AuditedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "catalog_type_id", nullable = false)
  public CatalogType catalogType;

  @Column(name = "value_code", nullable = false, length = 50)
  public String code;

  @Column(name = "value_name", nullable = false, length = 100)
  public String name;
}
