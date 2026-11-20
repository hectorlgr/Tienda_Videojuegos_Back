# RAWG: búsqueda y previsualización administrativa

No hay importación ni escritura de productos. Esta implementación no realizó
peticiones reales a RAWG, SQL ni pruebas HTTP. Los ejemplos son contratos
ilustrativos, no capturas de respuestas ejecutadas.

## Configuración

El servicio lee `RAWG_API_KEY` del entorno (`${RAWG_API_KEY:}`); no se necesita
agregar una clave a application.properties. Si está ausente/vacía, el CRUD
local sigue disponible y las consultas RAWG responden 503 / RAWG_NO_CONFIGURADO.

El cliente dedicado usa Java 21 HttpClient, base fija https://api.rawg.io/api,
conexión máxima de 3 segundos y timeout de solicitud de 10 segundos. No sigue
redirecciones, no recibe Authorization del usuario y no usa el WebClient
LoadBalanced. Envía la clave únicamente en el parámetro key requerido por RAWG.
No registra solicitudes ni excepciones externas ni conserva sus causas.
No activar trazas HTTP de JDK (`jdk.httpclient.HttpClient.log`) al probar con
credenciales reales: podrían imprimir la URL externa.

### Lanzador existente (sin modificaciones)

`iniciar_identidad.ps1` inicia procesos que heredan el entorno. Su lista de
variables que limpia no incluye RAWG_API_KEY. Por tanto, para probar basta con
definirla en la sesión PowerShell antes de invocar el BAT desde ESA sesión.
No es necesario modificar el lanzador ni usar setx.

Entrada oculta, sin escribir el valor literal en el historial:

```powershell
$clave = Read-Host 'RAWG API key' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($clave)
try {
    $env:RAWG_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    $clave.Dispose()
}
try {
    .\iniciar_checkpoint_local.bat
} finally {
    Remove-Item Env:RAWG_API_KEY -ErrorAction SilentlyContinue
}
```

Los procesos ya iniciados conservan su copia del entorno. Con el lanzador
actual todos los procesos hijos heredan la variable, aunque solo Catálogo la
consume. El ajuste mínimo futuro para limitarla a Catálogo sería asignarla
solo alrededor de Start-ServiceWindow 'catalogo-service' y retirarla antes
de iniciar Gateway, manteniéndola ausente al iniciar Eureka e Identidad.
No se ha aplicado ese cambio. No incluir claves en colecciones exportadas.

## Seguridad y enrutamiento

Solo se añadieron dos matchers GET ADMINISTRADOR en cada SecurityConfig:

- /api/productos/rawg/buscar
- /api/productos/rawg/{rawgId}

