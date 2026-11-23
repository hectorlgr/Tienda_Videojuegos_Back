package com.ferreteria.venta_service.service;

import com.ferreteria.venta_service.exception.MaterializacionPedidoException;
import com.ferreteria.venta_service.exception.ResourceNotFoundException;
import com.ferreteria.venta_service.exception.SolicitudPedidoConflictException;
import com.ferreteria.venta_service.model.*;
import com.ferreteria.venta_service.repository.PedidoRepository;
import com.ferreteria.venta_service.repository.SolicitudPedidoRepository;
import com.ferreteria.venta_service.stock.ContratoStock;
import com.ferreteria.venta_service.stock.ResultadoStock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.ferreteria.venta_service.exception.MaterializacionPedidoException.Codigo.*;

/** Entrada exclusiva de código interno confiable. No autentica respuestas HTTP ni invoca Catálogo. */
@Service
public class MaterializacionPedidoService {
    public enum Tipo { CREADO, RECUPERADO }
    public record Resultado(Tipo tipo, Long pedidoId) {}

    private final SolicitudPedidoRepository solicitudes;
    private final PedidoRepository pedidos;
    private final TransactionTemplate escritura;

    public MaterializacionPedidoService(SolicitudPedidoRepository solicitudes, PedidoRepository pedidos,
            PlatformTransactionManager manager) {
        this.solicitudes = solicitudes;
        this.pedidos = pedidos;
        escritura = new TransactionTemplate(manager);
        escritura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        escritura.setTimeout(10);
    }

    public Resultado materializar(Long solicitudId, UUID token, ResultadoStock stock) {
        if (solicitudId == null || solicitudId <= 0 || token == null) {
            throw new IllegalArgumentException("ID y token requeridos");
        }
        try {
            return escritura.execute(status -> ejecutar(solicitudId, token, stock, false));
        } catch (DataIntegrityViolationException ex) {
            // Recuperar SOLO después del rollback, con otro contexto de persistencia.
            Resultado recuperado = escritura.execute(status -> ejecutar(solicitudId, token, stock, true));
            if (recuperado != null) return recuperado;
            throw ex; // Otro CHECK/FK/UNIQUE no se convierte en éxito ni en fallo comercial.
        } catch (TransientDataAccessException ex) {
            throw new MaterializacionPedidoException(PERSISTENCIA_TRANSITORIA, ex);
        }
    }

