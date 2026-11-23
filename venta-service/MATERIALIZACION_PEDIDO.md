# Materialización local — Etapa 4.6B.2

Actualización 4.6B.3A: registro/hash/recuperación completa de checkout implementados
en [REGISTRO_CHECKOUT.md](REGISTRO_CHECKOUT.md). La política backend autorizada
sigue pendiente; el registro nuevo la exige antes de insertar.

`MaterializacionPedidoService.materializar(Long solicitudId, UUID token, ResultadoStock stock)`
es exclusivamente interno. Devuelve un record `Resultado(CREADO|RECUPERADO, pedidoId)`
después del commit. No devuelve entidades administradas. No hay invocaciones HTTP.

## Precondiciones de confianza

El resultado debe proceder del cliente interno autenticado de Catálogo: el record
Java por sí solo no acredita procedencia. Se exigen HTTP 200, tipo APLICADA,
código OPERACION_APLICADA, operación APLICADA y ambos UUID iguales al original.
Se comparan productos/cantidades incluso cuando el resultado procede de GET.

Checkout, comprador, clave, hash y UUID se cargan exclusivamente de la solicitud.
La estructura tipada valida versiones, aceptación explícita, límites positivos,
longitudes y textos de entrega. El contexto del servidor congelado aporta el
costo de despacho: no se calcula una tarifa ni se consultan políticas vigentes.
El futuro registro debe acreditar aprobación/aplicabilidad de esa política y
condiciones, y calcular/comparar el hash de checkout. El registro actual todavía
crea solicitudes sin checkout; estas se rechazan y no se completan silenciosamente.

## Transacción

1. REQUIRES_NEW, timeout de 10 segundos, bloqueo PESSIMISTIC_WRITE por ID.
2. Validar respuesta y buscar pedido por UNIQUE `operacion_stock_id`.
3. RESUELTA/PEDIDO_CREADO: verificar ID terminal, comprador, clave, hash y todos
   los detalles históricos; devolver el original sin actualizar ni exigir un lease
   nuevo. Otros resultados terminales no se reabren.
4. Para una solicitud abierta, verificar token/vencimiento después de adquirir el
   bloqueo; impedir compensación durable y checkout ausente.
5. Validar composición, entrega y total. Crear CREADO/DESCONTADO mediante persist
   de una entidad nueva con cascade PERSIST de todos sus detalles.
6. Flush de pedido/detalles; `SolicitudPedido.resolverConPedido()` vuelve a validar
   el token, guarda PEDIDO_CREADO/APLICADA/ID y elimina token/vencimiento. Flush.
7. BeforeCommit vuelve a comprobar el vencimiento capturado bajo lock. Si venció
   durante las escrituras, rollback de todo. El bloqueo se conserva hasta commit.

El control temporal se hace al adquirir el lock, al resolver y justo antes del
commit; no es una garantía de tiempo real sobre la latencia física del commit.
Un sucesor no puede cambiar token/decisión mientras esta transacción tenga el lock.
Un worker que retoma con token obsoleto no escribe. Todos los coordinadores futuros
deben usar el mismo bloqueo para decisiones mutuamente excluyentes.

Si excepcionalmente existe pedido con solicitud aún abierta, solo se resuelve
con token vigente y coincidencia de identidad, detalles, importes, indicadores,
entrega y estados CREADO/DESCONTADO. Una discrepancia exige investigación.

No se actualizan pedidos existentes ni se borran detalles. El hash de
`pedidos.hash_solicitud` es **hash_checkout**, no el hash stock-only de la solicitud.
Los detalles conservan SKU, nombre, categoría, formato, plataforma, cantidad,
precio y subtotal históricos; nunca se vuelven a cargar desde productos actuales.

## Validación comercial y SQL 02–04

- Cobertura exacta sin repetidos, faltantes ni productos adicionales.
- Longitudes SKU/nombre/plataforma compatibles con VARCHAR de SQL 02.
- El contrato tipado exige precio/subtotal positivos y plataforma/formato para
  videojuegos. Se recalculan multiplicaciones y suma con operaciones exactas.
- VIDEOJUEGO DIGITAL es digital; VIDEOJUEGO FISICO y CONSOLA/ACCESORIO/FIGURA
  con formato NULL o FISICO requieren despacho. No se infiere digital de NULL.
- CONSOLA/ACCESORIO/FIGURA con DIGITAL se rechazan como snapshot no soportado:
  Catálogo permite técnicamente esa combinación, pero no hay una regla comercial
  aprobada para materializarla. No se cambia su formato silenciosamente.
- Físicos/mixtos requieren entrega completa; digitales puros requieren costo cero
  y guardan todas las columnas de entrega NULL, aunque el checkout incluya dirección.
