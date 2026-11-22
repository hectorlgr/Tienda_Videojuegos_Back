package com.ferreteria.venta_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "detalle_pedidos", uniqueConstraints = {
        @UniqueConstraint(name = "uq_detalle_pedido_producto", columnNames = {"pedido_id", "producto_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class DetallePedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false, foreignKey = @ForeignKey(name = "fk_detalle_pedido"))
    private Pedido pedido;

    // Referencia externa a Catálogo. Los demás datos comerciales son snapshots.
    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 255)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CategoriaProductoPedido categoria;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 10)
    private FormatoProductoPedido formato;

    @Column(length = 255)
    private String plataforma;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario", nullable = false)
    private Long precioUnitario;

    @Column(nullable = false)
    private Long subtotal;
}
