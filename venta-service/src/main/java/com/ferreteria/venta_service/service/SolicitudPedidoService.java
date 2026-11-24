package com.ferreteria.venta_service.service;

import com.ferreteria.venta_service.Dto.SolicitudPedidoInterna;
import com.ferreteria.venta_service.exception.ResourceNotFoundException;
import com.ferreteria.venta_service.exception.SolicitudPedidoConflictException;
import com.ferreteria.venta_service.model.EstadoSolicitudPedido;
import com.ferreteria.venta_service.model.SolicitudPedido;
import com.ferreteria.venta_service.model.DatosCheckoutSolicitud;
import com.ferreteria.venta_service.exception.PreparacionCheckoutException;
import org.springframework.beans.factory.ObjectProvider;
import com.ferreteria.venta_service.repository.SolicitudPedidoRepository;
import com.ferreteria.venta_service.stock.ContratoStock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiConsumer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Solo persistencia local. Comprador recibido de código confiable, nunca de un body/encabezado. */
@Service
public class SolicitudPedidoService {
    private static final List<EstadoSolicitudPedido> ABIERTAS = List.of(
            EstadoSolicitudPedido.PENDIENTE, EstadoSolicitudPedido.RECONCILIACION_PENDIENTE,
            EstadoSolicitudPedido.COMPENSACION_PENDIENTE);
    private final SolicitudPedidoRepository repository;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;
    private final ObjectProvider<PoliticaCheckout> politica;

    public SolicitudPedidoService(SolicitudPedidoRepository repository, PlatformTransactionManager manager,
            ObjectProvider<PoliticaCheckout> politica) {
        this.repository = repository;
        this.politica = politica;
        escritura = new TransactionTemplate(manager);
        escritura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        lectura = new TransactionTemplate(manager);
        lectura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        lectura.setReadOnly(true);
    }

