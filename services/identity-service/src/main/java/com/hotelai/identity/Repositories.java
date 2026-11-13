package com.hotelai.identity;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AccountRepository extends JpaRepository<Account, UUID> {
  @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  Optional<Account> findByEmailIgnoreCase(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  @Query("select a from Account a where lower(a.email) = lower(:email)")
  Optional<Account> findForAuthentication(@Param("email") String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  @Query("select a from Account a where a.id = :id")
  Optional<Account> findForUpdateById(@Param("id") UUID id);

  @Modifying(flushAutomatically = true)
  @Query(
      "update Account a set a.failedLoginAttempts = 0, a.lockedUntil = null, a.lastLoginAt = :now, a.updatedAt = :now, a.version = a.version + 1 where a.id = :id")
  int markLoginSucceeded(@Param("id") UUID id, @Param("now") Instant now);

  @Modifying(flushAutomatically = true)
  @Query(
      "update Account a set a.failedLoginAttempts = a.failedLoginAttempts + 1, a.lockedUntil = case when a.failedLoginAttempts + 1 >= 5 then :lockedUntil else a.lockedUntil end, a.updatedAt = :now, a.version = a.version + 1 where a.id = :id")
  int markLoginFailed(
      @Param("id") UUID id,
      @Param("now") Instant now,
      @Param("lockedUntil") Instant lockedUntil);

  @Override
  @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  Optional<Account> findById(UUID id);

  boolean existsByEmailIgnoreCase(String email);

  @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  Page<Account> findAllByType(AccountType type, Pageable pageable);

  @Query(
      "select count(a) from Account a join a.roles r where a.type = com.hotelai.identity.AccountType.STAFF and a.status = com.hotelai.identity.AccountStatus.ACTIVE and a.hotelId = :hotelId and r.code = 'ADMIN'")
  long countActiveAdmins(@Param("hotelId") String hotelId);
}

interface RoleRepository extends JpaRepository<Role, UUID> {
  @EntityGraph(attributePaths = "permissions")
  Optional<Role> findByCode(String code);

  @EntityGraph(attributePaths = "permissions")
  List<Role> findAllByCodeIn(Set<String> codes);

  boolean existsByCode(String code);
}

interface PermissionRepository extends JpaRepository<Permission, UUID> {
  List<Permission> findAllByCodeIn(Set<String> codes);
}

interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
  Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

  @Query("select s from AuthSession s where s.account.id = :accountId and s.revokedAt is null")
  List<AuthSession> findActiveByAccountId(@Param("accountId") UUID accountId);
}

interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {
  Optional<OtpChallenge> findFirstByAccountIdAndPurposeOrderByCreatedAtDesc(
      UUID accountId, OtpPurpose purpose);
}

interface RateLimitRepository extends JpaRepository<RateLimit, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RateLimit> findBySubjectHashAndAction(String subjectHash, String action);
}

interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {
  Optional<IdempotencyRecord> findByOperationAndIdempotencyKey(String operation, String key);
}

interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
  @Query(
      "select e from OutboxEvent e where not exists (select d from OutboxDelivery d where d.eventId = e.id and d.publishedAt is not null) order by e.occurredAt")
  List<OutboxEvent> findPending(Pageable pageable);
}

interface OutboxDeliveryRepository extends JpaRepository<OutboxDelivery, UUID> {}
