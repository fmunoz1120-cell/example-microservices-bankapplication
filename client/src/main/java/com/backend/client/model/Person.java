package com.backend.client.model;

import jakarta.persistence.*;

@Entity
@Table(name = "persons")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Person extends AuditedEntity {
  @Version public long version;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "first_name", nullable = false, length = 100)
  public String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  public String lastName;

  @Column(name = "complete_name", nullable = false, length = 200)
  public String completeName;

  @Column(name = "dni_type_id", nullable = false)
  public Long dniTypeId;

  @Column(nullable = false, unique = true, length = 20)
  public String dni;

  @Column(nullable = false, length = 3)
  public String gender;

  @Column(nullable = false)
  public Integer age;

  @Column(nullable = false)
  public String address;

  @Column(nullable = false, length = 20)
  public String phone;
}