Se evalúan antes de los GET públicos. La ruta Gateway /api/productos/** hacia
lb://catalogo-service ya cubre ambos; no hicieron falta destinos nuevos.
Sin JWT válido: 401; CLIENTE/VENDEDOR: 403. ADMINISTRADOR pasa la autorización,
pero aún puede recibir errores de validación/configuración/dependencia.
Los permisos del CRUD y los movimientos existentes se conservan.

## Búsqueda

GET `{{baseUrl}}/api/productos/rawg/buscar?query=portal&page=1`

Bearer {{adminToken}}. query obligatorio, no vacío después de strip y máximo
100 caracteres; page entero positivo, por defecto 1. pageSize fijo de 20.
Se envían search, page, page_size y key al GET /games de RAWG.

Respuesta 200 ilustrativa:

```json
{
  "resultados": [
    {
      "rawgId": 4200,
      "nombre": "Portal 2",
      "imagenUrl": null,
      "plataformas": ["PC"],
      "yaImportado": false
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1,
  "haySiguiente": false
}
```

No se exponen next/previous de RAWG. haySiguiente se calcula a partir de count.
Una búsqueda sin coincidencias puede devolver resultados=[] y total=0.
yaImportado se resuelve con una sola consulta por lote a productos.rawg_id,
sin filtrar habilitado. No hay consultas por cada resultado ni escrituras.

## Previsualización

GET `{{baseUrl}}/api/productos/rawg/{{rawgId}}`

rawgId entero positivo. Elegirlo de una búsqueda, no asumir que el ejemplo
sigue disponible. El cliente consulta GET /games/{id} en RAWG.

Respuesta 200 ilustrativa:

```json
{
  "rawgId": 4200,
  "nombre": "Portal 2",
  "descripcion": "Descripción del videojuego.",
  "imagenUrl": null,
  "generos": ["Puzzle"],
  "plataformas": ["PC"],
  "yaImportado": false
}
```

Metadatos opcionales ausentes/null: textos e imagen null, listas vacías.
Se prefiere description_raw cuando existe; en otro caso description.
Ambas pasan por el parser HTML del JDK, que extrae texto y omite contenido
script/style. Se escapan los caracteres HTML residuales para que las entidades
decodificadas no reintroduzcan etiquetas. La descripción no es HTML para renderizar.
Las imágenes solo admiten URLs HTTPS válidas sin userinfo; no se descargan.
No se incluyen ni calculan precio, precioOferta, enOferta, stock o habilitado.

## Errores

Los errores RAWG conservan timestamp/status/error/message/path e incorporan
`code`, sin cuerpo externo, URLs con clave ni trazas:

| HTTP | code | Situación |
| --- | --- | --- |
| 503 | RAWG_NO_CONFIGURADO | Variable ausente/vacía |
| 502 | RAWG_CLAVE_RECHAZADA | RAWG responde 401/403 |
| 503 | RAWG_CUOTA_AGOTADA | RAWG responde 429 (cuota o límite temporal) |
| 504 | RAWG_TIMEOUT | Timeout de conexión/solicitud |
| 502 | RAWG_CONEXION | Fallo de red/TLS/lectura |
| 502 | RAWG_NO_DISPONIBLE | Otro estado externo no exitoso |
| 502 | RAWG_RESPUESTA_INVALIDA | JSON/estructura/ID externo inválido |
| 503 | RAWG_INTERRUPCION | Consulta interrumpida |
| 404 | RAWG_NO_ENCONTRADO | Detalle externo inexistente |
| 400 | RAWG_PAGINA_INEXISTENTE | RAWG devuelve 404 para la búsqueda |

Parámetros inválidos producen 400 mediante el manejo local existente.
Un rechazo de la clave RAWG no produce 401 al cliente: se diferencia del
JWT local inválido. No se hacen reintentos automáticos que consuman más cuota.
La clasificación de cuota se basa en el estado 429; no se deduce de mensajes
remotos ni se garantiza distinguir agotamiento mensual de rate limiting.

## Matriz Postman pendiente

Repetir en http://localhost:9091 y http://localhost:9090:

1. Ambos endpoints sin token, inválido o expirado: 401.
2. Ambos con CLIENTE y VENDEDOR: 403, incluso si falta RAWG_API_KEY.
3. ADMINISTRADOR sin clave configurada y parámetros válidos: 503,
   RAWG_NO_CONFIGURADO. GET público y CRUD local continúan disponibles.
4. Con clave válida, buscar portal; comprobar pageSize=20, DTO acotado,
   IDs, imágenes opcionales, plataformas y ausencia de URLs next/previous.
5. Omitir query, enviar espacios o más de 100 caracteres: 400.
6. page=0, -1, abc o 1.5: 400; omitir page: página 1.
7. Previsualizar un ID de la búsqueda; comprobar géneros, plataformas,
   descripción sin HTML ejecutable y ausencia de campos comerciales.
8. rawgId=0, -1 o abc: 400; ID externo inexistente: 404.
9. Comprobar yaImportado contra registros RAWG locales ya existentes,
   incluyendo ocultos si los hay. No crear productos para esta tarea.
   Si todos tienen rawg_id NULL, el caso true queda pendiente para importación.
10. Clave inválida: 502 / RAWG_CLAVE_RECHAZADA según respuesta externa;
    cuota 429, timeout y caída deben verificarse con condiciones controladas,
    sin agotar deliberadamente la cuota. Revisar que errores/logs no muestren
    el secreto ni URLs con key.
11. Regresión: GET público de productos, JWT del CRUD y movimientos de stock;
    en esta revisión evitar ejecutar operaciones que cambien productos.

No se implementaron importación, índices ni sincronización. Las condiciones
RAWG requieren atribución con enlace donde se muestren sus datos/imágenes;
la futura interfaz deberá incorporarla. Fuentes: https://rawg.io/apidocs y
https://api.rawg.io/docs/. No publicar la clave en capturas de configuración.
