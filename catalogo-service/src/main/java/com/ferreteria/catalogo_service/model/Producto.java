package com.ferreteria.catalogo_service.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "productos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Producto {

    // ID Generado automaticamente por la db
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String nombre;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CategoriaProducto categoria;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String descripcion;

    // Compatibilidad con el DTO, controlador y servicio heredados.
    @Column(length = 100)
    private String marca;

    private String plataforma;

    private String genero;

    @Column(name = "imagen_url", length = 2048)
    private String imagenUrl;

    // Precio final en CLP; no se agrega IVA en esta capa.
    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer precio;

    @Positive
    @Column(name = "precio_oferta")
    private Integer precioOferta;

    @NotNull
    @Column(name = "en_oferta", nullable = false)
    private Boolean enOferta = false;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    private Integer stock = 0;

    // Visibilidad administrativa independiente del stock.
    @NotNull
    @Column(nullable = false)
    private Boolean habilitado = true;

    @Column(name = "rawg_id")
    private Long rawgId;

    // Fase transitoria: nullable hasta adaptar DTO/formulario y completar los datos.
    @Size(max = 64)
    @Pattern(regexp = ".*\\S.*", message = "El código no puede estar vacío")
    @NotBlank(groups = ProductoCompleto.class)
    @Column(length = 64)
    private String codigo;

    @Size(max = 500)
    @Column(name = "descripcion_corta", length = 500)
    private String descripcionCorta;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 10)
    private FormatoProducto formato;

    @Size(max = 100)
    @Column(length = 100)
    private String ecosistema;

    @Size(max = 255)
    @Column(length = 255)
    private String compatibilidad;

    @Size(max = 255)
    @Column(length = 255)
    private String franquicia;

    @PositiveOrZero
    @Column(name = "stock_critico")
    private Integer stockCritico;

    // La colección no forma parte de los contratos HTTP actuales.
    @JsonIgnore
    @OneToMany(mappedBy = "producto", cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orden ASC")
    @Size(max = 4)
    private List<ProductoImagen> imagenesAdicionales = new ArrayList<>();

    @AssertTrue(groups = ProductoCompleto.class,
            message = "Debe completar el campo específico de la categoría")
    private boolean isCamposCategoriaCompletos() {
        if (categoria == null) return false;
        return switch (categoria) {
            case VIDEOJUEGO -> formato != null && plataforma != null && !plataforma.isBlank();
            case CONSOLA -> ecosistema != null && !ecosistema.isBlank();
            case ACCESORIO -> compatibilidad != null && !compatibilidad.isBlank();
            case FIGURA -> franquicia != null && !franquicia.isBlank();
        };
    }

    @AssertTrue(message = "Una oferta activa requiere un precio de oferta positivo e inferior al precio normal")
    private boolean isOfertaValida() {
        return !Boolean.TRUE.equals(enOferta)
                || (precio != null && precioOferta != null && precioOferta > 0 && precioOferta < precio);
    }

    // Valor derivado: no se persiste ni amplía el contrato JSON existente.
    public Integer calcularPrecioEfectivo() {
        if (!isOfertaValida()) {
            throw new IllegalStateException("La oferta activa no tiene un precio válido");
        }
        return Boolean.TRUE.equals(enOferta) ? precioOferta : precio;
    }
}
