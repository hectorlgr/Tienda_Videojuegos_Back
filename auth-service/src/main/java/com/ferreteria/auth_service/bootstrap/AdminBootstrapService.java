package com.ferreteria.auth_service.bootstrap;

import com.ferreteria.auth_service.Dto.RegisterRequestDto;
import com.ferreteria.auth_service.model.User;
import com.ferreteria.auth_service.repository.UserRepository;
import com.ferreteria.auth_service.security.Roles;
import com.ferreteria.auth_service.service.HashService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("bootstrap-admin")
@ConditionalOnProperty(name = "bootstrap.admin.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class AdminBootstrapService {

    private final UserRepository userRepository;
    private final HashService hashService;
    private final Validator validator;

    @Transactional
    public boolean crearPrimero(String email, String nombre, String password) {
        RegisterRequestDto dto = new RegisterRequestDto();
        dto.setEmail(email);
        dto.setNombre(nombre);
        dto.setPassword(password);
        if (!validator.validate(dto).isEmpty() || email.length() > 255 || nombre.length() > 255) {
            throw new IllegalArgumentException("Datos de inicialización inválidos");
        }

        // Nunca sobrescribir, elevar un CLIENTE ni crear más personal mediante este mecanismo.
        if (userRepository.findByEmail(email) != null
                || userRepository.existsByRole(Roles.ADMINISTRADOR)) {
            return false;
        }

        User user = new User();
        user.setEmail(email);
        user.setNombre(nombre);
        user.setPassword(hashService.encode(password));
        user.setRole(Roles.ADMINISTRADOR);
        userRepository.saveAndFlush(user);
        return true;
    }
}
