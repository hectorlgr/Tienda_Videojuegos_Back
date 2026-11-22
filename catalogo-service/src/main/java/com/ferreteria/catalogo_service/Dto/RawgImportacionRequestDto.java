package com.ferreteria.catalogo_service.Dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ferreteria.catalogo_service.exception.BadRequestException;
import com.ferreteria.catalogo_service.model.FormatoProducto;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Locale;

@Getter
@Setter
public class RawgImportacionRequestDto {
    @NotBlank
    @Size(max = 64)
    private String codigo;

    @NotBlank
    @Size(max = 255)
    private String plataforma;

    @NotNull
    private FormatoProducto formato;

    @NotNull
    @Positive
    private Integer precio;

    @Positive
    private Integer precioOferta;

    @NotNull
    private Boolean enOferta = false;

    @NotNull
    @PositiveOrZero
    private Integer stock;

    @PositiveOrZero
    private Integer stockCritico;

    @NotNull
    private Boolean habilitado = true;

    public void setCodigo(String codigo) {
        this.codigo = codigo == null ? null : codigo.strip().toUpperCase(Locale.ROOT);
    }

    public void setPlataforma(String plataforma) {
        this.plataforma = normalizarPlataforma(plataforma);
    }

    public static String normalizarPlataforma(String plataforma) {
        return plataforma == null ? null
                : plataforma.replaceAll("(?U)\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    @AssertTrue(message = "Una oferta activa requiere precioOferta positivo e inferior a precio")
    private boolean isOfertaValida() {
        return !Boolean.TRUE.equals(enOferta)
                || (precio != null && precioOferta != null && precioOferta > 0 && precioOferta < precio);
    }

    // Estricto solo para este contrato; no cambia la configuración global de JSON.
    @JsonAnySetter
    public void rechazarCampoDesconocido(String nombre, Object valor) {
        throw new BadRequestException("El cuerpo de importación admite únicamente los campos comerciales documentados.");
    }
}
