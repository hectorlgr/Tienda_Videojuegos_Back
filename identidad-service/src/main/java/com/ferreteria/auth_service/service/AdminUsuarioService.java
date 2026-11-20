package com.ferreteria.auth_service.service;

import com.ferreteria.auth_service.Dto.AdminUsuarioRequestDto;
import com.ferreteria.auth_service.Dto.AdminUsuarioResponseDto;
import com.ferreteria.auth_service.exception.BadRequestException;
import com.ferreteria.auth_service.model.User;
import com.ferreteria.auth_service.repository.UserRepository;
import com.ferreteria.auth_service.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUsuarioService {

    private final UserRepository userRepository;
    private final HashService hashService;

    @Transactional
    public AdminUsuarioResponseDto crearUsuario(AdminUsuarioRequestDto dto) {
        if (!Roles.ADMINISTRADOR.equals(dto.getRole()) && !Roles.VENDEDOR.equals(dto.getRole())) {
            throw new BadRequestException("El rol debe ser ADMINISTRADOR o VENDEDOR");
        }
        if (userRepository.findByEmail(dto.getEmail()) != null) {
            throw new BadRequestException("El usuario con este email ya existe");
        }

        User user = new User();
        user.setNombre(dto.getNombre());
        user.setEmail(dto.getEmail());
        user.setPassword(hashService.encode(dto.getPassword()));
        user.setRole(dto.getRole());
        // UNIQUE y el handler de integridad cubren también correos concurrentes.
        User created = userRepository.saveAndFlush(user);
        return new AdminUsuarioResponseDto(created.getId(), created.getNombre(),
                created.getEmail(), created.getRole());
    }
}
