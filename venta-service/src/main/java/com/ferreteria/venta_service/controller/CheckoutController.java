package com.ferreteria.venta_service.controller;

import com.ferreteria.venta_service.Dto.CheckoutRequest;
import com.ferreteria.venta_service.Dto.CheckoutResponse;
import com.ferreteria.venta_service.exception.CheckoutHttpException;
import com.ferreteria.venta_service.model.DatosCheckoutSolicitud;
import com.ferreteria.venta_service.security.JwtUserId;
import com.ferreteria.venta_service.service.CoordinadorPedidoService;
import com.ferreteria.venta_service.service.PedidoConsultaService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/** MVC servlet: el coordinador bloqueante se ejecuta en el hilo servlet, no en Reactor. */
@RestController
public class CheckoutController {
    private static final int MAX_BODY = 32 * 1024;
    private final CoordinadorPedidoService coordinador;
    private final PedidoConsultaService consultas;
    private final JsonMapper json;

    public CheckoutController(CoordinadorPedidoService coordinador, PedidoConsultaService consultas,
            JsonMapper mapper) {
        this.coordinador = coordinador;
        this.consultas = consultas;
        json = mapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    }

    @PostMapping("/api/pedidos")
    public ResponseEntity<CheckoutResponse> comprar(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        Long compradorId = JwtUserId.getValidId(jwt);
        if (compradorId == null || !"CLIENTE".equals(jwt.getClaimAsString("role"))) {
            throw new AccessDeniedException("Identidad no autorizada");
        }
        var claves = Collections.list(request.getHeaders("Idempotency-Key"));
        if (claves.size() != 1 || !claves.getFirst().matches(
                "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new CheckoutHttpException(400, "IDEMPOTENCY_KEY_INVALIDA");
        }
        var entrada = leer(request);
        var resultado = coordinador.comprar(compradorId, claves.getFirst(), entrada.items(), entrada.entrada());
        return switch (resultado.estado()) {
            case RECUPERABLE, OCUPADA -> ResponseEntity.accepted().contentType(MediaType.APPLICATION_JSON)
                    .header("Retry-After", "5")
                    .body(CheckoutResponse.sinPedido("PENDIENTE", "REINTENTAR_MISMA_CLAVE", false));
            case REVISION_REQUERIDA -> ResponseEntity.status(503).contentType(MediaType.APPLICATION_JSON)
                    .body(CheckoutResponse.sinPedido("REVISION_REQUERIDA", "REVISION_REQUERIDA", false));
            case RESUELTA -> {
                var terminal = resultado.solicitud().resultado();
                if (terminal.tipo() == DatosCheckoutSolicitud.TipoResultado.PEDIDO_CREADO) {
                    // Lectura propia, después del commit y de todo HTTP. Estado real, no CONFIRMADO ficticio.
                    var pedido = consultas.obtenerPropioPorId(terminal.pedidoId(), compradorId);
                    yield ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                            .location(URI.create("/api/pedidos/" + pedido.id()))
                            .body(new CheckoutResponse("PEDIDO_CREADO", "PEDIDO_CREADO",
                                    pedido.id(), pedido.estado(), true));
                }
                yield ResponseEntity.status(422).contentType(MediaType.APPLICATION_JSON).body(CheckoutResponse.sinPedido(
                        terminal.tipo().name(), "COMPRA_NO_COMPLETADA", true));
            }
        };
    }

    private CheckoutRequest leer(HttpServletRequest request) {
        // Validación aquí mantiene también los errores de Content-Type en el handler seguro de checkout.
        try {
            var tipo = MediaType.parseMediaType(request.getContentType() == null ? "" : request.getContentType());
            if (!"application".equalsIgnoreCase(tipo.getType()) || !"json".equalsIgnoreCase(tipo.getSubtype())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new CheckoutHttpException(415, "CONTENIDO_NO_SOPORTADO");
        }
        // Acotado también sin Content-Length (chunked). No confiar solo en el encabezado.
        if (request.getContentLengthLong() > MAX_BODY) throw new CheckoutHttpException(413, "CUERPO_DEMASIADO_GRANDE");
        if (request.getHeader("Content-Encoding") != null
                && !"identity".equalsIgnoreCase(request.getHeader("Content-Encoding"))) {
            throw new CheckoutHttpException(415, "CODIFICACION_NO_SOPORTADA");
        }
        try {
            byte[] bytes = request.getInputStream().readNBytes(MAX_BODY + 1);
            if (bytes.length > MAX_BODY) throw new CheckoutHttpException(413, "CUERPO_DEMASIADO_GRANDE");
            var tree = json.readTree(bytes);
            if (tree == null || !tree.isObject()) throw new IllegalArgumentException();
            return json.treeToValue(tree, CheckoutRequest.class);
        } catch (CheckoutHttpException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new CheckoutHttpException(400, "SOLICITUD_INVALIDA");
        }
    }
}
