package com.ferreteria.catalogo_service.Dto;

import java.io.IOException;
import java.util.ArrayList;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import com.ferreteria.catalogo_service.exception.BadRequestException;
import com.ferreteria.catalogo_service.model.DatosOperacionStock;
import com.ferreteria.catalogo_service.service.OperacionStockService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lector exclusivo del contrato interno: acota bytes incluso sin Content-Length
 * y rechaza campos extra, duplicados, decimales y coerciones string/número.
 */
@Component
public class StockSolicitudHttpReader {
    public static final int MAX_BYTES = 16 * 1024;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public DatosOperacionStock.Solicitud leer(HttpServletRequest request) throws IOException {
        try {
            var type = org.springframework.http.MediaType.parseMediaType(
                    request.getContentType() == null ? "" : request.getContentType());
            if (!"application".equalsIgnoreCase(type.getType()) || !"json".equalsIgnoreCase(type.getSubtype())
                    || (type.getCharset() != null && !java.nio.charset.StandardCharsets.UTF_8.equals(type.getCharset()))) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        if (request.getHeader("Content-Encoding") != null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        if (request.getContentLengthLong() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        }
        byte[] bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        JsonNode root;
        try {
            root = mapper.readTree(bytes);
        } catch (tools.jackson.core.JacksonException ex) {
            throw invalida();
        }
        if (root == null || !root.isObject() || root.size() != 2 || !root.has("versionContrato")
                || !root.has("items") || !root.get("versionContrato").isIntegralNumber()
                || !root.get("versionContrato").canConvertToInt() || root.get("versionContrato").intValue() != 1
                || !root.get("items").isArray() || root.get("items").isEmpty()
                || root.get("items").size() > OperacionStockService.MAX_PRODUCTOS) throw invalida();
        var items = new ArrayList<DatosOperacionStock.Item>();
        for (JsonNode item : root.get("items")) {
            if (!item.isObject() || item.size() != 2 || !item.has("productoId") || !item.has("cantidad")
                    || !item.get("productoId").isIntegralNumber() || !item.get("productoId").canConvertToLong()
                    || !item.get("cantidad").isIntegralNumber() || !item.get("cantidad").canConvertToInt()) {
                throw invalida();
            }
            items.add(new DatosOperacionStock.Item(item.get("productoId").longValue(),
                    item.get("cantidad").intValue()));
        }
        // Positividad, duplicados y canonicalización siguen siendo responsabilidad del núcleo.
        return new DatosOperacionStock.Solicitud(1, items);
    }

    private BadRequestException invalida() {
        return new BadRequestException("SOLICITUD_INVALIDA");
    }
}
