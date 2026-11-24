# API interna de stock — Etapa 4.3

Adaptador HTTP sobre el núcleo de 4.2. Sin cliente de Ventas, compras ni emisión
HTTP de tokens. Sin cambios de esquema: SQL 07 ya aplicado por el usuario.
Esta etapa se verifica mediante compilación e inspección; no se arrancaron
servicios ni se ejecutaron peticiones HTTP o SQL.

## Acceso y credenciales

Acceso directo interno a Catálogo, en local `http://localhost:9091`.
Gateway 9090 no publica estas operaciones. En despliegue, proteger transporte
con TLS y restringir 9091 a la red de servicios; no transportar credenciales
por HTTP fuera del entorno local.

`StockServiceSecurityConfig` crea una cadena prioritaria exclusivamente para
`/internal` y `/internal/**`. Usa un decoder local (no un bean JwtDecoder
adicional) para mantener intacto el decoder humano.

- Algoritmo permitido: HS256.
- `STOCK_SERVICE_JWT_SECRET`: leído exclusivamente mediante `System.getenv`.
- Mínimo 32 bytes UTF-8; generar aleatoriamente. No se decodifica como Base64:
  si se usa una cadena Base64, sus caracteres UTF-8 son la clave.
- Debe diferir del secreto humano resuelto. No hay fallback ni valor por defecto.
- Si falta/es corta/es igual a la humana, Catálogo falla al arrancar.
- `iss`: `checkpoint-stock-services`.
- `aud`: debe incluir `catalogo-stock`.
- `sub`: exactamente `venta-service`.
- `iat` y `exp` obligatorios, `iat <= ahora`, `exp > iat`, duración máxima 300 s.
- Expiración y `nbf` si existe se validan sin tolerancia de reloj. Sincronizar relojes.
- Claim `role` prohibido. `scope` debe ser una cadena.
- Permiso obligatorio: `stock:operar` dentro de `scope` (autoridad `SCOPE_stock:operar`).

Ejemplo de payload, con fechas NumericDate Unix reales al emitir:

```json
{
  "iss": "checkpoint-stock-services",
  "sub": "venta-service",
  "aud": ["catalogo-stock"],
  "iat": 1795388400,
  "exp": 1795388700,
  "scope": "stock:operar"
}
```

El controlador deriva `ConsumidorStock.VENTA_SERVICE` del JWT autenticado y su
autoridad. No acepta consumidor, roles o identidad en el body ni en X-headers.
JWT humanos CLIENTE/VENDEDOR/ADMINISTRADOR no autentican esta cadena.
El controlador está oculto de la documentación Swagger pública con `@Hidden`.
La autenticación se exige también al acceder directamente por 9091.

## Contrato HTTP

Todas las llamadas incluyen `Authorization: Bearer <token-temporal>`.
POST requiere `Content-Type: application/json` (UTF-8 si declara charset).
Usar `Accept: application/json`. No enviar Content-Encoding.

| Método y ruta | Acción |
|---|---|
| `POST /internal/stock/operaciones/{uuid}/aplicar` | Registrar intención y aplicar. |
| `GET /internal/stock/operaciones/{uuid}` | Consultar cualquier estado persistido. |
| `POST /internal/stock/operaciones/{uuid}/revertir` | Restituir o cerrar preventivamente. |

No hay listados, borrado ni métodos administrativos internos adicionales.
UUID completo con guiones; se normalizan mayúsculas a minúsculas.
POST tiene límite de **16 KiB**, incluso con transferencia sin Content-Length.
Máximo **100 productos**. El lector estricto es local a estas rutas:
rechaza campos desconocidos, claves JSON repetidas, JSON concatenados,
números decimales, strings numéricos, nulls y números fuera de Long/Integer.
No acepta precios, totales o metadatos comerciales.
El núcleo existente valida positividad, duplicados y canonicaliza.

### Aplicación

```http
POST /internal/stock/operaciones/7bb26a45-bf1d-4cdf-b6fa-9b59c6317f24/aplicar
Content-Type: application/json
Authorization: Bearer <token-temporal>
```

```json
{
  "versionContrato": 1,
  "items": [
    {"productoId": 12, "cantidad": 2},
    {"productoId": 35, "cantidad": 1}
  ]
}
```

Respuesta ilustrativa (los valores reales salen de Catálogo):

