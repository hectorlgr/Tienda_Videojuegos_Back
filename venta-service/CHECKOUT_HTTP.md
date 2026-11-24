# Checkout académico HTTP — Etapa 4.7

## Endpoint y autorización

`POST /api/pedidos`

- Gateway local: `http://localhost:9090/api/pedidos`.
- Directo a Ventas: `http://localhost:9094/api/pedidos`.
- JWT humano HS256 válido, **exclusivamente CLIENTE**. Sin JWT válido: 401;
  ADMINISTRADOR/VENDEDOR: 403. Las respuestas de filtros de seguridad pueden
  no tener JSON y no siguen necesariamente el contrato del controlador.
- Comprador tomado de `JwtUserId.getValidId(jwt)` (`userId` autenticado),
  igual que las consultas propias existentes. No se acepta identidad del body.
- Gateway permite únicamente este POST exacto antes del denyAll de pedidos.
  Mantiene las rutas heredadas de escritura bloqueadas y `/internal/**` cerrado.
  La ruta existente `lb://venta-service` ya cubre pedidos; no se añade otra.
- CORS de Gateway admite `Idempotency-Key` para los orígenes locales existentes.
  No se amplían orígenes ni se añade CORS al puerto directo.

## Entrada para Postman

Encabezados:

```text
Authorization: Bearer {{jwt_cliente}}
Content-Type: application/json
Idempotency-Key: 12345678-1234-4234-8234-123456789abc
```

JSON ilustrativo físico/mixto, con ID y datos ficticios. No fue ejecutado:

```json
{
  "items": [
    {"productoId": 101, "cantidad": 1}
  ],
  "entrega": {
    "destinatario": "Cliente de demostración",
    "telefono": "+56900000000",
    "direccion": "Calle Ejemplo 123",
    "region": "Región de ejemplo",
    "comuna": "Comuna de ejemplo",
    "referencia": null
  },
  "limiteTotalClp": 100000,
  "aceptacion": {
    "versionCondiciones": "CHECKPOINT_COMPRA_V1",
    "aceptaPrecioVigenteHastaLimite": true
  }
}
```

Para digital puro puede usarse `entrega: null`; esto NO declara al producto
digital. Catálogo determina su clasificación. No marcar aceptación por defecto:
debe representar la decisión explícita del comprador tras conocer
[las seis condiciones aprobadas](CONDICIONES_COMPRA_V1.md).

`Idempotency-Key` es obligatorio, una sola aparición y UUID canónico con guiones
(se aceptan letras mayúsculas, normalizadas por el servicio existente).
Conservar clave y contenido para todos los reintentos de la misma intención.

### Validaciones

- `application/json`; cuerpo máximo 32768 bytes, incluyendo transferencia chunked.
  Se rechazan compresión/Content-Encoding diferente de identity.
- 1–100 productos únicos, IDs Long positivos y cantidades enteras positivas
  dentro del rango Integer, conforme al contrato existente; no decimales ni strings
  numéricos. Stock insuficiente se rechaza en prelectura o definitivamente en Catálogo.
- Límite Long positivo, aceptación true y versión no vacía; la política compara
  la versión exacta con la configurada para una intención nueva.
- Entrega completa cuando existe: destinatario 255, teléfono 30, dirección 255,
  región/comuna 100; referencia opcional de hasta 500. Sin textos blancos.
  Físico/mixto exige entrega por la política existente.
- JSON con propiedades desconocidas, claves duplicadas o contenido sobrante
  se rechaza. Esto incluye compradorId, precios, costoDespacho, categorías,
  formatos, roles, estados y operacionStockId en cualquier nivel no admitido.

La deserialización estricta está aislada a este endpoint; no cambia los DTO
heredados ni el mapper global. La lectura acotada del body ocurre antes de
invocar al coordinador.

## Contrato de respuesta

Solo devuelve `resultado`, `codigo`, `pedidoId`, `estadoPedido`, `terminal`.
Nunca serializa `SolicitudPedidoInterna`, lease, token, hashes, UUID stock, cuerpos
remotos ni excepciones. `terminal` describe una resolución durable de la intención;
false NO prueba que no haya efectos remotos o que no exista pedido.

| HTTP | Caso | Significado |
|---|---|---|
| 200 | PEDIDO_CREADO, nuevo o recuperado | Pedido efectivamente persistido; ID y estado actual mediante lectura propia. |
| 202 | RECUPERABLE u OCUPADA | `PENDIENTE`, sin éxito comercial. Incluye reconciliación o compensación aún no confirmada. |
| 409 | Contenido diferente para la misma clave, o clave histórica sin checkout | `CONFLICTO_IDEMPOTENCIA`. No reemplazar la intención original. |
| 400 | UUID/header inválido, JSON inválido, campos ajenos, aceptación ausente/false o estructura inválida | Fallo de entrada anterior al coordinador. |
| 413 | Cuerpo mayor de 32 KiB | No se invoca el coordinador. |
| 415 | Content-Type ausente/no JSON o codificación no admitida | No se invoca el coordinador. |
| 422 | Condiciones distintas, falta entrega, despacho mayor al límite, producto no publicado/no disponible | Error de preparación; `terminal:false`, sin afirmar una resolución durable. |
| 422 | RECHAZO_DEFINITIVO, COMPENSACION_CONFIRMADA, CIERRE_PREVENTIVO_CONFIRMADO o RECHAZO_LOCAL persistidos | `COMPRA_NO_COMPLETADA`, `terminal:true`; no hay éxito comercial. |
| 502 | Metadatos de Catálogo no interpretables | Fallo de prelectura; `terminal:false`. |
| 503 | Configuración ausente, Catálogo no disponible durante preparación | `CHECKOUT_NO_DISPONIBLE`. |
| 503 | REVISION_REQUERIDA del coordinador | Necesita revisión interna; no se expone diagnóstico remoto. |
| 503 | Excepción técnica, incluida incertidumbre de commit | `RESULTADO_NO_CONFIRMADO_REINTENTAR_MISMA_CLAVE`; jamás se afirma rechazo definitivo. |
| 401 / 403 | Autenticación / autorización | Filtros de seguridad, anteriores al negocio. |

