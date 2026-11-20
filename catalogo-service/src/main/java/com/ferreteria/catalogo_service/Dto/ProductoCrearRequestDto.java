package com.ferreteria.catalogo_service.Dto;

import com.ferreteria.catalogo_service.model.CategoriaProducto;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ProductoCrearRequestDto {
    @NotBlank
    @Size(max = 255)
    private String nombre;

    @NotNull
    private CategoriaProducto categoria;

    private String descripcion;

    @Size(max = 100)
    private String marca;

    @Size(max = 255)
    private String plataforma;

    @Size(max = 255)
    private String genero;

    @Size(max = 2048)
    private String imagenUrl;

    @NotNull
    @Positive
    private Integer precio;

    @Positive
    private Integer precioOferta;

    @NotNull
    private Boolean enOferta = false;

    @NotNull
    @PositiveOrZero
    private Integer stock = 0;

    @NotNull
    private Boolean habilitado = true;

    @AssertTrue(message = "La oferta activa requiere precioOferta positivo e inferior a precio")
    private boolean isOfertaValida() {
        return !Boolean.TRUE.equals(enOferta)
                || (precio != null && precioOferta != null && precioOferta > 0 && precioOferta < precio);
    }
}
