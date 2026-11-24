# Etapa 3.2 — consultas de pedidos

Implementación exclusivamente de lectura sobre `checkpoint_store_ventas`.
No requiere SQL, datos de ejemplo ni cambios de permisos MySQL. Conserva las
cuatro tablas, las entidades y el bloqueo de escrituras de `/api/ventas`.

## Endpoints y autorización

Usar el mismo path en `http://127.0.0.1:9094` (Ventas directo) y
`http://127.0.0.1:9090` (Gateway).

| Método y ruta | CLIENTE | ADMINISTRADOR | VENDEDOR |
| --- | --- | --- | --- |
| GET /api/pedidos/mis-pedidos | Solo propios | 403 | 403 |
| GET /api/pedidos/{id} | Solo propio; ajeno/inexistente 404 | Cualquier pedido; inexistente 404 | 403 |
| GET /api/admin/pedidos | 403 | Todos los compradores y estados | 403 |
| GET /api/admin/pedidos/{id} | 403 | Cualquier pedido; inexistente 404 | 403 |

Sin JWT o con Bearer inválido/expirado: 401. Se aplica la tolerancia temporal
estándar de Spring Security. Los tokens conservan el formato de Identidad:
HS256, secreto externo compartido, role reconocido, exp y userId numérico válido.

El comprador se obtiene mediante `@AuthenticationPrincipal Jwt` y `JwtUserId`,
que comparte exactamente la conversión usada por el validador local. No hay
parámetro de comprador en estos endpoints. Un query param `usuarioId` o
`compradorId` no se enlaza a ningún argumento y no altera el filtro. X-User-Id
y X-Role tampoco conceden identidad o permisos. ADMINISTRADOR tiene su listado
global separado: no se habilita un historial propio para personal en esta etapa.

El checkout `POST /api/pedidos` requiere CLIENTE y sigue el [contrato de compra](CHECKOUT_HTTP.md). Las demás escrituras en ambos namespaces se deniegan.
También se deniegan `/api/pedidos/usuario/{id}`, las rutas heredadas de
estado/cancelación y el GET raíz `/api/pedidos`. OPTIONS solo permite preflight,
no ejecuta operaciones de negocio; HEAD no se habilita como alias de GET.

## DTO y representación

`PedidoResponseDto` contiene:

- id, fechaCreacion (Instant como texto ISO-8601 UTC), estado.
- compradorId **solo para ADMINISTRADOR**, también si usa el detalle común.
- moneda=CLP, totalProductos, costoDespacho y totalFinal: números enteros Long.
- requiereDespacho, contieneDigitales.
- entrega histórica o null para pedidos sin despacho.
- detalles históricos ordenados por ID del detalle.

`DetallePedidoResponseDto`: productoId, sku, nombre, categoria, formato,
plataforma, cantidad, precioUnitario y subtotal.

`EntregaPedidoResponseDto`: destinatario, telefono, direccion, region, comuna,
referencia. No devuelve el objeto embebido JPA.

No se exponen hashSolicitud, claveIdempotencia, operacionStockId, estadoStock,
version, relaciones JPA ni un enlace al comprador. No hay enlaces de escritura.

Las colecciones siguen HATEOAS, con relación explícita `pedidos` incluso vacías:

```json
{
  "_embedded": {"pedidos": []},
  "_links": {
    "self": {"href": "http://127.0.0.1:9090/api/pedidos/mis-pedidos"}
  }
}
```

Ejemplo ilustrativo de detalle para CLIENTE (no se insertaron estos datos):

```json
{
  "id": 101,
  "fechaCreacion": "2026-11-22T15:30:00Z",
  "estado": "CONFIRMADO",
  "moneda": "CLP",
  "totalProductos": 19990,
  "costoDespacho": 0,
  "totalFinal": 19990,
  "requiereDespacho": false,
  "contieneDigitales": true,
  "entrega": null,
  "detalles": [
    {
      "productoId": 42,
      "sku": "JUEGO-PC-DIGITAL",
      "nombre": "Juego de ejemplo",
      "categoria": "VIDEOJUEGO",
      "formato": "DIGITAL",
      "plataforma": "PC",
      "cantidad": 1,
      "precioUnitario": 19990,
      "subtotal": 19990
    }
  ],
  "_links": {
    "self": {"href": "http://127.0.0.1:9090/api/pedidos/101"},
    "mis-pedidos": {"href": "http://127.0.0.1:9090/api/pedidos/mis-pedidos"}
  }
}
```

La representación administrativa añade compradorId y usa self en
`/api/admin/pedidos/{id}` y `todos-los-pedidos` en `/api/admin/pedidos`.
Esto también ocurre al consultar el detalle común con un JWT administrativo.
El host/puerto de enlaces depende del acceso directo o cabeceras forwarded del
Gateway. Los 404 usan el handler existente; los 401/403 de filtros no garantizan JSON.

