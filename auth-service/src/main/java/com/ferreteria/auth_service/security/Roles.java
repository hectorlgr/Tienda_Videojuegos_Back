package com.ferreteria.auth_service.security;

import java.util.Set;

public final class Roles {
    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String CLIENTE = "CLIENTE";
    private static final Set<String> VALIDOS = Set.of(ADMINISTRADOR, "VENDEDOR", CLIENTE);

    private Roles() {
    }

    public static boolean esValido(Object role) {
        return role instanceof String value && VALIDOS.contains(value);
    }
}
