package com.ferreteria.venta_service.security;

import java.math.BigDecimal;
import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtUserId {
    private JwtUserId() {
    }

    // Convierte el claim; la firma, expiración y rol se verifican en SecurityConfig.
    public static Long getValidId(Jwt jwt) {
        if (jwt != null && jwt.getClaims().get("userId") instanceof Number number) {
            try {
                long id = new BigDecimal(number.toString()).longValueExact();
                return id > 0 ? id : null;
            } catch (IllegalArgumentException | ArithmeticException e) {
                return null;
            }
        }
        return null;
    }
}
