package com.ferreteria.auth_service.service;

import com.ferreteria.auth_service.Dto.PerfilResponseDto;
import com.ferreteria.auth_service.Dto.PerfilPatchRequestDto;
import com.ferreteria.auth_service.exception.ResourceNotFoundException;
import com.ferreteria.auth_service.model.User;
import com.ferreteria.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PerfilResponseDto obtenerPerfil(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("La cuenta no existe"));
        return toResponse(user);
    }

    @Transactional
    public PerfilResponseDto actualizarPerfil(long userId, PerfilPatchRequestDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("La cuenta no existe"));
        if (dto.getTelefono() != null) {
            user.setTelefono(emptyToNull(dto.getTelefono()));
        }
        if (dto.getDireccion() != null) {
            user.setDireccion(emptyToNull(dto.getDireccion()));
        }
        if (dto.getRegion() != null) {
            user.setRegion(emptyToNull(dto.getRegion()));
        }
        if (dto.getComuna() != null) {
            user.setComuna(emptyToNull(dto.getComuna()));
        }
        userRepository.saveAndFlush(user);
        return toResponse(user);
    }

    private String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private PerfilResponseDto toResponse(User user) {
        return new PerfilResponseDto(user.getId(), user.getNombre(), user.getEmail(),
                user.getTelefono(), user.getDireccion(), user.getRegion(), user.getComuna());
    }
}