```json
{
  "operacionId": "7bb26a45-bf1d-4cdf-b6fa-9b59c6317f24",
  "versionContrato": 1,
  "estado": "APLICADA",
  "codigo": "OPERACION_APLICADA",
  "snapshot": {
    "versionContrato": 1,
    "items": [
      {
        "productoId": 12,
        "sku": "SKU-12",
        "nombre": "Producto A",
        "categoria": "VIDEOJUEGO",
        "formato": "FISICO",
        "plataforma": "PS5",
        "cantidad": 2,
        "precioUnitario": 39990,
        "subtotal": 79980
      },
      {
        "productoId": 35,
        "sku": "SKU-35",
        "nombre": "Producto B",
        "categoria": "ACCESORIO",
        "formato": null,
        "plataforma": null,
        "cantidad": 1,
        "precioUnitario": 39990,
        "subtotal": 39990
      }
    ],
    "totalProductos": 119970
  },
  "fechaCreacion": "2026-11-22T23:00:00Z",
  "fechaActualizacion": "2026-11-22T23:00:01Z",
  "fechaAplicacion": "2026-11-22T23:00:01Z",
  "fechaCierre": null
}
```

La respuesta real contiene todos los ítems. No devuelve solicitud, hash ni
consumidor internos. El snapshot es un valor inmutable, no una entidad JPA.
Los precios efectivos son CLP finales Long, sin IVA adicional.
La respuesta de aplicar incluye `Location` hacia la consulta.

### Consulta

```http
GET /internal/stock/operaciones/7bb26a45-bf1d-4cdf-b6fa-9b59c6317f24
Authorization: Bearer <token-temporal>
```

Devuelve el mismo DTO con el estado actual. Por ejemplo, una operación cancelada
antes de aplicar tiene `estado: "CANCELADA"`, `codigo: "OPERACION_CANCELADA"`,
`snapshot: null`, `fechaAplicacion: null` y cierre igual a actualización.
Una REVERTIDA conserva el snapshot original, incluso si el producto cambió.

### Reversión

```http
POST /internal/stock/operaciones/7bb26a45-bf1d-4cdf-b6fa-9b59c6317f24/revertir
Content-Type: application/json
Authorization: Bearer <token-temporal>
```

```json
{
  "versionContrato": 1,
  "items": [
    {"productoId": 12, "cantidad": 2},
    {"productoId": 35, "cantidad": 1}
  ]
}
```

Debe enviar la solicitud original completa. Se compara su contenido canónico,
no el orden del array. Las cantidades a restituir se leen únicamente del snapshot.
Si no existe, registra CANCELADA; si está PENDIENTE, la cierra; si está APLICADA,
restituye y devuelve REVERTIDA. RECHAZADA se conserva sin restitución.
Una respuesta REVERTIDA tiene `codigo: "OPERACION_REVERTIDA"`, snapshot original
y `fechaCierre = fechaActualizacion >= fechaAplicacion`.

## Códigos y recuperación

| HTTP | Situación / código |
|---|---|
| 200 | Aplicada, incluida repetición; consulta existente en cualquier estado; reversión resuelta/idempotente, incluso RECHAZADA sin existencias que restituir. |
| 202 | Si aplicar devuelve PENDIENTE; `Retry-After: 2` y Location. El núcleo actual normalmente resuelve sin dejar este resultado en una respuesta exitosa. |
| 400 | `UUID_INVALIDO` o `SOLICITUD_INVALIDA`; no registrar intención para una solicitud inválida. |
| 401 | `SERVICIO_NO_AUTENTICADO`: falta JWT, firma/algoritmo/identidad/claims inválidos o expiración. |
| 403 | `SERVICIO_SIN_PERMISO`: JWT de servicio válido sin scope requerido o ruta/método interno no permitido. |
| 404 | `OPERACION_INEXISTENTE` en consulta. |
| 409 | Aplicación RECHAZADA (DTO completo con su código de negocio), CANCELADA o REVERTIDA; o conflicto de contenido/reversión. |
| 413 | `SOLICITUD_DEMASIADO_GRANDE`. |
| 415 | `FORMATO_NO_SOPORTADO`: Content-Type/charset/Content-Encoding no soportados. |
| 503 | `STOCK_TEMPORALMENTE_NO_DISPONIBLE`; bloqueo o acceso transitorio a BD identificado. `Retry-After: 2`. |
| 500 | `ERROR_TECNICO_STOCK`; error técnico no clasificado. Consultar resultado antes de decidir. |

