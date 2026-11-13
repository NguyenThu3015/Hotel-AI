package com.hotelai.backend.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdmin implements ApplicationRunner {
  private final UserRepository users;
  private final RoleRepository roles;
  private final PasswordEncoder passwordEncoder;
  private final String email;
  private final String password;

  public BootstrapAdmin(
      UserRepository users,
      RoleRepository roles,
      PasswordEncoder passwordEncoder,
      @Value("${hotel.bootstrap.admin-email:}") String email,
      @Value("${hotel.bootstrap.admin-password:}") String password) {
    this.users = users;
    this.roles = roles;
    this.passwordEncoder = passwordEncoder;
    this.email = email;
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (email.isBlank() || password.isBlank() || users.findByEmailIgnoreCase(email).isPresent())
      return;
    Role admin = roles.findByName("ADMIN").orElseThrow();
    users.save(new User(email, passwordEncoder.encode(password), "Quản trị HotelAI", null, admin));
  }
}
