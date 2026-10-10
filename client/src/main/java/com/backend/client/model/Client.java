package com.backend.client.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "clients")
@PrimaryKeyJoinColumn(name = "id", referencedColumnName = "id")
public class Client extends Person {
  @Column(nullable = false)
  public String password;

  @Column(nullable = false)
  public boolean isActive = true;

  @Column(name = "deleted_at")
  public Instant deletedAt;

  public void updateActiveState(boolean active) {
    isActive = active;
    if (active) {
      deletedAt = null;
    } else if (deletedAt == null) {
      deletedAt = Instant.now();
    }
  }

  public void deactivate() {
    updateActiveState(false);
  }
}
