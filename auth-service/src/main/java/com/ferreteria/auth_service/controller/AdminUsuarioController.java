package com.ferreteria.auth_service.controller;

import com.ferreteria.auth_service.Dto.AdminUsuarioRequestDto;
import com.ferreteria.auth_service.Dto.AdminUsuarioResponseDto;
import com.ferreteria.auth_service.service.AdminUsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/usuarios")
@RequiredArgsConstructor
@Tag(name = "Alta de personal", description = "Creación de cuentas de personal por un administrador autenticado")
public class AdminUsuarioController {

    private final AdminUsuarioService adminUsuarioService;

    @PostMapping
    @Operation(summary = "Crear una cuenta VENDEDOR o ADMINISTRADOR")
    public ResponseEntity<AdminUsuarioResponseDto> crearUsuario(@Valid @RequestBody AdminUsuarioRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUsuarioService.crearUsuario(dto));
    }
}