    /**
     * Registro completo. El llamador aporta identidad autenticada y entrada, nunca
     * contexto del servidor. La prelectura HTTP se ejecuta antes de la transacción.
     */
    public SolicitudPedidoInterna registrarConCheckout(Long compradorId, String claveIdempotencia,
            List<ContratoStock.Item> items, DatosCheckoutSolicitud.Entrada entrada) {
        validarComprador(compradorId);
        UUID clave = uuid(claveIdempotencia);
        Objects.requireNonNull(entrada, "Entrada comercial requerida");
        var validada = new ContratoStock.Solicitud(1, items);
        var canonica = new ContratoStock.Solicitud(1, validada.items().stream()
                .sorted(Comparator.comparing(ContratoStock.Item::productoId)).toList());
        String hashStock = hash(canonica);
        String hashCheckout = HashCheckout.calcular(canonica, entrada);
        var previa = buscar(compradorId, clave.toString());
        if (previa.isPresent()) return comprobarCheckout(previa.get(), canonica, hashStock, entrada, hashCheckout);
        DatosCheckoutSolicitud.Checkout checkout;
        // REQUIRES_NEW de la lectura ya finalizó. Rechazar una TX externa del llamador:
        // suspenderla mantendría su conexión/locks durante HTTP.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Registrar un checkout nuevo requiere un llamador sin transacción");
        }
        try {
            PoliticaCheckout proveedor = politica.getIfAvailable();
            if (proveedor == null) throw new PreparacionCheckoutException(
                    PreparacionCheckoutException.Codigo.POLITICA_NO_CONFIGURADA);
            var preparada = Objects.requireNonNull(proveedor.preparar(canonica, entrada));
            if (preparada.composicion() != PoliticaCheckout.Composicion.DIGITAL && entrada.entrega() == null) {
                throw new PreparacionCheckoutException(PreparacionCheckoutException.Codigo.ENTREGA_REQUERIDA);
            }
            if (preparada.contexto().costoDespacho() > entrada.limiteTotalClp()) {
                throw new PreparacionCheckoutException(PreparacionCheckoutException.Codigo.DESPACHO_SUPERA_LIMITE);
            }
            checkout = new DatosCheckoutSolicitud.Checkout(1, entrada, preparada.contexto());
        } catch (PreparacionCheckoutException ex) {
            // Una inserción concurrente puede haber ganado mientras se preparaba.
            var ganadora = buscar(compradorId, clave.toString());
            if (ganadora.isPresent()) return comprobarCheckout(ganadora.get(), canonica, hashStock, entrada, hashCheckout);
            throw ex;
        }
        try {
            return escritura.execute(status -> {
                var existente = repository.findByCompradorIdAndClaveIdempotencia(compradorId, clave.toString());
                if (existente.isPresent()) return comprobarCheckout(SolicitudPedidoInterna.from(existente.get()),
                        canonica, hashStock, entrada, hashCheckout);
                var nueva = new SolicitudPedido(compradorId, clave, canonica, hashStock,
                        UUID.randomUUID(), ahora(), checkout, hashCheckout);
                return SolicitudPedidoInterna.from(repository.saveAndFlush(nueva));
            });
        } catch (DataIntegrityViolationException ex) {
            var ganadora = buscar(compradorId, clave.toString()); // Nueva TX, después de rollback.
            if (ganadora.isEmpty()) throw ex;
            return comprobarCheckout(ganadora.get(), canonica, hashStock, entrada, hashCheckout);
        }
    }

    /** Registro técnico heredado, sin autorización de descuento; usar registrarConCheckout. */
    @Deprecated
    public SolicitudPedidoInterna registrar(Long compradorId, String claveIdempotencia,
            List<ContratoStock.Item> items) {
        validarComprador(compradorId);
        UUID clave = uuid(claveIdempotencia);
        // El record valida versión, tamaño, cantidades y duplicados antes de ordenar.
        ContratoStock.Solicitud validada = new ContratoStock.Solicitud(1, items);
        ContratoStock.Solicitud canonica = new ContratoStock.Solicitud(1, validada.items().stream()
                .sorted(Comparator.comparing(ContratoStock.Item::productoId)).toList());
        String hash = hash(canonica);
        Optional<SolicitudPedidoInterna> previa = buscar(compradorId, clave.toString());
        if (previa.isPresent()) return comprobarContenido(previa.get(), canonica, hash);
        try {
            return escritura.execute(status -> {
                // Segunda lectura ahorra UUID candidatos, pero NO resuelve la carrera:
                // la restricción UNIQUE sigue siendo la autoridad.
                var existente = repository.findByCompradorIdAndClaveIdempotencia(compradorId, clave.toString());
                if (existente.isPresent()) {
                    return comprobarContenido(SolicitudPedidoInterna.from(existente.get()), canonica, hash);
                }
                var nueva = new SolicitudPedido(compradorId, clave, canonica, hash, UUID.randomUUID(), ahora());
                // IDENTITY inserta durante save; hacerlo a través del repositorio traduce la excepción.
                return SolicitudPedidoInterna.from(repository.saveAndFlush(nueva));
            });
        } catch (DataIntegrityViolationException ex) {
            // TransactionTemplate ya terminó el rollback. No reutilizar el EntityManager fallido.
            var ganadora = buscar(compradorId, clave.toString());
            if (ganadora.isEmpty()) throw ex; // Colisión de UUID de stock u otra restricción: no ocultarla.
            return comprobarContenido(ganadora.get(), canonica, hash);
        }
    }

    public Optional<SolicitudPedidoInterna> buscar(Long compradorId, String claveIdempotencia) {
        validarComprador(compradorId);
        String clave = uuid(claveIdempotencia).toString();
        return lectura.execute(status -> repository.findByCompradorIdAndClaveIdempotencia(compradorId, clave)
                .map(SolicitudPedidoInterna::from));
    }

    /** Incluye reclamaciones vigentes; vista de estado, no asignación de trabajo. */
    public List<SolicitudPedidoInterna> buscarPendientes(int limite) {
        validarLimite(limite);
        return lectura.execute(status -> repository.buscarPendientes(ABIERTAS, PageRequest.of(0, limite))
                .stream().map(SolicitudPedidoInterna::from).toList());
    }

    /** Candidatos sin lease o con lease vencido. Es necesario reclamar después. */
    public List<SolicitudPedidoInterna> buscarDisponibles(int limite) {
        validarLimite(limite);
        return lectura.execute(status -> repository.buscarDisponibles(ABIERTAS, ahora(), PageRequest.of(0, limite))
                .stream().map(SolicitudPedidoInterna::from).toList());
    }

    public SolicitudPedidoInterna reclamar(Long id, Duration duracion) {
        validarDuracion(duracion);
        return actualizar(id, (s, ahora) -> s.reclamar(UUID.randomUUID(), ahora, duracion));
    }

    public SolicitudPedidoInterna renovar(Long id, UUID token, Duration duracion) {
        Objects.requireNonNull(token, "Token requerido");
        validarDuracion(duracion);
        return actualizar(id, (s, ahora) -> s.renovar(token, ahora, duracion));
    }

    public SolicitudPedidoInterna marcarReconciliacion(Long id, UUID token) {
        Objects.requireNonNull(token, "Token requerido");
        return actualizar(id, (s, ahora) -> s.marcarReconciliacion(token, ahora));
    }

    public SolicitudPedidoInterna liberar(Long id, UUID token) {
        Objects.requireNonNull(token, "Token requerido");
        return actualizar(id, (s, ahora) -> s.liberar(token, ahora));
    }

    private SolicitudPedidoInterna actualizar(Long id, BiConsumer<SolicitudPedido, Instant> cambio) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ID de solicitud inválido");
        return escritura.execute(status -> {
            SolicitudPedido solicitud = repository.buscarParaActualizar(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Solicitud de pedido inexistente"));
            // El reloj se consulta DESPUÉS de adquirir el lock, nunca antes de esperar.
            cambio.accept(solicitud, ahora());
            repository.flush(); // Dirty checking incrementa @Version; no usar updates bulk sin versionar.
            return SolicitudPedidoInterna.from(solicitud);
        });
    }

    private SolicitudPedidoInterna comprobarContenido(SolicitudPedidoInterna existente,
            ContratoStock.Solicitud solicitud, String hash) {
        // El método antiguo no puede recuperar checkout comercial validando solo productos.
        if (existente.checkout() != null) {
            throw new SolicitudPedidoConflictException(SolicitudPedidoConflictException.Codigo.CONTENIDO_DISTINTO);
        }
        if (!hash.equals(existente.hashSolicitud()) || !solicitud.equals(existente.solicitud())) {
            throw new SolicitudPedidoConflictException(SolicitudPedidoConflictException.Codigo.CONTENIDO_DISTINTO);
        }
        return existente;
    }

    private SolicitudPedidoInterna comprobarCheckout(SolicitudPedidoInterna existente,
            ContratoStock.Solicitud solicitud, String hashStock,
            DatosCheckoutSolicitud.Entrada entrada, String hashCheckout) {
        if (existente.checkout() == null || existente.hashCheckout() == null) {
            throw new SolicitudPedidoConflictException(
                    SolicitudPedidoConflictException.Codigo.CHECKOUT_HISTORICO_AUSENTE);
        }
        if (!solicitud.equals(existente.solicitud()) || !hashStock.equals(existente.hashSolicitud())
                || !entrada.equals(existente.checkout().entrada()) || !hashCheckout.equals(existente.hashCheckout())) {
            throw new SolicitudPedidoConflictException(SolicitudPedidoConflictException.Codigo.CONTENIDO_DISTINTO);
        }
        return existente; // Incluye contexto, UUID, estado y resultado originales, sin UPDATE.
    }

    private static String hash(ContratoStock.Solicitud solicitud) {
        StringJoiner items = new StringJoiner(",");
        for (ContratoStock.Item item : solicitud.items()) {
            items.add("{\"productoId\":" + item.productoId() + ",\"cantidad\":" + item.cantidad() + "}");
        }
        String json = "{\"versionContrato\":1,\"items\":[" + items + "]}";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private static UUID uuid(String texto) {
        if (texto == null || !texto.matches(
                "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Clave de idempotencia UUID inválida");
        }
        return UUID.fromString(texto);
    }

    private static void validarComprador(Long compradorId) {
        if (compradorId == null || compradorId <= 0) throw new IllegalArgumentException("Comprador inválido");
    }

    private static void validarLimite(int limite) {
        if (limite < 1 || limite > 100) throw new IllegalArgumentException("Límite entre 1 y 100 requerido");
    }

    private static void validarDuracion(Duration duracion) {
        if (duracion == null || duracion.compareTo(Duration.ofSeconds(1)) < 0
                || duracion.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("La reclamación debe durar entre 1 y 300 segundos");
        }
    }

    private static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
