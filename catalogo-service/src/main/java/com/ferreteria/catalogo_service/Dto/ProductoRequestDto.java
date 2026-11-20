package com.ferreteria.catalogo_service.Dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Schema(name = "ProductoRequest", description = "Estado completo editable. Requiere stockEsperado con el stock leído antes de editar.")
public class ProductoRequestDto extends ProductoCrearRequestDto {
    @Getter
    @Setter
    @NotNull
    @PositiveOrZero
    @Schema(description = "Stock observado antes de editar. Si cambió, se devuelve 409 sin modificar el producto.", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer stockEsperado;

    public ProductoRequestDto() {
        // Reutiliza campos y validaciones del POST, sin aplicar defaults de alta al editar.
        setStock(null);
        setEnOferta(null);
        setHabilitado(null);
    }
}
