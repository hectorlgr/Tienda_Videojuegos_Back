package com.ferreteria.auth_service.service;

import java.nio.charset.StandardCharsets;

import com.ferreteria.auth_service.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class HashService {

    private static final int MAX_PASSWORD_BYTES = 72;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String encode(String password) {
        if (password == null || password.isBlank()) {
            throw new BadRequestException("La contraseña es obligatoria");
        }
        if (exceedsByteLimit(password)) {
            throw new BadRequestException("La contraseña no debe superar 72 bytes en UTF-8");
        }
        return passwordEncoder.encode(password);
    }

    public boolean matches(String password, String encodedPassword) {
        if (password == null || password.isBlank() || exceedsByteLimit(password)) {
            return false;
        }
        try {
            return passwordEncoder.matches(password, encodedPassword);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean exceedsByteLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }
}