Se usa **200 para ambos casos de pedido nuevo y recuperado**, porque el coordinador
devuelve el resultado durable sin distinguirlos. Incluye `Location: /api/pedidos/{id}`.
El estado inicial normal es CREADO, no CONFIRMADO: descuento de stock y persistencia
no acreditan pago, despacho ni confirmación comercial adicional.

Ejemplo de éxito:

```json
{
  "resultado": "PEDIDO_CREADO",
  "codigo": "PEDIDO_CREADO",
  "pedidoId": 42,
  "estadoPedido": "CREADO",
  "terminal": true
}
```

Ejemplo pendiente (202, `Retry-After: 5`):

```json
{
  "resultado": "PENDIENTE",
  "codigo": "REINTENTAR_MISMA_CLAVE",
  "pedidoId": null,
  "estadoPedido": null,
  "terminal": false
}
```

Un timeout de stock interpretado por el coordinador produce pendiente; una falla
técnica propagada puede producir 503. Ninguna demuestra ausencia de descuento.
Si Gateway/cliente pierde la respuesta o agota su propio timeout, repetir con
la MISMA clave/cuerpo; el servidor puede haber continuado y confirmado.

## Ejecución y recuperación

Ventas usa Spring MVC servlet. El controlador síncrono llama al coordinador
imperativo en un hilo servlet, nunca bloquea el event loop de Gateway. No tiene
`@Transactional`. La lectura del pedido para responder ocurre después de
finalizar la coordinación/HTTP, en el servicio de consultas read-only existente.

El coordinador sigue siendo la única lógica de compra. Repetir el POST recupera
o continúa la intención original antes de consultar productos actuales. Preserva
despacho, condiciones, hashes y UUID. Los tokens obsoletos y la compensación
durable siguen protegidos por las transiciones de 4.6C.

No hay scheduler ni endpoint administrativo de recuperación. Si el cliente deja
de reintentar, una solicitud pendiente requiere la entrada interna autorizada
`continuar(id)`; revisión requerida puede necesitar intervención. El GET de pedido
solo existe cuando hay un pedido, no sirve para consultar intenciones pendientes.

## Permisos y configuración previos a pruebas

- Cuenta real de Ventas: SELECT en solicitudes_pedido, pedidos y detalle_pedidos;
  INSERT/UPDATE en solicitudes_pedido; INSERT en pedidos y detalle_pedidos.
- Si UPDATE es por columnas: estado, version, token_reclamacion, bloqueo_hasta,
  fecha_actualizacion, codigo_compensacion y resultado.
- Sin DELETE ni DDL; SQL 01–04 ya aplicado, Hibernate validate.
- Condiciones `CHECKPOINT_COMPRA_V1`, discovery accesible, JWT humano compartido
  y JWT de servicios de stock independiente según la configuración existente.
- No se verificaron ni concedieron permisos reales en esta tarea.

## Pasos mínimos de validación posterior — no ejecutados

1. Preparar entorno/datos controlados y permisos, iniciar servicios posteriormente
   y obtener JWT CLIENTE real mediante Identidad.
2. Comprobar sin token (401), VENDEDOR/ADMINISTRADOR (403) tanto en 9094 como 9090.
   Verificar POST/PUT/DELETE heredados e `/internal/**` siguen bloqueados.
3. Con IDs reales: digital puro debe guardar despacho 0; físico y mixto 3990 una
   sola vez y entrega. Revisar total y snapshots, sin IVA adicional.
4. Repetir clave/cuerpo y ejecutar concurrencia: mismo pedido o pendiente, nunca
   otro UUID/descuento. Cambiar cantidad/dirección/límite: 409.
5. Probar false/ausencia de aceptación, versión distinta y campos prohibidos.
6. Validar timeout tras descuento, caída antes de materializar, fallo MySQL y
   compensación conforme a COORDINADOR_PEDIDOS.md. Repetir siempre la misma clave.
7. Verificar que errores no devuelvan trazas, credenciales, tokens ni cuerpos de
   Catálogo. Comprobar GET propio del ID devuelto y aislamiento entre compradores.

Solo compilación Java 21 e inspección estática realizadas. No se hicieron HTTP,
SQL, compras de prueba, descuentos ni reversiones.
