package com.ferreteria.catalogo_service.service;

import com.ferreteria.catalogo_service.Dto.RawgDtos;
import com.ferreteria.catalogo_service.exception.BadRequestException;
import com.ferreteria.catalogo_service.exception.RawgException;
import com.ferreteria.catalogo_service.repository.ProductoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RawgService {
    private static final String BASE = "https://api.rawg.io/api";
    private static final int PAGE_SIZE = 20;
    private final String apiKey;
    private final ProductoRepository repository;
    private final JsonMapper mapper = JsonMapper.builder().build();
    // Independiente de WebClient LoadBalanced y de cualquier contexto de seguridad.
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public RawgService(@Value("${RAWG_API_KEY:}") String apiKey, ProductoRepository repository) {
        this.apiKey = apiKey.trim();
        this.repository = repository;
    }

    public RawgDtos.Busqueda buscar(String query, int page) {
        if (query == null || query.isBlank() || query.strip().length() > 100 || page < 1) {
            throw new BadRequestException("query debe tener entre 1 y 100 caracteres y page debe ser positivo.");
        }
        JsonNode root = consultar("/games", "&search=" + encode(query.strip())
                + "&page=" + page + "&page_size=" + PAGE_SIZE, false);
        if (!root.path("results").isArray() || !root.path("count").isIntegralNumber()
                || !root.path("count").canConvertToLong() || root.path("count").longValue() < 0) {
            throw respuestaInvalida();
        }
        List<JsonNode> juegos = new ArrayList<>();
        root.path("results").forEach(juegos::add);
        if (juegos.size() > PAGE_SIZE) {
            throw respuestaInvalida();
        }
        List<Long> ids = juegos.stream().map(this::id).toList();
        Set<Long> importados = importados(ids);
        List<RawgDtos.Juego> resultados = juegos.stream().map(juego ->
                new RawgDtos.Juego(id(juego), texto(juego, "name"), imagen(juego),
                        nombres(juego, "platforms", true), importados.contains(id(juego)))).toList();
        long total = root.path("count").longValue();
        return new RawgDtos.Busqueda(resultados, page, PAGE_SIZE, total,
                (long) page * PAGE_SIZE < total);
    }

    public RawgDtos.Detalle detalle(long rawgId) {
        if (rawgId <= 0) {
            throw new BadRequestException("rawgId debe ser un entero positivo.");
        }
        JsonNode juego = consultar("/games/" + rawgId, "", true);
        if (id(juego) != rawgId) {
            throw respuestaInvalida();
        }
        String descripcion = texto(juego, "description_raw");
        if (descripcion == null) {
            descripcion = texto(juego, "description");
        }
        return new RawgDtos.Detalle(rawgId, texto(juego, "name"), textoSeguro(descripcion),
                imagen(juego), nombres(juego, "genres", false),
                nombres(juego, "platforms", true), importados(List.of(rawgId)).contains(rawgId));
    }

    private Set<Long> importados(List<Long> ids) {
        return ids.isEmpty() ? Set.of() : new HashSet<>(repository.findRawgIdsExistentes(ids));
    }

    private JsonNode consultar(String path, String query, boolean detalle) {
        if (apiKey.isBlank()) {
            throw new RawgException(HttpStatus.SERVICE_UNAVAILABLE, "RAWG_NO_CONFIGURADO",
                    "La integración RAWG no tiene clave configurada.");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create(BASE + path + "?key=" + encode(apiKey) + query))
                    .timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET().build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                throw new RawgException(HttpStatus.BAD_GATEWAY, "RAWG_CLAVE_RECHAZADA",
                        "RAWG rechazó las credenciales de la integración.");
            }
            if (status == 429) {
                throw new RawgException(HttpStatus.SERVICE_UNAVAILABLE, "RAWG_CUOTA_AGOTADA",
                        "RAWG ha limitado las solicitudes. Intente más tarde.");
            }
            if (status == 404) {
                throw new RawgException(detalle ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST,
                        detalle ? "RAWG_NO_ENCONTRADO" : "RAWG_PAGINA_INEXISTENTE",
                        detalle ? "Videojuego no encontrado en RAWG." : "Página de búsqueda inexistente.");
            }
            if (status != 200) {
                throw new RawgException(HttpStatus.BAD_GATEWAY, "RAWG_NO_DISPONIBLE",
                        "RAWG no pudo atender la solicitud.");
            }
            JsonNode result = mapper.readTree(response.body());
            if (result == null || !result.isObject()) {
                throw respuestaInvalida();
            }
            return result;
        } catch (RawgException ex) {
            throw ex;
        } catch (HttpTimeoutException ex) {
            throw new RawgException(HttpStatus.GATEWAY_TIMEOUT, "RAWG_TIMEOUT",
                    "RAWG superó el tiempo de espera.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RawgException(HttpStatus.SERVICE_UNAVAILABLE, "RAWG_INTERRUPCION",
                    "La consulta RAWG fue interrumpida.");
        } catch (java.io.IOException ex) {
            throw new RawgException(HttpStatus.BAD_GATEWAY, "RAWG_CONEXION",
                    "No se pudo conectar con RAWG.");
        } catch (RuntimeException ex) {
            throw respuestaInvalida();
        }
    }

    private RawgException respuestaInvalida() {
        return new RawgException(HttpStatus.BAD_GATEWAY, "RAWG_RESPUESTA_INVALIDA",
                "RAWG devolvió una respuesta no válida.");
    }

    private long id(JsonNode juego) {
        JsonNode value = juego.path("id");
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw respuestaInvalida();
        }
        return value.longValue();
    }

    private String texto(JsonNode node, String campo) {
        JsonNode value = node.path(campo);
        if (!value.isTextual() || value.asString().isBlank()) {
            return null;
        }
        // Defensa adicional: ningún texto remoto puede reflejar nuestra credencial.
        return apiKey.isEmpty() ? value.asString() : value.asString().replace(apiKey, "[REDACTADO]");
    }

    private String imagen(JsonNode juego) {
        String value = texto(juego, "background_image");
        if (value == null || value.length() > 2048) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null ? value : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private List<String> nombres(JsonNode juego, String campo, boolean anidado) {
        List<String> values = new ArrayList<>();
        JsonNode array = juego.path(campo);
        if (array.isArray()) {
            for (JsonNode item : array) {
                String value = texto(anidado ? item.path("platform") : item, "name");
                if (value != null && !values.contains(value)) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    private String textoSeguro(String html) {
        if (html == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        try {
            new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
                private boolean ignorar;

                @Override
                public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                    if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) ignorar = true;
                }

                @Override
                public void handleEndTag(HTML.Tag tag, int position) {
                    if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) ignorar = false;
                    text.append(' ');
                }

                @Override
                public void handleText(char[] data, int position) {
                    if (!ignorar) text.append(data).append(' ');
                }
            }, true);
            // Evita que entidades decodificadas vuelvan a constituir etiquetas.
            return HtmlUtils.htmlEscape(text.toString().replaceAll("\\s+", " ").strip());
        } catch (java.io.IOException ex) {
            throw respuestaInvalida();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
