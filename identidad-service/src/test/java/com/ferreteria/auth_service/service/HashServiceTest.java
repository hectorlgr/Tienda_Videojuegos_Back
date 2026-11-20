package com.ferreteria.auth_service.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

public class HashServiceTest {
    private final HashService hashService = new HashService();

    @Test
    void testBCrypt_HasheaCorrectamente() {
        // GIVEN
        String passwordPura = "123456";

        // WHEN
        String resultado = hashService.encode(passwordPura);

        // THEN
        assertNotNull(resultado);
        assertTrue(hashService.matches(passwordPura, resultado));
    }
}
