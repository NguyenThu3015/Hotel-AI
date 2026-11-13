package com.hotelai.identity;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
class BootstrapAdmin implements ApplicationRunner {
  private final AccountRepository accounts;
  private final RoleRepository roles;
  private final PasswordEncoder passwords;
  private final IdentityProperties properties;
  private final OutboxService outbox;

  @Value("${identity.bootstrap.admin-email:}")
  private String email;

  @Value("${identity.bootstrap.admin-password:}")
  private String password;

  @Value("${identity.bootstrap.admin-name:System Administrator}")
  private String name;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (email == null || email.isBlank()) {
      log.info("Bootstrap administrator is disabled");
      return;
    }
    if (password == null || password.length() < 10) {
      throw new IllegalStateException(
          "BOOTSTRAP_ADMIN_PASSWORD must contain at least 10 characters");
    }
    if (accounts.existsByEmailIgnoreCase(email)) return;
    Role admin =
        roles
            .findByCode("ADMIN")
            .orElseThrow(() -> new IllegalStateException("ADMIN role missing"));
    Account account =
        accounts.save(
            Account.staff(
                email.trim().toLowerCase(),
                passwords.encode(password),
                name,
                null,
                properties.hotelId(),
                Set.of(admin)));
    outbox.append(
        "Account",
        account.getId(),
        "identity.staff.created.v1",
        IdentityEventPayloads.staffSnapshot(account, account.getId(), properties.hotelId()));
    log.warn(
        "Created bootstrap administrator {}. Remove bootstrap credentials after first use.", email);
  }
}
