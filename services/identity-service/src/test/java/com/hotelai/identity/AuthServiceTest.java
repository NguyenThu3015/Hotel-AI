package com.hotelai.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
  private final AccountRepository accounts = mock(AccountRepository.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
  private final OtpChallengeRepository challenges = mock(OtpChallengeRepository.class);
  private final IdempotencyRepository idempotency = mock(IdempotencyRepository.class);
  private final PasswordEncoder passwords = mock(PasswordEncoder.class);
  private final JwtService jwt = mock(JwtService.class);
  private final RateLimiter rateLimiter = mock(RateLimiter.class);
  private final OutboxService outbox = mock(OutboxService.class);
  private final SessionRevocationService revocations = mock(SessionRevocationService.class);
  private final EntityManager entityManager = mock(EntityManager.class);
  private final IdentityProperties properties = new IdentityProperties("HOTEL-1", null, null);
  private AuthService service;

  @BeforeEach
  void setUp() {
    service =
        new AuthService(
            accounts,
            roles,
            sessions,
            challenges,
            idempotency,
            passwords,
            jwt,
            rateLimiter,
            outbox,
            revocations,
            properties,
            entityManager);
    when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(challenges.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(passwords.encode(anyString())).thenReturn("encoded");
  }

  @Test
  void registrationStaysPendingAndProducesOtpChallenge() {
    when(accounts.existsByEmailIgnoreCase("guest@example.com")).thenReturn(false);

    AuthService.RegistrationChallenge response =
        service.register(
            " Guest@Example.com ",
            "StrongPass1!",
            "Guest User",
            "0900000000",
            "register-1",
            new AuthService.ClientContext("127.0.0.1", "JUnit"));

    ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
    verify(accounts).save(accountCaptor.capture());
    Account account = accountCaptor.getValue();
    assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
    assertThat(account.getRoles()).isEmpty();
    assertThat(response.challengeId()).isNotNull();
    assertThat(response.maskedDestination()).isEqualTo("g***@example.com");
    verify(outbox).append(eq("OtpChallenge"), any(), eq("notification.otp.requested.v1"), anyMap());
    @SuppressWarnings("rawtypes")
    ArgumentCaptor<Map> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(outbox)
        .append(
            eq("OtpChallenge"),
            any(),
            eq("notification.otp.requested.v1"),
            payloadCaptor.capture());
    assertThat(payloadCaptor.getValue())
        .containsEntry("purpose", "ACCOUNT_ACTIVATION")
        .containsEntry("templateCode", "OTP_ACCOUNT_ACTIVATION");
    Map<?, ?> variables = (Map<?, ?>) payloadCaptor.getValue().get("variables");
    assertThat(variables.containsKey("otp")).isTrue();
    assertThat(variables.containsKey("expiresAt")).isTrue();
    verify(outbox)
        .append(eq("Account"), eq(account.getId()), eq("identity.account.registered.v1"), anyMap());
  }

  @Test
  void verificationActivatesCustomerAndCreatesSessionTokens() {
    Role customer = new Role("CUSTOMER", "Customer", null, Set.of());
    Account account = Account.pendingCustomer("guest@example.com", "encoded", "Guest", null);
    OtpChallenge challenge =
        new OtpChallenge(
            account,
            OtpPurpose.VERIFY_EMAIL,
            Hashing.sha256("123456"),
            java.time.Instant.now().plusSeconds(600));
    when(challenges.findById(challenge.getId())).thenReturn(Optional.of(challenge));
    when(roles.findByCode("CUSTOMER")).thenReturn(Optional.of(customer));
    when(jwt.issue(any(), any())).thenReturn("access.jwt");

    AuthService.Tokens tokens =
        service.verifyRegistration(
            challenge.getId(), "123456", new AuthService.ClientContext("127.0.0.1", "JUnit"));

    assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(account.isEmailVerified()).isTrue();
    assertThat(account.hasRole("CUSTOMER")).isTrue();
    assertThat(challenge.getUsedAt()).isNotNull();
    assertThat(tokens.accessToken()).isEqualTo("access.jwt");
    assertThat(tokens.refreshToken()).isNotBlank();
    verify(outbox)
        .append(eq("Account"), eq(account.getId()), eq("identity.account.activated.v1"), anyMap());
  }

  @Test
  void reusedIdempotencyKeyRejectsAChangedPassword() {
    when(accounts.existsByEmailIgnoreCase("guest@example.com")).thenReturn(false);
    service.register(
        "guest@example.com",
        "StrongPass1!",
        "Guest User",
        null,
        "same-key",
        new AuthService.ClientContext("127.0.0.1", "JUnit"));
    ArgumentCaptor<IdempotencyRecord> record = ArgumentCaptor.forClass(IdempotencyRecord.class);
    verify(idempotency).save(record.capture());
    when(idempotency.findByOperationAndIdempotencyKey("register", "same-key"))
        .thenReturn(Optional.of(record.getValue()));

    assertThatThrownBy(
            () ->
                service.register(
                    "guest@example.com",
                    "DifferentPass2!",
                    "Guest User",
                    null,
                    "same-key",
                    new AuthService.ClientContext("127.0.0.1", "JUnit")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Idempotency-Key");
  }

  @Test
  void logoutRevokesTheAccessSessionAtTheGatewayBoundary() {
    Role customer = new Role("CUSTOMER", "Customer", null, Set.of());
    Account account = Account.pendingCustomer("guest@example.com", "encoded", "Guest", null);
    account.activateCustomer(customer);
    AuthSession session =
        new AuthSession(
            account,
            Hashing.sha256("refresh"),
            java.time.Instant.now().plusSeconds(3600),
            null,
            null);
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

    service.logout(null, session.getId());

    assertThat(session.getRevokedAt()).isNotNull();
    verify(revocations).revokeSession(session.getId(), "LOGOUT");
    verify(sessions).save(session);
  }
}
