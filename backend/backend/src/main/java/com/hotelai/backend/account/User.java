package com.hotelai.backend.account;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "identity_id", unique = true)
  private UUID identityId;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  private String phone;

  @Column(nullable = false)
  private String status = "ACTIVE";

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false)
  private Instant updatedAt;

  @Version private long version;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<Role> roles = new HashSet<>();

  public User(String email, String passwordHash, String fullName, String phone, Role role) {
    this.email = email.toLowerCase();
    this.passwordHash = passwordHash;
    this.fullName = fullName;
    this.phone = phone;
    this.roles.add(role);
  }

  public void login() {
    this.lastLoginAt = Instant.now();
  }

  public void updateProfile(String fullName, String phone) {
    this.fullName = fullName;
    this.phone = phone;
    this.updatedAt = Instant.now();
  }

  public void changePassword(String hash) {
    this.passwordHash = hash;
    this.updatedAt = Instant.now();
  }

  public void setStatus(String status) {
    this.status = status;
    this.updatedAt = Instant.now();
  }

  public void verifyEmail() {
    this.emailVerified = true;
  }

  public void synchronizeIdentity(
      UUID identityId,
      String email,
      String fullName,
      String phone,
      String status,
      boolean emailVerified,
      Set<Role> roles) {
    this.identityId = identityId;
    this.email = email.toLowerCase();
    this.fullName = fullName;
    this.phone = phone;
    this.status = status;
    this.emailVerified = emailVerified;
    this.roles.clear();
    this.roles.addAll(roles);
    this.updatedAt = Instant.now();
  }
}
