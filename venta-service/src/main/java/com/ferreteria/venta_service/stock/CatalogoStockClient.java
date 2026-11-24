package com.ferreteria.venta_service.stock;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import static com.ferreteria.venta_service.stock.ResultadoStock.Tipo.*;

/**
 * Sin endpoints ni transacciones. Cada suscripción es un intento explícito;
 * el coordinador debe persistir UUID/solicitud antes de suscribir.
 */
@Component
public class CatalogoStockClient {
    private static final String BASE_PATH = "/internal/stock/operaciones/";
    private final LoadBalancerClient loadBalancer;
    private final StockServiceTokenProvider tokens;
    private final WebClient http;
    private final Duration totalTimeout;
    private final JsonMapper json;

    public CatalogoStockClient(LoadBalancerClient loadBalancer, StockServiceTokenProvider tokens,
            JsonMapper mapper,
            @Value("${stock.client.connect-timeout-ms:2000}") int connectTimeout,
            @Value("${stock.client.response-timeout-ms:10000}") int responseTimeout,
            @Value("${stock.client.total-timeout-ms:15000}") int totalTimeout) {
        if (connectTimeout <= 0 || responseTimeout <= 0 || totalTimeout <= 0) {
            throw new IllegalArgumentException("Los tiempos máximos del cliente de stock deben ser positivos");
        }
        this.loadBalancer = loadBalancer;
        this.tokens = tokens;
        this.totalTimeout = Duration.ofMillis(totalTimeout);
        json = mapper.rebuild().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).build();
        HttpClient transport = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(responseTimeout))
                .disableRetry(true).followRedirect(false).wiretap(false);
        // Aislado del builder heredado: sin filtros de retry/autenticación humana.
        http = WebClient.builder().clientConnector(new ReactorClientHttpConnector(transport))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(512 * 1024)).build();
    }

    public Mono<ResultadoStock> aplicar(UUID id, ContratoStock.Solicitud solicitud) {
        return ejecutar(id, Accion.APLICAR, Objects.requireNonNull(solicitud));
    }

    public Mono<ResultadoStock> consultar(UUID id) {
        return ejecutar(id, Accion.CONSULTAR, null);
    }

    public Mono<ResultadoStock> revertir(UUID id, ContratoStock.Solicitud solicitudOriginal) {
        return ejecutar(id, Accion.REVERTIR, Objects.requireNonNull(solicitudOriginal));
    }

    /** Consulta explícita por el UUID incierto, sin reenvío de POST ni nuevo identificador. */
    public Mono<ResultadoStock> reconciliar(UUID id) {
        return consultar(id);
    }

    private Mono<ResultadoStock> ejecutar(UUID id, Accion accion, ContratoStock.Solicitud solicitud) {
        Objects.requireNonNull(id, "Se requiere el UUID persistido por el coordinador");
        byte[] body = solicitud == null ? null : json.writeValueAsBytes(solicitud);
        if (body != null && body.length > 16 * 1024) throw new IllegalArgumentException("Solicitud supera 16 KiB");
        return Mono.fromCallable(() -> loadBalancer.choose("catalogo-service"))
                // Discovery usa APIs bloqueantes; no ocupar un event loop de Reactor.
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(instance -> {
                    URI destino = instance.getUri().resolve(BASE_PATH + id + accion.sufijo);
                    var request = http.method(accion == Accion.CONSULTAR ? HttpMethod.GET : HttpMethod.POST)
                            .uri(destino).accept(MediaType.APPLICATION_JSON)
                            .headers(headers -> headers.setBearerAuth(tokens.emitir()));
                    WebClient.RequestHeadersSpec<?> ready = body == null ? request
                            : request.contentType(MediaType.APPLICATION_JSON).bodyValue(body);
                    return ready.exchangeToMono(response -> {
                        int status = response.statusCode().value();
                        return response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
                                .map(bytes -> interpretar(id, accion, solicitud, status, bytes))
                                .onErrorReturn(resultado(id, RESULTADO_INCIERTO, status, "RESPUESTA_NO_INTERPRETABLE"));
                    });
                })
                .switchIfEmpty(Mono.just(resultado(id, CATALOGO_NO_DISPONIBLE, null, "SIN_INSTANCIA_CATALOGO")))
                .timeout(totalTimeout)
                .onErrorResume(ex -> Mono.just(errorTransporte(id, ex)));
    }

    private ResultadoStock interpretar(UUID id, Accion accion, ContratoStock.Solicitud solicitud,
            int status, byte[] body) {
        try {
            var tree = json.readTree(body);
            if (tree == null || !tree.isObject()) throw new IllegalArgumentException();
            if ((status == 200 || status == 202 || status == 409) && tree.has("estado")) {
                var operacion = json.treeToValue(tree, ContratoStock.Operacion.class);
                if (!id.equals(operacion.operacionId()) || !estadoHttpValido(accion, status, operacion.estado())) {
                    throw new IllegalArgumentException();
                }
                if (solicitud != null && operacion.snapshot() != null) {
                    var enviados = solicitud.items().stream().collect(Collectors.toMap(
                            ContratoStock.Item::productoId, ContratoStock.Item::cantidad));
                    var recibidos = operacion.snapshot().items().stream().collect(Collectors.toMap(
                            ContratoStock.Detalle::productoId, ContratoStock.Detalle::cantidad));
                    if (!enviados.equals(recibidos)) throw new IllegalArgumentException();
                }
                return new ResultadoStock(id, ResultadoStock.Tipo.valueOf(operacion.estado().name()),
                        status, operacion.codigo(), operacion);
            }
            String codigo = tree.has("codigo") && tree.get("codigo").isString()
                    ? tree.get("codigo").stringValue() : "";
            if (status == 401 || status == 403) return resultado(id, AUTENTICACION_INTERNA, status, "AUTENTICACION_INTERNA");
            if (status == 404 && accion == Accion.CONSULTAR && "OPERACION_INEXISTENTE".equals(codigo)) {
                return resultado(id, INEXISTENTE, status, codigo);
            }
            if (status == 409 && Set.of("OPERACION_CONTENIDO_DISTINTO", "OPERACION_CONSUMIDOR_DISTINTO").contains(codigo)) {
                return resultado(id, CONFLICTO_IDEMPOTENCIA, status, codigo);
            }
            if (status == 409 && Set.of("REVERSION_PRODUCTO_INEXISTENTE", "REVERSION_STOCK_FUERA_DE_RANGO").contains(codigo)) {
                return resultado(id, CONFLICTO_REVERSION, status, codigo);
            }
            if (Set.of(400, 413, 415).contains(status) && Set.of("UUID_INVALIDO", "SOLICITUD_INVALIDA",
                    "SOLICITUD_DEMASIADO_GRANDE", "FORMATO_NO_SOPORTADO").contains(codigo)) {
                return resultado(id, SOLICITUD_INVALIDA, status, codigo);
            }
        } catch (RuntimeException ex) {
            // No retener ni propagar cuerpo/cause: podría contener información técnica o credenciales.
        }
        if (status == 401 || status == 403) return resultado(id, AUTENTICACION_INTERNA, status, "AUTENTICACION_INTERNA");
        if (status == 502 || status == 503 || status == 504) {
            return resultado(id, CATALOGO_NO_DISPONIBLE, status, "CATALOGO_NO_DISPONIBLE");
        }
        if (status >= 500) return resultado(id, ERROR_TECNICO, status, "ERROR_TECNICO_STOCK");
        return resultado(id, RESULTADO_INCIERTO, status, "RESPUESTA_NO_INTERPRETABLE");
    }

    private boolean estadoHttpValido(Accion accion, int status, ContratoStock.Estado estado) {
        return switch (accion) {
            case CONSULTAR -> status == 200;
            case APLICAR -> switch (estado) {
                case APLICADA -> status == 200;
                case PENDIENTE -> status == 202;
                case RECHAZADA, REVERTIDA, CANCELADA -> status == 409;
            };
            case REVERTIR -> status == 200 && (estado == ContratoStock.Estado.REVERTIDA
                    || estado == ContratoStock.Estado.CANCELADA || estado == ContratoStock.Estado.RECHAZADA);
        };
    }

    private ResultadoStock errorTransporte(UUID id, Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof ReadTimeoutException) {
                return resultado(id, RESULTADO_INCIERTO, null, "TIMEOUT_STOCK");
            }
            if (cause instanceof java.net.ConnectException || cause instanceof java.net.UnknownHostException) {
                return resultado(id, CATALOGO_NO_DISPONIBLE, null, "CATALOGO_NO_DISPONIBLE");
            }
        }
        if (ex instanceof WebClientRequestException) {
            return resultado(id, RESULTADO_INCIERTO, null, "COMUNICACION_STOCK_INTERRUMPIDA");
        }
        return resultado(id, ERROR_TECNICO, null, "ERROR_TECNICO_STOCK");
    }

    private ResultadoStock resultado(UUID id, ResultadoStock.Tipo tipo, Integer status, String codigo) {
        return new ResultadoStock(id, tipo, status, codigo, null);
    }

    private enum Accion {
        APLICAR("/aplicar"), CONSULTAR(""), REVERTIR("/revertir");
        private final String sufijo;
        Accion(String sufijo) { this.sufijo = sufijo; }
    }
}