Conflictos sin snapshot: `OPERACION_CONTENIDO_DISTINTO`,
`OPERACION_CONSUMIDOR_DISTINTO`, `REVERSION_PRODUCTO_INEXISTENTE`,
`REVERSION_STOCK_FUERA_DE_RANGO`. Los errores devuelven `{"codigo":"..."}`;
503 añade un mensaje de recuperación. No se publican mensajes SQL ni excepciones.
Los rechazos persistidos son los documentados en `OPERACIONES_STOCK.md`.

**Timeout o conexión cortada no prueban fracaso.** El commit puede haber ocurrido.
Consultar el mismo UUID con un token vigente:

1. APLICADA: usar snapshot persistido; no generar otro UUID para reintentar.
2. PENDIENTE: repetir aplicar con la misma solicitud.
3. RECHAZADA: rechazo definitivo de esa operación.
4. REVERTIDA/CANCELADA: cerrada; aplicar devuelve 409 sin descontar.
5. 404: puede existir una solicitud en tránsito. Reintentar el mismo UUID o
   cerrar preventivamente mediante revertir si se decide abandonar.
6. 500/503: estado desconocido hasta reconciliar; backoff acotado, sin cambiar UUID.

El adaptador no tiene `@Transactional`; llama registrar y luego aplicar.
Los `TransactionTemplate REQUIRES_NEW` de 4.2 quedan intactos.

## Configuración temporal en Windows / PowerShell

Procedimiento manual para cuando se autorice iniciar servicios. No fue ejecutado.
No usar `setx`, archivos .env versionados ni valores literales de secretos.
En una PowerShell de confianza, generar una clave solo en el entorno del proceso:

```powershell
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$random = New-Object byte[] 48
$rng.GetBytes($random)
$rng.Dispose()
$env:STOCK_SERVICE_JWT_SECRET = [Convert]::ToBase64String($random)
[Array]::Clear($random, 0, $random.Length)
Remove-Variable random, rng
```

No ejecutar `Get-ChildItem Env:` ni imprimir la variable. No habilitar transcripción
de sesión ni logs de headers. Conservar la configuración humana existente.
Los procesos iniciados desde esta consola heredan la variable. Para poder emitir
el token desde la consola original, iniciar Catálogo en una consola hija:

```powershell
# Ejecutar desde catalogo-service, únicamente durante la prueba manual autorizada.
Start-Process powershell.exe -WorkingDirectory (Get-Location).Path `
    -ArgumentList '-NoExit', '-Command', '.\mvnw.cmd spring-boot:run'
