package com.ferreteria.catalogo_service.Dto;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(name = "ProductoRequest", description = "Estado completo editable del producto. Stock, oferta y habilitado deben enviarse explícitamente.")
public class ProductoRequestDto extends ProductoCrearRequestDto {
    public ProductoRequestDto() {
        // Reutiliza campos y validaciones del POST, sin aplicar defaults de alta al editar.
        setStock(null);
        setEnOferta(null);
        setHabilitado(null);
    }
}
