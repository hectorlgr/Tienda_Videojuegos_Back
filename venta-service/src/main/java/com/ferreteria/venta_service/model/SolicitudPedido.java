package com.ferreteria.venta_service.model;

import com.ferreteria.venta_service.stock.ContratoStock;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Intención técnica previa al pedido comercial. Sin precios, entrega ni llamadas remotas.
 * El futuro coordinador aporta comprador autenticado, solicitud canónica, hash y UUID estable.
 */
@Entity
@Table(name = "solicitudes_pedido",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_solicitud_comprador_clave",
                        columnNames = {"comprador_id", "clave_idempotencia"}),
                @UniqueConstraint(name = "uq_solicitud_operacion_stock", columnNames = "operacion_stock_id")
        },
        indexes = {
                @Index(name = "idx_solicitud_estado_actualizacion", columnList = "estado,fecha_actualizacion,id"),
                @Index(name = "idx_solicitud_estado_bloqueo", columnList = "estado,bloqueo_hasta,id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolicitudPedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comprador_id", nullable = false, updatable = false)
    private Long compradorId;

    @Column(name = "clave_idempotencia", nullable = false, length = 36, updatable = false)
    private String claveIdempotencia;

    @Column(name = "version_contrato", nullable = false, updatable = false)
    private int versionContrato;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSON", updatable = false)
    private ContratoStock.Solicitud solicitud;

    @Column(name = "hash_solicitud", nullable = false, length = 64, updatable = false)
    private String hashSolicitud;

    @Column(name = "operacion_stock_id", nullable = false, length = 36, updatable = false)
    private String operacionStockId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private EstadoSolicitudPedido estado;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "token_reclamacion", length = 36)
    private String tokenReclamacion;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "bloqueo_hasta", columnDefinition = "DATETIME(6)")
    private Instant bloqueoHasta;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "fecha_creacion", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private Instant fechaCreacion;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "fecha_actualizacion", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant fechaActualizacion;

    /**
     * Solo construcción del modelo, sin persistir ni generar identidades.
     * La correspondencia hash/contenido deberá comprobarla el futuro coordinador.
     */
    public SolicitudPedido(Long compradorId, UUID claveIdempotencia, ContratoStock.Solicitud solicitudCanonica,
            String hashSolicitud, UUID operacionStockId, Instant fechaCreacion) {
        if (compradorId == null || compradorId <= 0
                || hashSolicitud == null || !hashSolicitud.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Comprador o hash de solicitud inválido");
        }
        Objects.requireNonNull(solicitudCanonica, "Solicitud canónica requerida");
        long anterior = 0;
        for (ContratoStock.Item item : solicitudCanonica.items()) {
            if (item.productoId() <= anterior) {
                throw new IllegalArgumentException("La solicitud canónica debe estar ordenada por productoId");
            }
            anterior = item.productoId();
        }
        this.compradorId = compradorId;
        this.claveIdempotencia = Objects.requireNonNull(claveIdempotencia).toString();
        this.versionContrato = solicitudCanonica.versionContrato();
        this.solicitud = solicitudCanonica;
        this.hashSolicitud = hashSolicitud;
        this.operacionStockId = Objects.requireNonNull(operacionStockId).toString();
        this.estado = EstadoSolicitudPedido.PENDIENTE;
        this.fechaCreacion = Objects.requireNonNull(fechaCreacion).truncatedTo(ChronoUnit.MICROS);
        this.fechaActualizacion = this.fechaCreacion;
        // @Version se inicializa por Hibernate al persistir; reclamación inicialmente nula.
    }
}
