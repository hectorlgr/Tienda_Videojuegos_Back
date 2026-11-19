package com.ferreteria.auth_service.service;

import com.ferreteria.auth_service.Dto.PerfilResponseDto;
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
        return new PerfilResponseDto(user.getId(), user.getNombre(), user.getEmail(),
                user.getTelefono(), user.getDireccion(), user.getRegion(), user.getComuna());
    }
}