    private Resultado ejecutar(Long id, UUID token, ResultadoStock stock, boolean soloRecuperar) {
        SolicitudPedido s = solicitudes.buscarParaActualizar(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de pedido inexistente"));
        validarStock(s, stock);
        if (s.getEstado() == EstadoSolicitudPedido.COMPENSACION_PENDIENTE
                || s.getCodigoCompensacion() != null) fallo(COMPENSACION_PENDIENTE);
        Pedido existente = pedidos.buscarPorOperacionStock(s.getOperacionStockId()).orElse(null);
        if (s.getEstado() == EstadoSolicitudPedido.RESUELTA) {
            var r = s.getResultado();
            if (r == null || r.tipo() != DatosCheckoutSolicitud.TipoResultado.PEDIDO_CREADO) {
                throw new SolicitudPedidoConflictException(
                        SolicitudPedidoConflictException.Codigo.SOLICITUD_RESUELTA);
            }
            if (existente == null || !existente.getId().equals(r.pedidoId())) fallo(PEDIDO_INCONSISTENTE);
            verificarIdentidad(s, existente);
            verificarSnapshot(existente, stock.operacion().snapshot());
            return new Resultado(Tipo.RECUPERADO, existente.getId()); // Sin mutación ni reapertura.
        }
        if (soloRecuperar) return null;
        s.exigirTokenVigente(token, ahora()); // Reloj posterior a adquirir el bloqueo.
        if (s.getCheckout() == null || s.getHashCheckout() == null) fallo(CHECKOUT_AUSENTE);
        s.exigirCheckoutParaDescuento();
        Instant vencimiento = s.getBloqueoHasta();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                if (!vencimiento.isAfter(ahora())) {
                    throw new SolicitudPedidoConflictException(
                            SolicitudPedidoConflictException.Codigo.TOKEN_NO_VIGENTE);
                }
            }
        });
        Pedido preparado = preparar(s, stock.operacion().snapshot());
        if (existente != null) {
            verificarIdentidad(s, existente);
            verificarSnapshot(existente, stock.operacion().snapshot());
            if (existente.getEstado() != EstadoPedido.CREADO
                    || existente.getEstadoStock() != EstadoStockPedido.DESCONTADO
                    || !Objects.equals(existente.getTotalFinal(), preparado.getTotalFinal())
                    || !Objects.equals(existente.getCostoDespacho(), preparado.getCostoDespacho())
                    || !Objects.equals(existente.getRequiereDespacho(), preparado.getRequiereDespacho())
                    || !Objects.equals(existente.getContieneDigitales(), preparado.getContieneDigitales())
                    || !entregaIgual(existente.getEntrega(), preparado.getEntrega())) fallo(PEDIDO_INCONSISTENTE);
        } else {
            if (pedidos.findByCompradorIdAndClaveIdempotencia(s.getCompradorId(),
                    s.getClaveIdempotencia()).isPresent()) fallo(PEDIDO_INCONSISTENTE);
            pedidos.saveAndFlush(preparado); // ID/version nulos: persist + cascade, nunca merge existente.
            existente = preparado;
        }
        solicitudes.flush(); // Incluye TODOS los detalles; cualquier fallo revierte la transacción completa.
        s.resolverConPedido(token, ahora(), existente.getId());
        solicitudes.flush();
        return new Resultado(existente == preparado ? Tipo.CREADO : Tipo.RECUPERADO, existente.getId());
    }

    private static void validarStock(SolicitudPedido s, ResultadoStock r) {
        if (r == null || r.tipo() != ResultadoStock.Tipo.APLICADA || r.operacion() == null
                || !Objects.equals(r.httpStatus(), 200) || !"OPERACION_APLICADA".equals(r.codigo())
                || r.operacionId() == null || !r.operacionId().toString().equals(s.getOperacionStockId())
                || !r.operacionId().equals(r.operacion().operacionId())
                || r.operacion().estado() != ContratoStock.Estado.APLICADA
                || r.operacion().snapshot() == null) fallo(SNAPSHOT_INVALIDO);
        Map<Long, Integer> esperados = new HashMap<>();
        for (var item : s.getSolicitud().items()) {
            if (esperados.put(item.productoId(), item.cantidad()) != null) fallo(SNAPSHOT_INVALIDO);
        }
        long total = 0;
        try {
            for (var d : r.operacion().snapshot().items()) {
                if (!Objects.equals(esperados.remove(d.productoId()), d.cantidad())
                        || !texto(d.sku(), 64) || !texto(d.nombre(), 255)
                        || (d.plataforma() != null && !longitud(d.plataforma(), 255))
                        || (d.categoria() != ContratoStock.Categoria.VIDEOJUEGO
                            && d.formato() == ContratoStock.Formato.DIGITAL)) fallo(SNAPSHOT_INVALIDO);
                if (Math.multiplyExact(d.precioUnitario(), d.cantidad().longValue()) != d.subtotal()) {
                    fallo(SNAPSHOT_INVALIDO);
                }
                total = Math.addExact(total, d.subtotal());
            }
        } catch (ArithmeticException ex) {
            throw new MaterializacionPedidoException(SNAPSHOT_INVALIDO, ex);
        }
        if (!esperados.isEmpty() || total != r.operacion().snapshot().totalProductos()) fallo(SNAPSHOT_INVALIDO);
    }

    private static Pedido preparar(SolicitudPedido s, ContratoStock.Snapshot snapshot) {
        var checkout = s.getCheckout();
        boolean fisico = snapshot.items().stream().anyMatch(d -> d.formato() != ContratoStock.Formato.DIGITAL);
        boolean digital = snapshot.items().stream().anyMatch(d -> d.formato() == ContratoStock.Formato.DIGITAL);
        long despacho = checkout.contextoServidor().costoDespacho();
        if (fisico && checkout.entrada().entrega() == null) fallo(ENTREGA_FISICA_AUSENTE);
        if (!fisico && despacho != 0) fallo(DESPACHO_DIGITAL_INVALIDO);
        long total;
        try {
            total = Math.addExact(snapshot.totalProductos(), despacho);
        } catch (ArithmeticException ex) {
            throw new MaterializacionPedidoException(TOTAL_FUERA_DE_RANGO, ex);
        }
        if (total > checkout.entrada().limiteTotalClp()) fallo(TOTAL_NO_ACEPTADO);
        Pedido p = new Pedido();
        p.setCompradorId(s.getCompradorId());
        p.setClaveIdempotencia(s.getClaveIdempotencia());
        p.setHashSolicitud(s.getHashCheckout()); // Hash comercial completo, NO hash de stock.
        p.setOperacionStockId(s.getOperacionStockId());
        p.setFechaCreacion(ahora());
        p.setFechaActualizacion(p.getFechaCreacion());
        p.setEstado(EstadoPedido.CREADO);
        p.setEstadoStock(EstadoStockPedido.DESCONTADO);
        p.setTotalProductos(snapshot.totalProductos());
        p.setCostoDespacho(despacho);
        p.setTotalFinal(total);
        p.setRequiereDespacho(fisico);
        p.setContieneDigitales(digital);
        if (fisico) {
            var origen = checkout.entrada().entrega();
            EntregaPedido entrega = new EntregaPedido();
            entrega.setDestinatario(origen.destinatario());
            entrega.setTelefono(origen.telefono());
            entrega.setDireccion(origen.direccion());
            entrega.setRegion(origen.region());
            entrega.setComuna(origen.comuna());
            entrega.setReferencia(origen.referencia());
            p.setEntrega(entrega);
        } // Digital puro: dirección histórica del checkout no se copia al pedido.
        for (var origen : snapshot.items()) {
            DetallePedido d = new DetallePedido();
            d.setPedido(p);
            d.setProductoId(origen.productoId());
            d.setSku(origen.sku());
            d.setNombre(origen.nombre());
            d.setCategoria(CategoriaProductoPedido.valueOf(origen.categoria().name()));
            d.setFormato(origen.formato() == null ? null : FormatoProductoPedido.valueOf(origen.formato().name()));
            d.setPlataforma(origen.plataforma());
            d.setCantidad(origen.cantidad());
            d.setPrecioUnitario(origen.precioUnitario());
            d.setSubtotal(origen.subtotal());
            p.getDetalles().add(d);
        }
        return p;
    }

    private static void verificarIdentidad(SolicitudPedido s, Pedido p) {
        if (!Objects.equals(s.getCompradorId(), p.getCompradorId())
                || !Objects.equals(s.getClaveIdempotencia(), p.getClaveIdempotencia())
                || s.getHashCheckout() == null || !s.getHashCheckout().equals(p.getHashSolicitud())
                || !s.getOperacionStockId().equals(p.getOperacionStockId())) fallo(PEDIDO_INCONSISTENTE);
    }

    private static void verificarSnapshot(Pedido p, ContratoStock.Snapshot snapshot) {
        Map<Long, ContratoStock.Detalle> detalles = new HashMap<>();
        snapshot.items().forEach(d -> detalles.put(d.productoId(), d));
        for (DetallePedido d : p.getDetalles()) {
            var esperado = detalles.remove(d.getProductoId());
            if (esperado == null || !Objects.equals(d.getSku(), esperado.sku())
                    || !Objects.equals(d.getNombre(), esperado.nombre())
                    || d.getCategoria() == null || !d.getCategoria().name().equals(esperado.categoria().name())
                    || !Objects.equals(d.getFormato() == null ? null : d.getFormato().name(),
                            esperado.formato() == null ? null : esperado.formato().name())
                    || !Objects.equals(d.getPlataforma(), esperado.plataforma())
                    || !Objects.equals(d.getCantidad(), esperado.cantidad())
                    || !Objects.equals(d.getPrecioUnitario(), esperado.precioUnitario())
                    || !Objects.equals(d.getSubtotal(), esperado.subtotal())) fallo(PEDIDO_INCONSISTENTE);
        }
        if (!detalles.isEmpty() || !Objects.equals(p.getTotalProductos(), snapshot.totalProductos())) {
            fallo(PEDIDO_INCONSISTENTE);
        }
    }

    private static boolean entregaIgual(EntregaPedido a, EntregaPedido b) {
        return entregaValores(a).equals(entregaValores(b));
    }

    private static List<String> entregaValores(EntregaPedido e) {
        return e == null ? Arrays.asList(null, null, null, null, null, null)
                : Arrays.asList(e.getDestinatario(), e.getTelefono(), e.getDireccion(),
                        e.getRegion(), e.getComuna(), e.getReferencia());
    }

    private static boolean texto(String s, int max) { return s != null && !s.isBlank() && longitud(s, max); }
    private static boolean longitud(String s, int max) { return s.codePointCount(0, s.length()) <= max; }
    private static Instant ahora() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
    private static void fallo(MaterializacionPedidoException.Codigo codigo) {
        throw new MaterializacionPedidoException(codigo);
    }
}
