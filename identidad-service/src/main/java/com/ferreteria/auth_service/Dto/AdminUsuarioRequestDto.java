package com.ferreteria.auth_service.Dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUsuarioRequestDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 255, message = "El nombre no debe superar 255 caracteres")
    private String nombre;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    @Size(max = 255, message = "El email no debe superar 255 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Schema(description = "Contraseña de la nueva cuenta, máximo 72 bytes UTF-8",
            accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @NotBlank(message = "El rol es obligatorio")
    @Pattern(regexp = "ADMINISTRADOR|VENDEDOR", message = "El rol debe ser ADMINISTRADOR o VENDEDOR")
    @Schema(allowableValues = { "ADMINISTRADOR", "VENDEDOR" })
    private String role;
}
