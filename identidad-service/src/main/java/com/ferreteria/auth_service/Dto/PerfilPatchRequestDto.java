package com.ferreteria.auth_service.Dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Campos omitidos o null no se modifican; una cadena vacía limpia el campo.")
public class PerfilPatchRequestDto {

    @Size(max = 30, message = "El teléfono no debe superar 30 caracteres")
    private String telefono;

    @Size(max = 255, message = "La dirección no debe superar 255 caracteres")
    private String direccion;

    @Size(max = 100, message = "La región no debe superar 100 caracteres")
    private String region;

    @Size(max = 100, message = "La comuna no debe superar 100 caracteres")
    private String comuna;
}
