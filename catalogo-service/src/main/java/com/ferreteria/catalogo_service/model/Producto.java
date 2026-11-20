package com.ferreteria.catalogo_service.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
