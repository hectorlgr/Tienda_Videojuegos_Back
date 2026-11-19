package com.ferreteria.auth_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ferreteria.auth_service.model.User;
import com.ferreteria.auth_service.repository.UserRepository;
import com.ferreteria.auth_service.exception.UnauthorizedException;
import com.ferreteria.auth_service.exception.BadRequestException;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private HashService hashService;

    @InjectMocks
    private UserService userService;

    @Test
    void testLogin_Exito_RetornaToken() {
        // GIVEN
        User user = new User();
        user.setEmail("admin@ferreteria.com");
        user.setPassword("hashed_123");
        user.setRole("ADMIN");

        when(userRepository.findByEmail("admin@ferreteria.com")).thenReturn(user);
        when(hashService.matches("123", "hashed_123")).thenReturn(true);
        when(jwtService.generateToken("admin@ferreteria.com", "ADMIN")).thenReturn("token_jwt_valido");

        // WHEN
        String token = userService.login("admin@ferreteria.com", "123");

        // THEN
        assertEquals("token_jwt_valido", token);
        verify(hashService, times(1)).matches("123", "hashed_123");
    }

    @Test
    void testLogin_CredencialesInvalidas_LanzaExcepcion() {
        // GIVEN
        User user = new User();
        user.setEmail("admin@ferreteria.com");
        user.setPassword("hashed_123");

        when(userRepository.findByEmail("admin@ferreteria.com")).thenReturn(user);
        when(hashService.matches("clave_falsa", "hashed_123")).thenReturn(false);

        // WHEN & THEN
        UnauthorizedException excepcion = assertThrows(UnauthorizedException.class, () -> {
            userService.login("admin@ferreteria.com", "clave_falsa");
        });

        assertEquals("Credenciales inválidas", excepcion.getMessage());
    }

    @Test
    void testRegister_UsuarioNuevo_Exito() {
        // GIVEN
        when(userRepository.findByEmail("nuevo@ferreteria.com")).thenReturn(null);
        when(hashService.encode("1234")).thenReturn("hashed_1234");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // WHEN
        String resultado = userService.register("nuevo@ferreteria.com", "1234", "Juan Perez");

        // THEN
        assertEquals("Usuario creado exitosamente!", resultado);
        verify(userRepository, times(1)).saveAndFlush(any(User.class));
    }

    @Test
    void testRegister_UsuarioYaExiste_LanzaExcepcion() {
        // GIVEN
        User existente = new User();
        existente.setEmail("existente@ferreteria.com");
        when(userRepository.findByEmail("existente@ferreteria.com")).thenReturn(existente);

        // WHEN & THEN
        BadRequestException excepcion = assertThrows(BadRequestException.class, () -> {
            userService.register("existente@ferreteria.com", "1234", "Juan");
        });

        assertEquals("El usuario con este email ya existe", excepcion.getMessage());
    }
}
