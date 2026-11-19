package com.ferreteria.auth_service.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;

@Service
public class JwtService {

    // Spring irá al application.properties y traerá el valor
    @Value("${jwt.secret}")
    private String secretKey;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("jwt.secret debe contener al menos 32 bytes UTF-8");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    // Método para generar un token JWT con el ID persistido, email y rol del usuario
    public String generateToken(long userId, String email, String role) {
        Date ahora = new Date();
        Date expiration = new Date(ahora.getTime() + 1000 * 60 * 60);
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim("userId", userId)
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    // Método para extraer el email del usuario a partir del token JWT
    public String getEmailFromToken(String token) {
        if (token == null || token.isBlank())
            return null;
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .sig().clear().add(Jwts.SIG.HS256).and()
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    // Método para validar un token JWT
    public boolean isValid(String token) {
        if (token == null || token.isBlank())
            return false;
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .sig().clear().add(Jwts.SIG.HS256).and()
                    .build()
                    .parseSignedClaims(jwt);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

}