## Persistencia y aislamiento

`PedidoRepository` extiende `Repository`, exponiendo únicamente cuatro consultas:

- `buscarPorComprador`: filtro compradorId y orden fechaCreacion DESC, id DESC.
- `buscarPropioPorId`: filtro simultáneo id + compradorId; no carga primero
  un pedido ajeno para comprobarlo después.
- `buscarTodos`: todos los estados, orden fechaCreacion DESC, id DESC.
- `buscarPorId`: consulta administrativa por ID.

Todas usan `select distinct p ... left join fetch p.detalles`. Cada operación
de repositorio obtiene cabecera, entrega embebida y detalles en una consulta,
sin N+1. LEFT JOIN no oculta una cabecera sin detalles. `PedidoConsultaService`
es transaccional readOnly y transforma entidades a DTO antes de salir de la
transacción: la serialización y el assembler no acceden a proxies JPA.
No hay llamadas a Identidad, Catálogo o servicios heredados.

Las listas son completas, sin paginación en este contrato. Para volúmenes grandes
deberá incorporarse paginación con carga de detalles por lote; no añadir Pageable
directamente a un fetch join de colección, que puede paginar en memoria.

## Enrutamiento de pedidos

`PedidoRoutesConfig` dirige `/api/pedidos` y `/api/admin/pedidos`, incluidos sus subpaths, a `lb://venta-service` sin reescritura. Swagger publica las operaciones en `/api/ventas/v3/api-docs`.

Gateway y Ventas autorizan el checkout y las cuatro lecturas anteriores. Las demás operaciones quedan denegadas.
La ruta literal mis-pedidos se evalúa antes de {id}; no obtiene permisos de
administrador por coincidir con el patrón de detalle.

## Matriz manual mínima de Postman

Ejecutar cada fila por ambos puertos con tokens existentes vigentes.
No iniciar servicios ni ejecutar estas solicitudes como parte de la implementación.

| Solicitud | Sin token / firma alterada | CLIENTE | ADMINISTRADOR | VENDEDOR |
| --- | --- | --- | --- | --- |
| GET /api/pedidos/mis-pedidos | 401 | 200 vacío | 403 | 403 |
| GET /api/pedidos/1 | 401 | 404 | 404 | 403 |
| GET /api/admin/pedidos | 401 | 403 | 200 vacío | 403 |
| GET /api/admin/pedidos/1 | 401 | 403 | 404 | 403 |
| GET /api/pedidos/usuario/1 | 401 | 403 | 403 | 403 |
| POST /api/pedidos | 401 | Según solicitud e idempotencia ([checkout](CHECKOUT_HTTP.md)) | 403 | 403 |
| PUT /api/pedidos/1/estado?nuevoEstado=COMPLETADO | 401 | 403 | 403 | 403 |
| PUT /api/pedidos/1/cancelar | 401 | 403 | 403 | 403 |
| POST /api/admin/pedidos | 401 | 403 | 403 | 403 |

También comprobar:

1. PUT, PATCH y DELETE sobre `/api/pedidos/1` y `/api/admin/pedidos/1`,
   con ADMINISTRADOR: 403. Usar `{}` como body JSON cuando corresponda.
2. GET de mis-pedidos con token CLIENTE expirado más allá de la tolerancia:
   401 en ambos puertos.
3. GET de mis-pedidos con `?usuarioId=999&compradorId=999` y headers
   X-User-Id=999, X-Role=ADMINISTRADOR: sigue siendo el historial del JWT.
   Sin token y con esos headers: 401; VENDEDOR con esos headers: 403.
4. Por regresión del orden de reglas modificado: POST /api/ventas con
   ADMINISTRADOR y body `{}` sigue en 403; GET /api/ventas sigue reservado a ADMINISTRADOR.
5. Verificar las lecturas por Gateway y por el puerto directo de Ventas con la misma cuenta.

### Pendientes que una base vacía no demuestra

No crear semillas ni insertar datos en esta etapa. Cuando existan compras
legítimas de dos clientes, verificar:

- Cada JWT ve solo su historial; un ID ajeno y uno inexistente devuelven el
  mismo 404 en el detalle común.
- ADMINISTRADOR ve ambas compras, compradorId y todos los estados.
- Orden descendente por fecha y desempate por ID.
- Detalles/entrega históricos, omisión de compradorId para CLIENTE y ausencia
  de datos técnicos; enlaces sin acciones o listados no autorizados.
- Serialización completa sin consultas adicionales fuera del servicio.

Compilar y revisar código/JPQL no demuestra funcionamiento real de rutas,
SQL generado, HATEOAS ni aislamiento con datos. La validación HTTP queda pendiente.
