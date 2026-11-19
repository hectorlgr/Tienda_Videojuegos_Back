package com.ferreteria.auth_service.controller;

import com.ferreteria.auth_service.Dto.PerfilResponseDto;
import com.ferreteria.auth_service.Dto.PerfilPatchRequestDto;
import com.ferreteria.auth_service.exception.UnauthorizedException;
import com.ferreteria.auth_service.security.JwtUserId;
import com.ferreteria.auth_service.service.PerfilService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@Tag(name = "Perfil", description = "Consulta y actualización del perfil de la cuenta autenticada")
public class PerfilController {

    private final PerfilService perfilService;

    @GetMapping("/me")
    @Operation(summary = "Consultar mi perfil")
    public PerfilResponseDto obtenerMiPerfil(@AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtUserId.getValidId(jwt);
        if (userId == null) {
            throw new UnauthorizedException("Credenciales inválidas");
        }
        return perfilService.obtenerPerfil(userId);
    }

    @PatchMapping("/me")
    @Operation(summary = "Actualizar mis datos de contacto y dirección")
    public PerfilResponseDto actualizarMiPerfil(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PerfilPatchRequestDto dto) {
        Long userId = JwtUserId.getValidId(jwt);
        if (userId == null) {
            throw new UnauthorizedException("Credenciales inválidas");
        }
        return perfilService.actualizarPerfil(userId, dto);
    }
}
