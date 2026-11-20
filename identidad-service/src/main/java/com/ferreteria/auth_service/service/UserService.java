package com.ferreteria.auth_service.service;

import com.ferreteria.auth_service.exception.BadRequestException;
import com.ferreteria.auth_service.exception.UnauthorizedException;
import com.ferreteria.auth_service.model.User;
import com.ferreteria.auth_service.security.Roles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ferreteria.auth_service.repository.UserRepository;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private HashService hashService;

    // Método para manejar el proceso de login
    public String login(String email, String password) {
        User user = userRepository.findByEmail(email);

        if (user == null || !Roles.esValido(user.getRole())) {
            throw new UnauthorizedException("Credenciales inválidas"); // Lanza 401
        }

        if (!hashService.matches(password, user.getPassword())) {
            throw new UnauthorizedException("Credenciales inválidas"); // Lanza 401
        }

        return jwtService.generateToken(user.getId(), email, user.getRole());
    }

    // Método para obtener el rol de un usuario a partir de su email
    public String getRole(String email) {
        User user = userRepository.findByEmail(email);
        return user.getRole();
    }

    // Registro público: el rol se define exclusivamente en el servidor.
    @Transactional
    public String register(String email, String password, String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new BadRequestException("El nombre es obligatorio");
        }
        User existing = userRepository.findByEmail(email);
        if (existing != null) {
            throw new BadRequestException("El usuario con este email ya existe"); // Lanza 400
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(hashService.encode(password));
        user.setRole(Roles.CLIENTE);
        user.setNombre(nombre);
        userRepository.saveAndFlush(user);

        return "Usuario creado exitosamente!";
    }
}
