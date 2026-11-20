package com.ferreteria.auth_service.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminUsuarioResponseDto {
    private final Long id;
    private final String nombre;
    private final String email;
    private final String role;
}
