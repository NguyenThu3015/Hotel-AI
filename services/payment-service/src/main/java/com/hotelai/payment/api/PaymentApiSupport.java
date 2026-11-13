package com.hotelai.payment.api;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

final class PaymentApiSupport {
    private PaymentApiSupport() {}

    static UUID actor(Jwt jwt) {
        String uid = jwt.getClaimAsString("uid");
        if (uid == null || uid.isBlank()) uid = jwt.getSubject();
        return UUID.fromString(uid);
    }

    static boolean staff(Jwt jwt) {
        return jwt.getClaimAsStringList("roles") != null
                && jwt.getClaimAsStringList("roles").stream()
                        .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                        .anyMatch(value -> value.equals("RECEPTIONIST")
                                || value.equals("MANAGER")
                                || value.equals("ADMIN"));
    }
}
