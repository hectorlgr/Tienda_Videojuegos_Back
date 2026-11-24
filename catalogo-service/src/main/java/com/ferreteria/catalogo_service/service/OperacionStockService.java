package com.ferreteria.catalogo_service.service;

import com.ferreteria.catalogo_service.exception.BadRequestException;
import com.ferreteria.catalogo_service.exception.ConflictException;
import com.ferreteria.catalogo_service.exception.ResourceNotFoundException;
import com.ferreteria.catalogo_service.model.*;
import com.ferreteria.catalogo_service.model.DatosOperacionStock.*;
import com.ferreteria.catalogo_service.repository.OperacionStockRepository;
import com.ferreteria.catalogo_service.repository.ProductoRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Núcleo interno. El consumidor debe proceder de código confiable; este servicio
 * no autentica HTTP. No envolver llamadas en transacciones que bloqueen productos.
 */
@Service
public class OperacionStockService {
    public static final int MAX_PRODUCTOS = 100;
    private final OperacionStockRepository operaciones;
    private final ProductoRepository productos;
    private final EntityManager entityManager;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;

    public OperacionStockService(OperacionStockRepository operaciones, ProductoRepository productos,
            EntityManager entityManager, PlatformTransactionManager transactionManager) {
        this.operaciones = operaciones;
        this.productos = productos;
        this.entityManager = entityManager;
        escritura = new TransactionTemplate(transactionManager);
        escritura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        lectura = new TransactionTemplate(transactionManager);
        lectura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        lectura.setReadOnly(true);
    }

    /** Transacción A: confirma intención antes de cualquier intento de aplicación. */
    public Resultado registrar(String id, ConsumidorStock consumidor, Solicitud solicitud) {
        return registrar(validar(id, consumidor, solicitud), false);
    }

