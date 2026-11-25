package com.ferreteria.auth_service.controller;

import com.ferreteria.auth_service.Dto.AdminUsuarioRequestDto;
import com.ferreteria.auth_service.Dto.AdminUsuarioResponseDto;
import com.ferreteria.auth_service.service.AdminUsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/usuarios")
@RequiredArgsConstructor
@Tag(name = "Administración de usuarios", description = "Listado de usuarios y creación de cuentas de personal")
public class AdminUsuarioController {

    private final AdminUsuarioService adminUsuarioService;

    @GetMapping
    @Operation(summary = "Listar usuarios registrados", description = "Requiere el rol ADMINISTRADOR")
    public List<AdminUsuarioResponseDto> listarUsuarios() {
        return adminUsuarioService.listarUsuarios();
    }

    @PostMapping
    @Operation(summary = "Crear una cuenta VENDEDOR o ADMINISTRADOR")
    public ResponseEntity<AdminUsuarioResponseDto> crearUsuario(@Valid @RequestBody AdminUsuarioRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUsuarioService.crearUsuario(dto));
    }
}
