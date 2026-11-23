package com.ferreteria.venta_service.model;

import com.ferreteria.venta_service.stock.ContratoStock;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.ferreteria.venta_service.exception.SolicitudPedidoConflictException;
import static com.ferreteria.venta_service.exception.SolicitudPedidoConflictException.Codigo.*;

/**
 * Intención técnica previa al pedido comercial. Sin snapshots de precios ni llamadas remotas.
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON", updatable = false)
    private DatosCheckoutSolicitud.Checkout checkout;

    @Column(name = "hash_checkout", length = 64, updatable = false)
    private String hashCheckout;

    @Column(name = "codigo_compensacion", length = 64)
    private String codigoCompensacion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private DatosCheckoutSolicitud.Resultado resultado;

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
     * La correspondencia hash/contenido la prepara SolicitudPedidoService.
     */
    public SolicitudPedido(Long compradorId, UUID claveIdempotencia, ContratoStock.Solicitud solicitudCanonica,
            String hashSolicitud, UUID operacionStockId, Instant fechaCreacion) {
        this(compradorId, claveIdempotencia, solicitudCanonica, hashSolicitud,
                operacionStockId, fechaCreacion, null, null);
    }

    /** El checkout solo se recibe al crear; nunca se completa silenciosamente en registros antiguos. */
    public SolicitudPedido(Long compradorId, UUID claveIdempotencia, ContratoStock.Solicitud solicitudCanonica,
            String hashSolicitud, UUID operacionStockId, Instant fechaCreacion,
            DatosCheckoutSolicitud.Checkout checkout, String hashCheckout) {
        if ((checkout == null) != (hashCheckout == null)
                || (hashCheckout != null && !hashCheckout.matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("Checkout y hash deben estar presentes juntos y ser válidos");
        }
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
        this.checkout = checkout;
        this.hashCheckout = hashCheckout;
        this.operacionStockId = Objects.requireNonNull(operacionStockId).toString();
        this.estado = EstadoSolicitudPedido.PENDIENTE;
        this.fechaCreacion = Objects.requireNonNull(fechaCreacion).truncatedTo(ChronoUnit.MICROS);
        this.fechaActualizacion = this.fechaCreacion;
        // @Version se inicializa por Hibernate al persistir; reclamación inicialmente nula.
    }

    /** Invocar sobre una fila bloqueada dentro de una transacción corta. */
    public void reclamar(UUID token, Instant ahora, Duration duracion) {
        exigirAbierta();
        if (bloqueoHasta != null && bloqueoHasta.isAfter(ahora)) {
            throw new SolicitudPedidoConflictException(RECLAMACION_VIGENTE);
        }
        if (duracion == null || duracion.compareTo(Duration.ofSeconds(1)) < 0
                || duracion.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("La reclamación debe durar entre 1 y 300 segundos");
        }
        // Un dueño anterior pudo haber comenzado trabajo antes de perder su lease.
        if (tokenReclamacion != null && estado != EstadoSolicitudPedido.COMPENSACION_PENDIENTE) {
            estado = EstadoSolicitudPedido.RECONCILIACION_PENDIENTE;
        }
        tokenReclamacion = Objects.requireNonNull(token).toString();
        actualizar(ahora);
        bloqueoHasta = fechaActualizacion.plus(duracion).truncatedTo(ChronoUnit.MICROS);
    }

    public void renovar(UUID token, Instant ahora, Duration duracion) {
        exigirTokenVigente(token, ahora);
        if (duracion == null || duracion.compareTo(Duration.ofSeconds(1)) < 0
                || duracion.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("La reclamación debe durar entre 1 y 300 segundos");
        }
        actualizar(ahora);
        Instant nuevoVencimiento = fechaActualizacion.plus(duracion).truncatedTo(ChronoUnit.MICROS);
        if (nuevoVencimiento.isAfter(bloqueoHasta)) bloqueoHasta = nuevoVencimiento;
    }

    public void marcarReconciliacion(UUID token, Instant ahora) {
        exigirTokenVigente(token, ahora);
        if (estado != EstadoSolicitudPedido.COMPENSACION_PENDIENTE) {
            estado = EstadoSolicitudPedido.RECONCILIACION_PENDIENTE;
        }
        actualizar(ahora);
    }

    public void liberar(UUID token, Instant ahora) {
        exigirTokenVigente(token, ahora);
        // Liberar no borra la necesidad de reconciliación ni implica resolver.
        tokenReclamacion = null;
        bloqueoHasta = null;
        actualizar(ahora);
    }

    private void exigirAbierta() {
        if (estado == EstadoSolicitudPedido.RESUELTA) {
            throw new SolicitudPedidoConflictException(SOLICITUD_RESUELTA);
        }
    }

    /**
     * Precondición obligatoria del futuro coordinador antes de autorizar un descuento.
     * No sustituye validar entrega, política, importes y token vigente en esa transacción.
     */
    public void exigirCheckoutParaDescuento() {
        exigirAbierta();
        if (estado == EstadoSolicitudPedido.COMPENSACION_PENDIENTE || codigoCompensacion != null) {
            throw new IllegalStateException("La decisión de compensar impide descontar o crear un pedido");
        }
        if (checkout == null || hashCheckout == null) {
            throw new IllegalStateException("Solicitud histórica sin checkout: descuento no autorizado");
        }
    }

    private void exigirTokenVigente(UUID token, Instant ahora) {
        exigirAbierta();
        if (token == null || !token.toString().equals(tokenReclamacion)
                || bloqueoHasta == null || !bloqueoHasta.isAfter(ahora)) {
            throw new SolicitudPedidoConflictException(TOKEN_NO_VIGENTE);
        }
    }

    private void actualizar(Instant ahora) {
        Instant micros = Objects.requireNonNull(ahora).truncatedTo(ChronoUnit.MICROS);
        if (micros.isAfter(fechaActualizacion)) fechaActualizacion = micros;
    }
}
