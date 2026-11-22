package com.ferreteria.catalogo_service.Dto;

import com.ferreteria.catalogo_service.model.CategoriaProducto;
import com.ferreteria.catalogo_service.model.FormatoProducto;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ProductoCrearRequestDto {
    @NotBlank
    @Size(max = 64)
    private String codigo;

    public void setCodigo(String codigo) {
        this.codigo = codigo == null ? null : codigo.strip().toUpperCase(Locale.ROOT);
    }

    @Size(max = 500)
    private String descripcionCorta;

    private FormatoProducto formato;

    @Size(max = 100)
    private String ecosistema;

    @Size(max = 255)
    private String compatibilidad;

    @Size(max = 255)
    private String franquicia;

    @PositiveOrZero
    private Integer stockCritico;

    // POST: ausente/null equivale a vacía. PUT: ausente/null conserva la galería.
    @Size(max = 4)
    private List<@NotBlank @Size(max = 2048) String> imagenes;

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

    @AssertTrue(message = "Complete formato y plataforma para VIDEOJUEGO, ecosistema para CONSOLA, compatibilidad para ACCESORIO o franquicia para FIGURA")
    private boolean isCategoriaValida() {
        if (categoria == null) return false;
        return switch (categoria) {
            case VIDEOJUEGO -> formato != null && plataforma != null && !plataforma.isBlank();
            case CONSOLA -> ecosistema != null && !ecosistema.isBlank();
            case ACCESORIO -> compatibilidad != null && !compatibilidad.isBlank();
            case FIGURA -> franquicia != null && !franquicia.isBlank();
        };
    }

    @AssertTrue(message = "Las imágenes deben ser URL absolutas HTTP/HTTPS, sin Base64 ni espacios")
    private boolean isImagenesValidas() {
        return (imagenUrl == null || urlValida(imagenUrl))
                && (imagenes == null || imagenes.stream().allMatch(ProductoCrearRequestDto::urlValida));
    }

    private static boolean urlValida(String value) {
        if (value == null || !value.matches("https?://\\S+")) return false;
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute() && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
