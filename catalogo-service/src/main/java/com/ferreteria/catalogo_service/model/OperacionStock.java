package com.ferreteria.catalogo_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.ferreteria.catalogo_service.model.DatosOperacionStock.*;

@Entity
@Table(name = "operaciones_stock", indexes = @Index(
        name = "idx_operacion_estado_fecha", columnList = "estado,fecha_actualizacion"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperacionStock {
    @Id
    @Column(name = "operacion_id", length = 36, nullable = false, updatable = false)
    private String operacionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 64, nullable = false, updatable = false)
    private ConsumidorStock consumidor;

    @Column(name = "version_contrato", nullable = false, updatable = false)
    private int versionContrato;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "JSON")
    private Solicitud solicitud;

    @Column(name = "hash_solicitud", length = 64, nullable = false, updatable = false)
    private String hashSolicitud;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20, nullable = false)
    private EstadoOperacionStock estado;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resultado_aplicacion", columnDefinition = "JSON")
    private Aplicacion resultadoAplicacion;

    @Column(name = "codigo_rechazo", length = 64)
    private String codigoRechazo;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "fecha_creacion", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private Instant fechaCreacion;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "fecha_actualizacion", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant fechaActualizacion;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "fecha_aplicacion", columnDefinition = "DATETIME(6)")
    private Instant fechaAplicacion;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "fecha_cierre", columnDefinition = "DATETIME(6)")
    private Instant fechaCierre;

    public static OperacionStock registrar(String id, ConsumidorStock consumidor,
            Solicitud solicitud, String hash, boolean cancelar, Instant ahora) {
        OperacionStock op = new OperacionStock();
        op.operacionId = id;
        op.consumidor = consumidor;
        op.versionContrato = solicitud.versionContrato();
        op.solicitud = solicitud;
        op.hashSolicitud = hash;
        op.estado = cancelar ? EstadoOperacionStock.CANCELADA : EstadoOperacionStock.PENDIENTE;
        op.fechaCreacion = ahora;
        op.fechaActualizacion = ahora;
        op.fechaCierre = cancelar ? ahora : null;
        return op;
    }

    public void aplicar(Aplicacion snapshot, Instant ahora) {
        exigir(EstadoOperacionStock.PENDIENTE);
        resultadoAplicacion = java.util.Objects.requireNonNull(snapshot);
        estado = EstadoOperacionStock.APLICADA;
        fechaAplicacion = actualizar(ahora);
    }

    public void rechazar(String codigo, Instant ahora) {
        exigir(EstadoOperacionStock.PENDIENTE);
        if (codigo == null || codigo.isBlank() || codigo.length() > 64) {
            throw new IllegalArgumentException("Código de rechazo inválido");
        }
        codigoRechazo = codigo;
        estado = EstadoOperacionStock.RECHAZADA;
        fechaCierre = actualizar(ahora);
    }

    public void cancelar(Instant ahora) {
        exigir(EstadoOperacionStock.PENDIENTE);
        estado = EstadoOperacionStock.CANCELADA;
        fechaCierre = actualizar(ahora);
    }

    public void revertir(Instant ahora) {
        exigir(EstadoOperacionStock.APLICADA);
        estado = EstadoOperacionStock.REVERTIDA;
        fechaCierre = actualizar(ahora);
    }

    private void exigir(EstadoOperacionStock esperado) {
        if (estado != esperado) throw new IllegalStateException("Transición de stock inválida");
    }

    private Instant actualizar(Instant ahora) {
        // Tolera un retroceso del reloj sin romper la cronología persistida.
        fechaActualizacion = ahora.isBefore(fechaActualizacion) ? fechaActualizacion : ahora;
        return fechaActualizacion;
    }

    public Resultado resultado() {
        return new Resultado(operacionId, consumidor, versionContrato, solicitud, hashSolicitud,
                estado, resultadoAplicacion, codigoRechazo, fechaCreacion, fechaActualizacion,
                fechaAplicacion, fechaCierre);
    }
}