```

La clave no aparece en argumentos. No cambia `iniciar_checkpoint_local.bat`.
En la consola original, generar un token de cinco minutos sin emisor HTTP:

```powershell
function ConvertTo-Base64Url([byte[]] $Bytes) {
    [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}
$issued = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$claims = @{
    iss = 'checkpoint-stock-services'
    sub = 'venta-service'
    aud = @('catalogo-stock')
    iat = $issued
    exp = $issued + 300
    scope = 'stock:operar'
}
$header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
$payload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes(($claims | ConvertTo-Json -Compress)))
$unsigned = "$header.$payload"
$keyBytes = [Text.Encoding]::UTF8.GetBytes($env:STOCK_SERVICE_JWT_SECRET)
$hmac = [System.Security.Cryptography.HMACSHA256]::new($keyBytes)
try {
    $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsigned)))
    $stockToken = "$unsigned.$signature"
} finally {
    $hmac.Dispose()
    [Array]::Clear($keyBytes, 0, $keyBytes.Length)
    Remove-Variable keyBytes, hmac
}
# Opcional: pegar en Authorization/Bearer de Postman, sin imprimirlo.
Set-Clipboard -Value $stockToken
```

Usar valores locales no sincronizados de Postman; no exportar el token, headers
ni ejemplos con credenciales. Evitar historial/sincronización del portapapeles.
Al pegar, limpiar portapapeles; al terminar, detener la consola hija y limpiar:

```powershell
Set-Clipboard -Value ''
Remove-Item Env:STOCK_SERVICE_JWT_SECRET
Remove-Variable stockToken, signature, unsigned, header, payload, claims, issued -ErrorAction SilentlyContinue
Remove-Item Function:ConvertTo-Base64Url
```

Eliminar la variable del padre no elimina la copia heredada del proceso hijo.
La rotación de esta clave requiere reiniciar Catálogo y renovar tokens. No se
configura aún el secreto en Ventas.

## Matriz mínima manual — 9091

Usar productos reales y registrar stock inicial/final en Workbench; no introducir
datos mediante SQL en esta etapa. UUID distinto por escenario salvo reintentos.

| Prueba | Resultado esperado |
|---|---|
| Sin token; cabeceras X-Service-Name/X-User-Id falsificadas | 401; sin cambios. |
| JWT humano CLIENTE, VENDEDOR y ADMINISTRADOR | 401; sin cambios. |
| Token firmado con otra clave o payload manipulado | 401. |
| HS384/algoritmo distinto | 401. |
| Token expirado, sin exp/iat, iat futuro o duración >300 s | 401. |
| Otro issuer/audience/sub o claim role | 401. |
| Identidad válida con `scope: "otro"` | 403. |
| Identidad válida y `scope: "stock:operar"` | Acceso al contrato. |
| UUID inválido, versión distinta, JSON malformado/duplicado | 400. |
| Campos precio/consumidor/total extra, decimales o strings numéricos | 400. |
| Lista vacía, 101 ítems, repetidos, ID/cantidad no positivos o fuera de rango | 400; sin intención nueva. |
| Body >16 KiB con/sin Content-Length | 413; sin intención nueva. |
| Content-Type incorrecto o Content-Encoding | 415. |
| Aplicar varios productos válidos | 200/APLICADA, descuento completo y snapshot. |
| Mismo UUID y contenido, incluido array reordenado | 200/APLICADA; no descontar otra vez. |
| Mismo UUID y cantidad/producto diferente | 409/OPERACION_CONTENIDO_DISTINTO. |
| Un producto deshabilitado/inexistente/sin stock | 409/RECHAZADA; ninguno descontado. |
| GET UUID desconocido | 404. |
| GET aplicada/rechazada/cancelada/revertida | 200, estado y snapshot correspondiente. |
| Revertir aplicada, luego repetir | 200/REVERTIDA ambas; una sola restitución. |
| Revertir con contenido distinto | 409; ninguna restitución. |
| Revertir desconocida, luego aplicar misma solicitud | 200/CANCELADA, luego 409; ningún descuento. |
| Revertir rechazada | 200/RECHAZADA; ningún movimiento. |
| Revertir producto deshabilitado tras aplicar | Stock restituido, sigue deshabilitado. |
| Dos aplicaciones simultáneas mismo UUID | Un único descuento. |
| Operaciones distintas compitiendo por últimas unidades | Sin stock negativo. |
| Aplicar/revertir concurrentes | Final cerrado; nunca descuento posterior a CANCELADA. |
| Cortar cliente tras envío, consultar y repetir mismo UUID | Resultado reconciliable, sin doble efecto. |
| Fallo técnico entre registro y aplicación | Consultar PENDIENTE y reintentar mismo UUID. |

El núcleo actual no ofrece una ruta para forzar PENDIENTE ni fallos; reproducir
esos escenarios requerirá interrupción controlada/depuración autorizada.

## Comprobaciones manuales — Gateway 9090

El YAML local y su ejemplo tienen discovery locator deshabilitado. Solo existen
rutas públicas /api/productos/** y administrativas explícitas hacia Catálogo;
ninguna reescribe hacia /internal. Se agregó denyAll para /internal y /internal/**
antes del permiso OPTIONS, y CORS sin orígenes permitidos para ese namespace.

1. Repetir GET y ambos POST internos en 9090, sin token, con cada JWT humano
   y con token de servicio: deben ser 401/403, nunca DTO de stock ni efecto en BD.
2. OPTIONS con Origin http://localhost:5173 y Access-Control-Request-Method POST:
   debe rechazarse, sin permiso CORS para /internal.
3. Probar `/catalogo-service/internal/stock/operaciones/{uuid}`: no hay ruta
   discovery publicada; no debe retornar un resultado de stock.
4. Confirmar que Swagger público de Catálogo no enumera este controlador.
5. Regresión manual de catálogo público, imágenes, RAWG y administración con
   sus permisos anteriores; no se modificaron sus rutas ni autenticación.

## Límites pendientes

Compilar no demuestra ejecución: pendientes las pruebas HTTP, arranque con clave,
aislamiento real de cadenas y matriz concurrente. El usuario ya validó DDL/JPA
de 4.2; no se modificaron. No hay recuperador automático ni cliente Ventas.
HS256 acredita a quien conoce el secreto: protegerlo y no compartirlo con
frontend/Gateway/Identidad. La denegación de Gateway no sustituye la autenticación
de Catálogo ni las restricciones de red. Un token robado puede usarse durante
su vigencia; la idempotencia por UUID no es revocación de credenciales.