- Total productos más despacho usa `Math.addExact`, y no supera el límite aceptado.
  No se añade IVA ni se aceptan precios suministrados por el llamador.
- Fechas Instant truncadas a microsegundos, UTC; versiones inicializadas por JPA.
- Resultado terminal y claim nulo cumplen SQL 04; motivo de compensación permanece NULL.
- PK, FK y UNIQUE existentes son la última barrera contra duplicaciones.

## Duplicados y fallos

Las llamadas concurrentes para la misma solicitud se serializan por lock; la
segunda recupera el pedido confirmado, sin reinserción.
Ante DataIntegrityViolationException se termina primero el rollback y se abre
otra transacción para recuperar únicamente un éxito terminal coherente. Si no
existe, se propaga el error de integridad original; no se ocultan CHECK/FK u otra
colisión como éxito. No se continúa usando una transacción fallida.

`MaterializacionPedidoException` distingue:

| Código | Acción del futuro coordinador |
|---|---|
| PERSISTENCIA_TRANSITORIA | Reconciliar/reintentar con la misma solicitud y UUID; reclamar si venció. |
| ENTREGA_FISICA_AUSENTE, DESPACHO_DIGITAL_INVALIDO, TOTAL_NO_ACEPTADO, TOTAL_FUERA_DE_RANGO | Incumplimiento comercial; `requiereCompensacion()` es true. |
| SNAPSHOT_INVALIDO | No confirmar; verificar/reconciliar evidencia, sin asumir rechazo comercial. |
| CHECKOUT_AUSENTE, PEDIDO_INCONSISTENTE | Investigar inconsistencia local. |
| COMPENSACION_PENDIENTE | Continuar la decisión de compensar, nunca crear pedido. |

Solicitud inexistente y token inválido/resolución incompatible conservan las
excepciones existentes. Los fallos técnicos no transitorios y de commit se propagan;
no todo fallo de base de datos es transitorio. Una pérdida de conexión durante
commit puede dejar resultado desconocido: recuperar por la misma identidad.

La señal comercial **no persiste COMPENSACION_PENDIENTE**. Ningún fallo ejecuta
reversión ni libera el claim confirmado previamente en otra transacción.
Si se requiere compensar, el futuro coordinador debe guardar primero una decisión
explícita con lock/token vigente, fuera de la transacción fallida, volviendo a
verificar que nadie haya creado un pedido. Solo después podrá autorizar la
reversión idempotente del UUID original. Rollback local nunca significa restitución.

## Permisos mínimos pendientes

Verificar con administración los permisos reales de `checkpoint_ventas_app` y su
host. Con el SELECT ya documentado:

- INSERT en `checkpoint_store_ventas.pedidos` y `detalle_pedidos`.
- UPDATE en `solicitudes_pedido`; INSERT también lo necesita el registro durable.
- Si se limitan por columnas, el UPDATE de Hibernate sin DynamicUpdate incluye
  `estado`, `version`, `token_reclamacion`, `bloqueo_hasta`, `fecha_actualizacion`,
  `codigo_compensacion`, `resultado`. No basta conceder solo UPDATE(resultado).
- No necesita UPDATE/DELETE sobre pedidos/detalles, ni permisos DDL.

No se ejecutó SQL ni GRANT. SQL 01–04 no cambia; el usuario informó que SQL 04
ya fue aplicado y validado.

## Verificación manual pendiente

La compilación y la inspección estática no validan MySQL en ejecución.
Con permisos y un llamador interno autorizado, comprobar:

1. Físico, digital y mixto: pedido completo, suma exacta y resultado con mismo ID.
2. Repetición y dos trabajadores concurrentes: un solo pedido y conjunto de detalles.
3. Token vencido/obsoleto, también durante flush: ninguna escritura confirmada.
4. Fallo tras insertar pedido o un detalle: rollback de todo; solicitud abierta.
5. Límite superado, falta entrega, overflow de despacho: señal comercial sin decisión durable.
6. Snapshot de otro UUID, cantidades distintas, formato no soportado: rechazo sin escritura.
7. COMPENSACION_PENDIENTE y RESUELTA no exitosa: nunca materializar.
8. Pérdida de respuesta tras commit: recuperar exactamente el ID original.

## Integración pendiente

Registro durable de checkout con canonicalizador/hash y recuperación completa;
aprobación/versionado de condiciones y política de despacho; coordinación con
CatalogoStockClient fuera de transacciones locales; reconciliación usando UUID
original; decisión durable y ejecución idempotente de compensaciones; pruebas
reales de concurrencia, rollback, JSON y permisos. No hay checkout HTTP público.
