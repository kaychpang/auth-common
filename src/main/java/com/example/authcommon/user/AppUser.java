package com.example.authcommon.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {

  @Id
  private UUID id;

  @Column(name = "auth0_user_id", nullable = false, unique = true)
  private String auth0UserId;

  private String email;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AppUser() {
  }

  public AppUser(String auth0UserId, String email, boolean active) {
    this.id = UUID.randomUUID();
    this.auth0UserId = auth0UserId;
    this.email = email;
    this.active = active;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getAuth0UserId() {
    return auth0UserId;
  }

  public String getEmail() {
    return email;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