    /** Transacción B independiente. Los estados definitivos solamente se consultan. */
    public Resultado aplicar(String id, ConsumidorStock consumidor) {
        String uuid = validarIdentidad(id, consumidor);
        return escritura.execute(status -> {
            OperacionStock op = bloquear(uuid, consumidor);
            if (op.getEstado() != EstadoOperacionStock.PENDIENTE) return op.resultado();
            comprobarContenido(op.resultado(), validar(uuid, consumidor, op.getSolicitud()));
            List<Item> items = op.getSolicitud().items();
            List<Producto> bloqueados = new ArrayList<>();
            // La solicitud persistida ya está ordenada; no usar un IN sin orden de locks garantizado.
            for (Item item : items) {
                Producto producto = productos.findByIdForUpdate(item.productoId()).orElse(null);
                if (producto == null) return rechazar(op, "PRODUCTO_INEXISTENTE");
                bloqueados.add(producto);
            }
            List<Detalle> detalles = new ArrayList<>();
            long total = 0;
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                Producto p = bloqueados.get(i);
                if (!Boolean.TRUE.equals(p.getHabilitado())) return rechazar(op, "PRODUCTO_DESHABILITADO");
                if (p.getStock() == null || p.getStock() < 0) {
                    throw new IllegalStateException("Stock persistido inválido");
                }
                if (p.getStock() < item.cantidad()) return rechazar(op, "STOCK_INSUFICIENTE");
                if (!metadatosValidos(p)) return rechazar(op, "METADATOS_INVALIDOS");
                Integer precio;
                try {
                    precio = p.calcularPrecioEfectivo();
                } catch (IllegalStateException ex) {
                    return rechazar(op, "PRECIO_INVALIDO");
                }
                if (precio == null || precio <= 0) return rechazar(op, "PRECIO_INVALIDO");
                long subtotal;
                try {
                    subtotal = Math.multiplyExact(precio.longValue(), item.cantidad().longValue());
                    total = Math.addExact(total, subtotal);
                } catch (ArithmeticException ex) {
                    return rechazar(op, "IMPORTE_FUERA_DE_RANGO");
                }
                detalles.add(new Detalle(p.getId(), p.getCodigo(), p.getNombre(), p.getCategoria(),
                        p.getFormato(), p.getPlataforma(), item.cantidad(), precio.longValue(), subtotal));
            }
            // Ningún producto se modifica antes de completar TODAS las validaciones.
            Aplicacion snapshot = new Aplicacion(1, detalles, total);
            for (int i = 0; i < items.size(); i++) {
                Producto p = bloqueados.get(i);
                p.setStock(p.getStock() - items.get(i).cantidad());
            }
            op.aplicar(snapshot, ahora());
            entityManager.flush();
            return op.resultado();
        });
    }

    public Optional<Resultado> consultar(String id, ConsumidorStock consumidor) {
        String uuid = validarIdentidad(id, consumidor);
        return lectura.execute(status -> operaciones.findById(uuid).map(op -> {
            comprobarConsumidor(op.getConsumidor(), consumidor);
            return op.resultado();
        }));
    }

    public Resultado revertir(String id, ConsumidorStock consumidor, Solicitud solicitud) {
        Entrada entrada = validar(id, consumidor, solicitud);
        // Inserción CANCELADA si falta: nunca hay una ventana PENDIENTE creada por la reversión.
        registrar(entrada, true);
        return escritura.execute(status -> {
            OperacionStock op = bloquear(entrada.id(), consumidor);
            comprobarContenido(op.resultado(), entrada);
            if (op.getEstado() == EstadoOperacionStock.PENDIENTE) {
                op.cancelar(ahora());
            } else if (op.getEstado() == EstadoOperacionStock.APLICADA) {
                List<Detalle> detalles = op.getResultadoAplicacion().items().stream()
                        .sorted(Comparator.comparing(Detalle::productoId)).toList();
                List<Producto> bloqueados = new ArrayList<>();
                List<Integer> stocks = new ArrayList<>();
                for (Detalle detalle : detalles) {
                    Producto p = productos.findByIdForUpdate(detalle.productoId())
                            .orElseThrow(() -> new ConflictException("REVERSION_PRODUCTO_INEXISTENTE"));
                    if (p.getStock() == null || p.getStock() < 0 || detalle.cantidad() <= 0) {
                        throw new IllegalStateException("Stock o snapshot persistido inválido");
                    }
                    long nuevoStock = (long) p.getStock() + detalle.cantidad();
                    if (nuevoStock > Integer.MAX_VALUE) {
                        throw new ConflictException("REVERSION_STOCK_FUERA_DE_RANGO");
                    }
                    bloqueados.add(p);
                    stocks.add((int) nuevoStock);
                }
                for (int i = 0; i < bloqueados.size(); i++) bloqueados.get(i).setStock(stocks.get(i));
                op.revertir(ahora());
            }
            entityManager.flush();
            return op.resultado();
        });
    }

    private Resultado registrar(Entrada entrada, boolean cancelar) {
        Optional<Resultado> existente = consultar(entrada.id(), entrada.consumidor());
        if (existente.isPresent()) {
            comprobarContenido(existente.get(), entrada);
            return existente.get();
        }
        try {
            return escritura.execute(status -> {
                OperacionStock op = OperacionStock.registrar(entrada.id(), entrada.consumidor(),
                        entrada.solicitud(), entrada.hash(), cancelar, ahora());
                // persist, no merge: un UUID asignado no debe sobrescribir una fila concurrente.
                entityManager.persist(op);
                operaciones.flush();
                return op.resultado();
            });
        } catch (DataIntegrityViolationException ex) {
            // El callback ya salió y la transacción fallida terminó. Leer en una NUEVA.
            Optional<Resultado> ganador = consultar(entrada.id(), entrada.consumidor());
            if (ganador.isEmpty()) throw ex; // No ocultar otras violaciones de integridad.
            comprobarContenido(ganador.get(), entrada);
            return ganador.get();
        }
    }

    private OperacionStock bloquear(String id, ConsumidorStock consumidor) {
        OperacionStock op = operaciones.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Operación de stock inexistente"));
        comprobarConsumidor(op.getConsumidor(), consumidor);
        return op;
    }

    private Resultado rechazar(OperacionStock op, String codigo) {
        // Resultado normal: no lanzar una excepción que revierta el rechazo durable.
        op.rechazar(codigo, ahora());
        entityManager.flush();
        return op.resultado();
    }

    private static void comprobarConsumidor(ConsumidorStock actual, ConsumidorStock solicitado) {
        if (actual != solicitado) throw new ConflictException("OPERACION_CONSUMIDOR_DISTINTO");
    }

    private static void comprobarContenido(Resultado actual, Entrada entrada) {
        comprobarConsumidor(actual.consumidor(), entrada.consumidor());
        if (!actual.hashSolicitud().equals(entrada.hash()) || !actual.solicitud().equals(entrada.solicitud())) {
            throw new ConflictException("OPERACION_CONTENIDO_DISTINTO");
        }
    }

    private static String validarIdentidad(String id, ConsumidorStock consumidor) {
        if (consumidor != ConsumidorStock.VENTA_SERVICE) {
            throw new BadRequestException("Consumidor interno no permitido");
        }
        if (id == null || !id.matches(
                "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new BadRequestException("El identificador debe ser UUID");
        }
        return UUID.fromString(id).toString();
    }

    private static Entrada validar(String id, ConsumidorStock consumidor, Solicitud solicitud) {
        String uuid = validarIdentidad(id, consumidor);
        if (solicitud == null || solicitud.versionContrato() != 1 || solicitud.items() == null
                || solicitud.items().isEmpty() || solicitud.items().size() > MAX_PRODUCTOS) {
            throw new BadRequestException("Versión 1 y entre 1 y 100 productos requeridos");
        }
        Set<Long> ids = new HashSet<>();
        for (Item item : solicitud.items()) {
            if (item == null || item.productoId() == null || item.productoId() <= 0
                    || item.cantidad() == null || item.cantidad() <= 0) {
                throw new BadRequestException("Producto y cantidad deben ser positivos");
            }
            if (!ids.add(item.productoId())) throw new BadRequestException("Producto duplicado");
        }
        Solicitud canonica = new Solicitud(1, solicitud.items().stream()
                .sorted(Comparator.comparing(Item::productoId)).toList());
        // JSON canónico propio v1: enteros decimales, orden fijo, sin espacios.
        StringJoiner items = new StringJoiner(",");
        for (Item item : canonica.items()) {
            items.add("{\"productoId\":" + item.productoId() + ",\"cantidad\":" + item.cantidad() + "}");
        }
        String json = "{\"versionContrato\":1,\"items\":[" + items + "]}";
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8)));
            return new Entrada(uuid, consumidor, canonica, hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private static boolean metadatosValidos(Producto p) {
        return p.getCodigo() != null && !p.getCodigo().isBlank() && p.getCodigo().length() <= 64
                && p.getNombre() != null && !p.getNombre().isBlank() && p.getNombre().length() <= 255
                && p.getCategoria() != null
                && (p.getPlataforma() == null || p.getPlataforma().length() <= 255)
                && (p.getCategoria() != CategoriaProducto.VIDEOJUEGO
                    || (p.getFormato() != null && p.getPlataforma() != null && !p.getPlataforma().isBlank()));
    }

    private static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private record Entrada(String id, ConsumidorStock consumidor, Solicitud solicitud, String hash) {}
}
