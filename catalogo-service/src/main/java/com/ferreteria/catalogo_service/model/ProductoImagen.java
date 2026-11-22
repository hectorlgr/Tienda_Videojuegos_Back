package com.ferreteria.catalogo_service.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "producto_imagenes", uniqueConstraints =
        @UniqueConstraint(name = "uq_producto_imagen_orden", columnNames = {"producto_id", "orden"}))
@Getter
@Setter
@NoArgsConstructor
public class ProductoImagen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Lado propietario de la FK; al añadir imágenes hay que asignar producto.
    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @NotBlank
    @Size(max = 2048)
    @Pattern(regexp = "https?://\\S+", message = "La imagen debe ser una URL HTTP o HTTPS")
    @Column(nullable = false, length = 2048)
    private String url;

    @NotNull
    @Min(1)
    @Max(4)
    @Column(nullable = false)
    private Integer orden;
}
