package com.ferreteria.venta_service.service;

import com.ferreteria.venta_service.Dto.SolicitudPedidoInterna;
import com.ferreteria.venta_service.exception.MaterializacionPedidoException;
import com.ferreteria.venta_service.exception.SolicitudPedidoConflictException;
import com.ferreteria.venta_service.model.DatosCheckoutSolicitud;
import com.ferreteria.venta_service.model.EstadoSolicitudPedido;
import com.ferreteria.venta_service.stock.CatalogoStockClient;
import com.ferreteria.venta_service.stock.ContratoStock;
import com.ferreteria.venta_service.stock.ResultadoStock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Mono;

/** Orquestación explícita y acotada. Ningún método abre una TX alrededor de HTTP. */
@Service
public class CoordinadorPedidoService {
    public enum Estado { RESUELTA, RECUPERABLE, OCUPADA, REVISION_REQUERIDA }
    public record Resultado(Estado estado, String codigo, SolicitudPedidoInterna solicitud) {}
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final Duration MAX_HTTP = Duration.ofSeconds(60);
    private final SolicitudPedidoService solicitudes;
    private final CatalogoStockClient stock;
    private final MaterializacionPedidoService materializacion;

    public CoordinadorPedidoService(SolicitudPedidoService solicitudes, CatalogoStockClient stock,
            MaterializacionPedidoService materializacion) {
        this.solicitudes = solicitudes;
        this.stock = stock;
        this.materializacion = materializacion;
    }

    public Resultado comprar(Long compradorId, String claveIdempotencia, List<ContratoStock.Item> items,
            DatosCheckoutSolicitud.Entrada entrada) {
        exigirSinTransaccion();
        var s = solicitudes.registrarConCheckout(compradorId, claveIdempotencia, items, entrada);
        return continuar(s.id());
    }

    /** Entrada de recuperación por ID técnico, solo para llamador interno autorizado. */
    public Resultado continuar(Long solicitudId) {
        exigirSinTransaccion();
        var s = solicitudes.buscarPorId(solicitudId);
        if (s.estado() == EstadoSolicitudPedido.RESUELTA) return terminal(s);
        try {
            s = solicitudes.reclamar(solicitudId, LEASE);
            s = solicitudes.prepararConsulta(s.id(), s.tokenReclamacion(), LEASE);
            var actual = s;
            ResultadoStock r = remoto(s, () -> stock.reconciliar(actual.operacionStockId()));
            if (s.estado() == EstadoSolicitudPedido.COMPENSACION_PENDIENTE) {
                return continuarCompensacion(s, r);
            }
            if (r.tipo() == ResultadoStock.Tipo.INEXISTENTE || r.tipo() == ResultadoStock.Tipo.PENDIENTE) {
                if (s.checkout() == null || s.hashCheckout() == null) {
                    return pendiente(s, Estado.REVISION_REQUERIDA, "CHECKOUT_AUSENTE");
                }
                // 404 NO demuestra ausencia de intentos: reenvío explícito del mismo
                // UUID/contenido. Catálogo serializa aplicar/revertir para ese UUID.
                s = solicitudes.autorizarAplicacion(s.id(), s.tokenReclamacion(), LEASE);
                var autorizada = s;
                r = remoto(s, () -> stock.aplicar(autorizada.operacionStockId(), autorizada.solicitud()));
            }
            return interpretar(s, r);
        } catch (SolicitudPedidoConflictException ex) {
            // Otro trabajador pudo terminar, reclamar o decidir compensar.
            // Nunca limpiar/escribir usando un token obsoleto.
            var actual = solicitudes.buscarPorId(solicitudId);
            if (actual.estado() == EstadoSolicitudPedido.RESUELTA) return terminal(actual);
            return new Resultado(Estado.OCUPADA, ex.getCodigo().name(), actual);
        }
    }

    private Resultado interpretar(SolicitudPedidoInterna s, ResultadoStock r) {
        return switch (r.tipo()) {
            case APLICADA -> materializar(s, r);
            case RECHAZADA -> terminal(solicitudes.confirmarCierreRemoto(s.id(), s.tokenReclamacion(), r));
            case REVERTIDA, CANCELADA -> pendiente(s, Estado.REVISION_REQUERIDA,
                    "CIERRE_REMOTO_SIN_DECISION_LOCAL");
            case CONFLICTO_IDEMPOTENCIA, CONFLICTO_REVERSION, AUTENTICACION_INTERNA, SOLICITUD_INVALIDA ->
                    pendiente(s, Estado.REVISION_REQUERIDA, r.tipo().name());
            default -> pendiente(s, Estado.RECUPERABLE, r.tipo().name());
        };
    }

