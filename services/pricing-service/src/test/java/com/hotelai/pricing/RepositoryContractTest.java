package com.hotelai.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

@DataJpaTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:pricing;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false"
    })
@AutoConfigureTestDatabase(replace = Replace.NONE)
class RepositoryContractTest {
  @Autowired private BaseRateVersionRepository baseRates;
  @Autowired private PolicyVersionRepository policies;

  @Test
  void applicableRateAndRoomSpecificPolicyQueriesUseExclusiveEndAndScopePrecedence() {
    UUID roomTypeId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    LocalDate start = LocalDate.now().plusDays(2);
    Instant effective = Instant.now().minusSeconds(60);
    baseRates.saveAndFlush(
        new BaseRateVersion(
            roomTypeId,
            1,
            "VND",
            new BigDecimal("900000"),
            start,
            start.plusDays(2),
            effective,
            null,
            false,
            actorId));
    policies.saveAndFlush(
        new PolicyVersion(
            UUID.randomUUID(),
            1,
            PolicyType.DEPOSIT,
            PolicyScope.GLOBAL,
            null,
            "Global deposit",
            "Deposit",
            "{\"required\":false}",
            effective,
            null,
            actorId));
    PolicyVersion roomPolicy =
        policies.saveAndFlush(
            new PolicyVersion(
                UUID.randomUUID(),
                1,
                PolicyType.DEPOSIT,
                PolicyScope.ROOM_TYPE,
                roomTypeId,
                "Room deposit",
                "Room deposit",
                "{\"required\":true,\"depositType\":\"PERCENT\",\"depositValue\":20}",
                effective,
                null,
                actorId));

    assertThat(baseRates.findApplicable(roomTypeId, start, Instant.now())).hasSize(1);
    assertThat(baseRates.findApplicable(roomTypeId, start.plusDays(2), Instant.now())).isEmpty();
    assertThat(policies.findEffective(PolicyType.DEPOSIT, roomTypeId, Instant.now()))
        .isNotEmpty()
        .first()
        .extracting(PolicyVersion::getId)
        .isEqualTo(roomPolicy.getId());
  }
}
