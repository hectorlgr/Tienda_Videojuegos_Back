package com.ferreteria.auth_service.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PerfilResponseDto {
    private final Long id;
    private final String nombre;
    private final String email;
    private final String telefono;
    private final String direccion;
    private final String region;
    private final String comuna;
}
