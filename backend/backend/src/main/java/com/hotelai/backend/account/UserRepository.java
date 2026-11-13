package com.hotelai.backend.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmailIgnoreCase(String email);

  Optional<User> findByIdentityId(UUID identityId);

  boolean existsByEmailIgnoreCase(String email);
}
