package com.ferreteria.auth_service.security;

import java.math.BigDecimal;

import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtUserId {

    private JwtUserId() {
    }

    // Solo convierte el claim; la firma y expiración se validan en Spring Security.
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
