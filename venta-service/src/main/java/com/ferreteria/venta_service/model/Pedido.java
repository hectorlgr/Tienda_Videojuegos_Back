package com.ferreteria.venta_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pedidos",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_pedido_comprador_idempotencia",
                        columnNames = {"comprador_id", "clave_idempotencia"}),
                @UniqueConstraint(name = "uq_pedido_operacion_stock", columnNames = "operacion_stock_id")
        },
        indexes = {
                @Index(name = "idx_pedido_comprador_fecha", columnList = "comprador_id,fecha_creacion,id"),
                @Index(name = "idx_pedido_estado_fecha", columnList = "estado,fecha_creacion,id"),
                @Index(name = "idx_pedido_fecha", columnList = "fecha_creacion,id"),
                @Index(name = "idx_pedido_stock_actualizacion",
                        columnList = "estado_stock,fecha_actualizacion,id")
        })
@Getter
@Setter
@NoArgsConstructor
public class Pedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Referencia externa a Identidad; no es una asociación JPA.
    @Column(name = "comprador_id", nullable = false)
    private Long compradorId;

    // Instant representa un instante real. MySQL conserva sus componentes UTC.
    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "fecha_creacion", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant fechaCreacion;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "fecha_actualizacion", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant fechaActualizacion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private EstadoPedido estado;

    // Importes históricos finales en CLP enteros. Sin tarifa ni cálculo automático.
    @Column(name = "total_productos", nullable = false)
    private Long totalProductos;

    @Column(name = "costo_despacho", nullable = false)
    private Long costoDespacho;

    @Column(name = "total_final", nullable = false)
    private Long totalFinal;

    @Column(name = "requiere_despacho", nullable = false)
    private Boolean requiereDespacho;

    @Column(name = "contiene_digitales", nullable = false)
    private Boolean contieneDigitales;

    @Embedded
    private EntregaPedido entrega;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "clave_idempotencia", nullable = false, length = 36)
    private String claveIdempotencia;

    @Column(name = "hash_solicitud", nullable = false, length = 64)
    private String hashSolicitud;

    @Column(name = "operacion_stock_id", nullable = false, length = 36)
    private String operacionStockId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "estado_stock", nullable = false, length = 30)
    private EstadoStockPedido estadoStock;

    @Column(name = "motivo_cancelacion", length = 500)
    private String motivoCancelacion;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "fecha_cancelacion", columnDefinition = "DATETIME(6)")
    private Instant fechaCancelacion;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "fecha_entrega_fisica", columnDefinition = "DATETIME(6)")
    private Instant fechaEntregaFisica;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "fecha_cumplimiento_digital", columnDefinition = "DATETIME(6)")
    private Instant fechaCumplimientoDigital;

    // No se configura REMOVE ni orphanRemoval: la cancelación no borra historia.
    @OneToMany(mappedBy = "pedido", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<DetallePedido> detalles = new ArrayList<>();
}