    private Resultado materializar(SolicitudPedidoInterna s, ResultadoStock aplicada) {
        try {
            materializacion.materializar(s.id(), s.tokenReclamacion(), aplicada);
            return terminal(solicitudes.buscarPorId(s.id()));
        } catch (MaterializacionPedidoException ex) {
            if (!ex.requiereCompensacion()) {
                var estado = ex.getCodigo() == MaterializacionPedidoException.Codigo.PERSISTENCIA_TRANSITORIA
                        ? Estado.RECUPERABLE : Estado.REVISION_REQUERIDA;
                return pendiente(s, estado, ex.getCodigo().name());
            }
            // materializar ya terminó su rollback. La decisión revalida el fallo
            // comercial y ausencia de pedido bajo el mismo lock, en otra TX.
            var decidida = solicitudes.decidirCompensacion(s.id(), s.tokenReclamacion(), aplicada);
            return revertir(decidida);
        }
        // Otros fallos MySQL/commit se propagan: nunca significan compensar.
        // Lease y RECONCILIACION_PENDIENTE permiten recuperación posterior.
    }

    private Resultado continuarCompensacion(SolicitudPedidoInterna s, ResultadoStock r) {
        return switch (r.tipo()) {
            case REVERTIDA, CANCELADA, RECHAZADA ->
                    terminal(solicitudes.confirmarCierreRemoto(s.id(), s.tokenReclamacion(), r));
            case APLICADA, PENDIENTE, INEXISTENTE -> revertir(s);
            case CONFLICTO_IDEMPOTENCIA, CONFLICTO_REVERSION, AUTENTICACION_INTERNA, SOLICITUD_INVALIDA ->
                    pendiente(s, Estado.REVISION_REQUERIDA, r.tipo().name());
            default -> pendiente(s, Estado.RECUPERABLE, r.tipo().name());
        };
    }

    private Resultado revertir(SolicitudPedidoInterna s) {
        var autorizada = solicitudes.autorizarReversion(s.id(), s.tokenReclamacion(), LEASE);
        var r = remoto(autorizada, () -> stock.revertir(autorizada.operacionStockId(), autorizada.solicitud()));
        return switch (r.tipo()) {
            case REVERTIDA, CANCELADA, RECHAZADA ->
                    terminal(solicitudes.confirmarCierreRemoto(s.id(), s.tokenReclamacion(), r));
            case CONFLICTO_REVERSION, CONFLICTO_IDEMPOTENCIA, AUTENTICACION_INTERNA, SOLICITUD_INVALIDA ->
                    pendiente(s, Estado.REVISION_REQUERIDA, r.tipo().name());
            default -> pendiente(s, Estado.RECUPERABLE, r.tipo().name());
        };
    }

    private Resultado pendiente(SolicitudPedidoInterna s, Estado estado, String codigo) {
        return new Resultado(estado, codigo, solicitudes.dejarRecuperable(s.id(), s.tokenReclamacion()));
    }

    private Resultado terminal(SolicitudPedidoInterna s) {
        if (s.estado() != EstadoSolicitudPedido.RESUELTA || s.resultado() == null) {
            throw new IllegalStateException("Resolución terminal ausente");
        }
        return new Resultado(Estado.RESUELTA, s.resultado().codigo(), s);
    }

    private ResultadoStock remoto(SolicitudPedidoInterna s, Supplier<Mono<ResultadoStock>> llamada) {
        exigirSinTransaccion();
        ResultadoStock incierto = new ResultadoStock(s.operacionStockId(), ResultadoStock.Tipo.RESULTADO_INCIERTO,
                null, "RESULTADO_INCIERTO", null);
        ResultadoStock r = Mono.defer(llamada).timeout(MAX_HTTP).onErrorReturn(incierto)
                .defaultIfEmpty(incierto).block();
        if (r == null || r.tipo() == null || !Objects.equals(r.operacionId(), s.operacionStockId())) return incierto;
        return r;
    }

    private static void exigirSinTransaccion() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Coordinación remota requiere llamador sin transacción");
        }
    }
}
