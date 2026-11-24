package com.ferreteria.venta_service.stock;

import com.ferreteria.venta_service.exception.PreparacionCheckoutException;
import com.ferreteria.venta_service.service.DespachoFijoV1;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import static com.ferreteria.venta_service.exception.PreparacionCheckoutException.Codigo.*;

/** Solo GET público directo a Catálogo. No transporta tokens humanos ni de stock. */
@Component
public class CatalogoProductosClient {
    public record Producto(Long id, ContratoStock.Categoria categoria, ContratoStock.Formato formato) {}

    private final LoadBalancerClient loadBalancer;
    private final WebClient http;
    private final JsonMapper json;
    private final Duration totalTimeout;

    public CatalogoProductosClient(LoadBalancerClient loadBalancer, JsonMapper mapper,
            @Value("${catalogo.productos.connect-timeout-ms:2000}") int connectTimeout,
            @Value("${catalogo.productos.response-timeout-ms:5000}") int responseTimeout,
            @Value("${catalogo.productos.total-timeout-ms:15000}") int totalTimeout) {
        if (connectTimeout <= 0 || responseTimeout <= 0 || totalTimeout <= 0) {
            throw new IllegalArgumentException("Timeouts de lectura de productos deben ser positivos");
        }
        this.loadBalancer = loadBalancer;
        this.totalTimeout = Duration.ofMillis(totalTimeout);
        this.json = mapper.rebuild().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).build();
        HttpClient transport = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(responseTimeout))
                .disableRetry(true).followRedirect(false).wiretap(false);
        http = WebClient.builder().clientConnector(new ReactorClientHttpConnector(transport))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(128 * 1024)).build();
    }

    /** Llamador imperativo fuera de una TX; plazo total para TODOS los productos (máximo 100). */
    public List<Producto> consultar(ContratoStock.Solicitud solicitud) {
        Objects.requireNonNull(solicitud);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Consulta de Catálogo dentro de una transacción no permitida");
        }
        return Flux.fromIterable(solicitud.items())
                .flatMapSequential(this::consultarUno, 4, 1)
                .collectList().timeout(totalTimeout)
                .onErrorMap(ex -> ex instanceof PreparacionCheckoutException ? ex
                        : new PreparacionCheckoutException(CATALOGO_NO_DISPONIBLE))
                .block();
    }

    private Mono<Producto> consultarUno(ContratoStock.Item item) {
        return Mono.fromCallable(() -> loadBalancer.choose("catalogo-service"))
                .subscribeOn(Schedulers.boundedElastic())
                .switchIfEmpty(Mono.error(new PreparacionCheckoutException(CATALOGO_NO_DISPONIBLE)))
                .flatMap(instance -> http.get()
                        .uri(instance.getUri().resolve("/api/productos/" + item.productoId()))
                        .accept(MediaType.APPLICATION_JSON)
                        .exchangeToMono(response -> {
                            int status = response.statusCode().value();
                            if (status != 200) {
                                var codigo = status == 404 ? PRODUCTO_INEXISTENTE_O_NO_PUBLICADO
                                        : status == 401 || status == 403 ? ACCESO_CATALOGO_RECHAZADO
                                        : CATALOGO_NO_DISPONIBLE;
                                return response.releaseBody().then(Mono.error(new PreparacionCheckoutException(codigo)));
                            }
                            return response.bodyToMono(byte[].class)
                                    .switchIfEmpty(Mono.error(new PreparacionCheckoutException(METADATOS_INVALIDOS)))
                                    .map(bytes -> interpretar(item, bytes));
                        }));
    }

    private Producto interpretar(ContratoStock.Item item, byte[] bytes) {
        try {
            // EntityModel de HATEOAS presenta campos del DTO en raíz; _links se ignora.
            JsonNode root = json.readTree(bytes);
            if (root == null || !root.isObject()) throw new IllegalArgumentException();
            JsonNode id = root.get("id");
            JsonNode stock = root.get("stock");
            JsonNode habilitado = root.get("habilitado");
            JsonNode disponible = root.get("disponible");
            if (id == null || !id.isIntegralNumber() || !id.canConvertToLong()
                    || id.longValue() != item.productoId()
                    || stock == null || !stock.isIntegralNumber() || !stock.canConvertToInt()
                    || stock.intValue() < 0 || habilitado == null || !habilitado.isBoolean()
                    || disponible == null || !disponible.isBoolean()) throw new IllegalArgumentException();
            if (!habilitado.booleanValue()) throw new PreparacionCheckoutException(PRODUCTO_INEXISTENTE_O_NO_PUBLICADO);
            if (disponible.booleanValue() != (stock.intValue() > 0)) throw new IllegalArgumentException();
            if (!disponible.booleanValue() || stock.intValue() < item.cantidad()) {
                throw new PreparacionCheckoutException(PRODUCTO_NO_DISPONIBLE);
            }
            JsonNode categoria = root.get("categoria");
            JsonNode formato = root.get("formato");
            if (categoria == null || !categoria.isString() || formato == null
                    || (!formato.isNull() && !formato.isString())) throw new IllegalArgumentException();
            var c = ContratoStock.Categoria.valueOf(categoria.stringValue());
            var f = formato.isNull() ? null : ContratoStock.Formato.valueOf(formato.stringValue());
            if (c == ContratoStock.Categoria.VIDEOJUEGO) {
                JsonNode plataforma = root.get("plataforma");
                if (plataforma == null || !plataforma.isString() || plataforma.stringValue().isBlank()) {
                    throw new IllegalArgumentException();
                }
            }
            // Mismas reglas que el snapshot definitivo, sin crear snapshots/precios ficticios.
            DespachoFijoV1.clasificarProducto(c, f);
            return new Producto(item.productoId(), c, f);
        } catch (PreparacionCheckoutException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new PreparacionCheckoutException(METADATOS_INVALIDOS);
        }
    }
}
