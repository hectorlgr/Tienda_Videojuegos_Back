package com.ferreteria.venta_service.service;

import com.ferreteria.venta_service.Dto.SolicitudPedidoInterna;
import com.ferreteria.venta_service.exception.ResourceNotFoundException;
import com.ferreteria.venta_service.exception.SolicitudPedidoConflictException;
import com.ferreteria.venta_service.model.EstadoSolicitudPedido;
import com.ferreteria.venta_service.model.SolicitudPedido;
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

/** Solo persistencia local. Comprador recibido de código confiable, nunca de un body/encabezado. */
@Service
public class SolicitudPedidoService {
    private static final List<EstadoSolicitudPedido> ABIERTAS = List.of(
            EstadoSolicitudPedido.PENDIENTE, EstadoSolicitudPedido.RECONCILIACION_PENDIENTE);
    private final SolicitudPedidoRepository repository;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;

    public SolicitudPedidoService(SolicitudPedidoRepository repository, PlatformTransactionManager manager) {
        this.repository = repository;
        escritura = new TransactionTemplate(manager);
        escritura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        lectura = new TransactionTemplate(manager);
        lectura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        lectura.setReadOnly(true);
    }

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
        if (!hash.equals(existente.hashSolicitud()) || !solicitud.equals(existente.solicitud())) {
            throw new SolicitudPedidoConflictException(SolicitudPedidoConflictException.Codigo.CONTENIDO_DISTINTO);
        }
        return existente;
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
