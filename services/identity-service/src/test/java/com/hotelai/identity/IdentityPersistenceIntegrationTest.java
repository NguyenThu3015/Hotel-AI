package com.hotelai.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = {
      "spring.task.scheduling.enabled=false",
      "identity.bootstrap.admin-email=",
      "management.health.rabbit.enabled=false"
    })
class IdentityPersistenceIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("identity_db")
          .withUsername("identity_user")
          .withPassword("identity_password");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired RoleRepository roles;
  @Autowired PermissionRepository permissions;

  @Test
  void flywaySeedsRequiredRolesAndPermissions() {
    assertThat(roles.findByCode("CUSTOMER")).isPresent();
    assertThat(roles.findByCode("ADMIN")).isPresent();
    assertThat(permissions.findAll())
        .extracting(Permission::getCode)
        .contains("roles:write", "staff:lock");
  }
}
