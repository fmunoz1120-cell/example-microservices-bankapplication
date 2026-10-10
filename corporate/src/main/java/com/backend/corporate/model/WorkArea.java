package com.backend.corporate.model;

import jakarta.persistence.*;

@Entity
@Table(name = "work_areas")
public class WorkArea extends AuditedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "area_name", nullable = false, length = 100)
  public String name;

  @Column(name = "area_address", nullable = false, length = 255)
  public String address;

  @Column(nullable = false, length = 100)
  public String city;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "area_type_id", nullable = false)
  public CatalogValue areaType;
}
