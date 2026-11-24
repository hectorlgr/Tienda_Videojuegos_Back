# Solicitudes durables — Etapa 4.6B.1

Actualización 4.6C: [COORDINADOR_PEDIDOS.md](COORDINADOR_PEDIDOS.md) describe el
coordinador, las autorizaciones locales y las transiciones de compensación/cierre
ya implementadas. Las referencias posteriores a esas funciones futuras son históricas.

Actualización 4.6B.3A: `registrarConCheckout`, canonicalización/hash comercial y
recuperación completa implementados. Ver [REGISTRO_CHECKOUT.md](REGISTRO_CHECKOUT.md)
para el contrato vigente y el rechazo explícito cuando falta política backend.
Las menciones posteriores al registro/hash pendientes son historia de 4.6B.1.

Actualización 4.6B.2: materialización y resolución exitosa disponibles en
[MATERIALIZACION_PEDIDO.md](MATERIALIZACION_PEDIDO.md). Las referencias siguientes
al futuro materializador describen la situación original de 4.6B.1.

Solo servicio interno de persistencia. Sin endpoints, tareas programadas, pedidos
comerciales, resolución ni invocaciones a CatalogoStockClient.
SQL 03 permanece intacto y ya fue validado manualmente por el usuario.
SQL 04 amplía solo esta tabla; está preparado para ejecución manual, no ejecutado.
Para ejecutar escrituras falta el GRANT mínimo de `ARRANQUE_LOCAL.md`, sección 1.2.

## API interna

Bean `SolicitudPedidoService`, respuesta inmutable `SolicitudPedidoInterna`.
Nunca devuelve una entidad JPA administrada.

| Método | Comportamiento |
|---|---|
| `registrar(Long compradorId, String claveIdempotencia, List<ContratoStock.Item> items)` | Confirma intención nueva o recupera la existente con contenido idéntico. |
| `buscar(Long compradorId, String claveIdempotencia)` | Optional; recupera JSON, hash, UUID de stock, estado, lease y versión originales. |
| `buscarPendientes(int limite)` | PENDIENTE, RECONCILIACION_PENDIENTE y COMPENSACION_PENDIENTE, incluso reclamadas. |
| `buscarDisponibles(int limite)` | Los mismos estados, sin lease o con vencimiento alcanzado. |
| `reclamar(Long id, Duration duracion)` | Bloquea fila, comprueba disponibilidad y asigna un token nuevo. |
| `renovar(Long id, UUID token, Duration duracion)` | Solo token coincidente y aún vigente; nunca acorta el vencimiento. |
| `marcarReconciliacion(Long id, UUID token)` | Solo token vigente; conserva el lease y persiste RECONCILIACION_PENDIENTE, salvo que ya exista decisión de compensar, que conserva. |
| `liberar(Long id, UUID token)` | Solo token vigente; limpia token/vencimiento y conserva estado. |

Los listados tienen límite 1..100, ordenados por fecha de actualización e ID.
No reclaman trabajo ni garantizan que una fila siga disponible cuando se procese.
Los leases duran entre 1 y 300 segundos. No se reciben tiempos absolutos del cliente.
El servicio consulta UTC después de obtener el lock; vencimiento igual a ahora
ya se considera vencido. Los instantes se truncan a microsegundos.

El comprador debe proceder del JWT validado en el futuro adaptador. La comprobación
de ID positivo no autentica al comprador. Ningún parámetro se obtiene de body,
headers o SecurityContext en este servicio de persistencia.
IDs de solicitud/tokens son coordinación interna, no credenciales de autorización.

## Validación y canonicalización v1

- UUID completo con guiones; admite mayúsculas y normaliza a minúsculas.
- Comprador, producto y cantidad positivos.
- Solicitud de 1..100 productos; sin nulls ni IDs repetidos.
- Reutiliza `ContratoStock.Solicitud` y `Item` para validación de contenido.
- Ordena productos por ID ascendente, sin agrupar ni sumar duplicados.
- SHA-256 en hexadecimal minúscula sobre UTF-8 de este formato exacto, sin espacios:

```json
{"versionContrato":1,"items":[{"productoId":12,"cantidad":2},{"productoId":35,"cantidad":1}]}
```

El hash incorpora versión, IDs y cantidades; comprador/clave determinan el espacio
de idempotencia mediante UNIQUE. El JSON almacenado puede cambiar de formato
textual en MySQL: no calcular el hash sobre JSON_PRETTY o el texto devuelto por BD.
La comparación comprueba tanto hash como igualdad estructural de la solicitud.

El JSON no contiene precios, identidad del comprador ni datos comerciales.
No modificarlo cuando llegue un snapshot. No incorporar posteriormente entrega
o aceptación de precios sin versionar y diseñar el contrato correspondiente.

## Transacciones e inserciones concurrentes

Los TransactionTemplate usan REQUIRES_NEW, incluidas las lecturas. Cada retorno
ocurre después del commit de su callback; no depende de autoinvocación de proxies.
No hay llamadas remotas ni transacciones largas de coordinación.

1. Consulta rápida por comprador/clave, para recuperar existentes sin crear UUID.
2. En transacción de escritura, segunda consulta y luego saveAndFlush de una
   entidad nueva, con UUID de stock generado únicamente para ese intento de inserción.
3. La PK/UNIQUE de MySQL decide la carrera real, no las consultas previas.
4. Si falla la inserción por integridad, el callback sale y termina el rollback.
5. Una transacción nueva consulta al ganador; mismo contenido devuelve su UUID,
   distinto contenido lanza CONTENIDO_DISTINTO.
6. Sin ganador para comprador/clave se propaga la excepción original. No se oculta
   una colisión de UUID de stock u otro CHECK, ni se regenera automáticamente.

IDENTITY puede insertar durante save, por eso se usa el repositorio Spring para
traducir la excepción desde ese punto, no solo desde flush.
Dos contendientes pueden generar UUID candidatos en memoria; solo el ganador
se confirma. El perdedor nunca devuelve/utiliza su candidato ni sustituye el
UUID existente. Un reintento de registro con fila ya existente no genera otro.
Una pérdida de conexión al commit exige volver a buscar por comprador/clave:
no implica que la intención no exista.

No envolver estas llamadas en transacciones externas que ya tengan locks sobre
las mismas filas: REQUIRES_NEW suspende la transacción exterior, no libera sus locks.

## Reclamaciones y protección de escritores obsoletos

Todas las mutaciones pasan por SELECT ... FOR UPDATE de la solicitud.
Después del lock se comprueban estado, token y vencimiento. Una segunda reclamación
vigente recibe conflicto; al expirar puede adquirirse con token nuevo.
Hibernate hace dirty checking, flush e incremento de @Version cuando hay cambios.
No se utilizan updates bulk que eviten el versionado.

Al recuperar un lease vencido se marca conservadoramente RECONCILIACION_PENDIENTE,
excepto si ya está en COMPENSACION_PENDIENTE, que se conserva:
el dueño anterior podría haber comenzado trabajo. El UUID de stock no cambia.
Renovar, liberar o marcar reconciliación con token obsoleto/expirado falla aunque
todavía nadie haya reclamado de nuevo. El dueño debe reclamar y obtener otro token.
No se reinicia RECONCILIACION_PENDIENTE a PENDIENTE ni se marca RESUELTA.

## Ampliación de persistencia 4.6B.1

Se añaden `checkout JSON`, `hash_checkout VARCHAR(64)`, `codigo_compensacion
VARCHAR(64)` y `resultado JSON`, inicialmente SQL NULL. UUID, solicitud de stock,
hash original, índices, fechas y @Version se conservan.

`DatosCheckoutSolicitud` define records inmutables tipados para los JSON.
Checkout y hash se reciben juntos únicamente en el nuevo constructor de la entidad,
sin setters y con `updatable=false`. El constructor anterior y el servicio de
registro de 4.6A.2 siguen funcionando, pero producen intenciones SIN checkout.
No hay actualización silenciosa de solicitudes antiguas.
Los campos motivo/resultado están mapeados, sin métodos que decidan compensación,
resuelvan, materialicen pedidos o invoquen Catálogo.

### Checkout versión 1

Estructura ilustrativa de un checkout exclusivamente digital. Los identificadores
de condiciones/política deberán proceder de condiciones realmente aprobadas; este
ejemplo no establece una tarifa ni habilita compras:

```json
{
  "versionContrato": 1,
  "entrada": {
    "entrega": null,
    "limiteTotalClp": 50000,
    "aceptacion": {
      "versionCondiciones": "IDENTIFICADOR_CONDICIONES_APROBADAS",
      "aceptaPrecioVigenteHastaLimite": true
    }
  },
  "contextoServidor": {
    "politicaDespacho": "IDENTIFICADOR_POLITICA_APROBADA",
    "versionPoliticaDespacho": 1,
    "costoDespacho": 0,
    "versionContratoComercial": 1
  }
}
```

- `entrada` procede del comprador, excepto su identidad (JWT validado, no JSON).
- `entrega`: null o objeto con destinatario (255), telefono (30), direccion (255),
  region (100), comuna (100), referencia (500 o null). Todos los nombres de campos
  son obligatorios en el objeto; textos no vacíos en Java. No se deriva la
  composición física/digital de la presencia de una dirección.
- `limiteTotalClp`: Long positivo, máximo total aceptado INCLUIDO despacho.
- `aceptacion`: versión identificable de condiciones y consentimiento explícito
  para precio vigente hasta el límite. No se infiere consentimiento si falta.
- `contextoServidor`: exclusivamente servidor; política identificada/versionada,
  costo de despacho Long no negativo y versión comercial 1, sin valores por defecto.
- El cero del ejemplo corresponde a un caso digital sin despacho; no define
  gratuidad para productos físicos. No existe todavía cálculo de tarifas.
- Sin precios unitarios ni totales autorizados desde React: los precios efectivos
  se tomarán exclusivamente del snapshot de Catálogo.

El futuro coordinador deberá comprobar entrega física/digital, existencia y
aplicabilidad de la política, límites y vigencia comercial. La estructura JSON
no certifica esos hechos. Las condiciones del servidor quedan congeladas:
un reintento debe cargar las persistidas, no reemplazarlas por la política actual.

### Dos hashes independientes

`hash_solicitud` conserva EXACTAMENTE el algoritmo de stock v1 descrito arriba.
No se cambia ni se recalcula para los registros existentes.

`hash_checkout` será SHA-256 hexadecimal minúscula del siguiente sobre canónico:

```text
{
  "versionContratoCheckout": 1,
  "solicitud": <solicitud de stock original, productos ordenados por ID>,
  "entrada": <checkout.entrada completo>
}
```

Regla del futuro canonicalizador: claves de objetos ordenadas lexicográficamente,
JSON sin espacios, números enteros decimales, booleanos/null literales y UTF-8;
strings preservados (sin plegar mayúsculas, acentos o espacios), escapando comillas,
barra inversa y controles JSON; controles U+0000..001F mediante `\u00xx` minúsculo.
Los arrays de productos se ordenan por ID antes de serializar. Sin escapes
opcionales para `/` ni caracteres Unicode no de control.
Esto detecta productos/cantidades, dirección, referencia, límite y aceptación.

Se EXCLUYEN contextoServidor, token, vencimiento, fechas y resultado. Cambiar la
política vigente del servidor no convierte el mismo reintento en un conflicto:
se compara la entrada original y se reutiliza su contexto persistido.
No calcular el hash desde la representación textual de MySQL.
Esta etapa define el formato, pero no implementa cálculo/comparación de
hash_checkout ni amplía registrar para recibir checkout. Antes de habilitarlo
deberán incorporarse ambos al servicio y su vista interna de recuperación.

### Compensación irreversible

COMPENSACION_PENDIENTE significa que esta intención ya no puede crear un pedido;
solo corresponde reconciliar/revertir/cerrar la operación con el UUID original.
Requiere codigo_compensacion con formato `[A-Z][A-Z0-9_]{0,63}` y resultado NULL.
Motivos deben ser códigos estables, no excepciones ni texto técnico libre.

Se protegen todas las transiciones existentes:
- reclamar un lease vencido NO reemplaza COMPENSACION_PENDIENTE;
- marcarReconciliacion tampoco la reemplaza;
- renovar/liberar conservan el estado y el motivo;
- RESUELTA no admite nuevas reclamaciones ni retorno a pendiente.

La única corrección de SolicitudPedidoService es incluir compensaciones entre
estados abiertos, para que no desaparezcan de los listados recuperables.
Todavía no se implementa la transición que decide compensar ni su ejecución.
Los CHECK verifican coherencia de la fila, no historia entre UPDATEs; código SQL
manual podría alterar estados y queda fuera de estas garantías de modelo.

### Resultado terminal versión 1

Campos obligatorios: versionContrato=1, tipo, codigo, estadoRemoto, pedidoId.
Código con formato `[A-Z][A-Z0-9_]{0,63}`. Los campos nulos se escriben como JSON
null explícito, no se omiten. Resultado ausente es SQL NULL, no un JSON null.

| tipo | estadoRemoto | pedidoId |
|---|---|---|
| PEDIDO_CREADO | APLICADA | Long positivo |
| RECHAZO_DEFINITIVO | RECHAZADA | null |
| COMPENSACION_CONFIRMADA | REVERTIDA | null |
| CIERRE_PREVENTIVO_CONFIRMADO | CANCELADA | null |
| RECHAZO_LOCAL | null | null |

Ejemplo estructural (no acredita que se haya realizado una reversión):

```json
{
  "versionContrato": 1,
  "tipo": "COMPENSACION_CONFIRMADA",
  "codigo": "IMPORTE_NO_ACEPTADO",
  "estadoRemoto": "REVERTIDA",
  "pedidoId": null
}
```

RESUELTA exige resultado y reclamación nula. Todos los otros estados exigen
resultado NULL. Compensación/cierre confirmados requieren conservar motivo;
PEDIDO_CREADO y RECHAZO_LOCAL no permiten motivo de compensación.
RECHAZO_DEFINITIVO exige motivo de compensación NULL, conforme al SQL 04 aplicado.
PEDIDO_CREADO exige también checkout/hash presentes.
Sin FK JSON a pedidos: el futuro coordinador deberá verificar pedido existente y
persistirlo junto con la resolución en la misma transacción local.
RECHAZO_LOCAL solo podrá usarse si se demuestra que no hubo intento remoto
autorizado; un timeout no es un rechazo local ni definitivo.

### Registros antiguos y barrera de descuento

PENDIENTE/RECONCILIACION_PENDIENTE antiguos admiten checkout/hash/resultado NULL.
No se rellenan ni se cambia su hash de stock.
`SolicitudPedido.exigirCheckoutParaDescuento()` rechaza ausencia de checkout/hash,
decisión de compensación o RESUELTA. Debe ser precondición obligatoria del futuro
coordinador, junto con token vigente y validación comercial, antes de autorizar HTTP.

Actualmente no hay ningún flujo que invoque Catálogo desde solicitudes. Este
guard no puede impedir por sí solo que código futuro llame directamente al cliente:
la integración deberá pasar por él y probarlo explícitamente antes de habilitarse.
No habilitar el antiguo registrar de productos como checkout público.

SQL 04 exige resultado para toda fila RESUELTA. Como el código previo no resuelve
solicitudes, no se esperan esas filas, pero no se presupone que no existan:
si alguien las creó manualmente, el ALTER fallará. Detenerse, investigar y aprobar
una migración explícita; nunca fabricar resultados ni relajar el CHECK.
SQL 04 es un único ALTER, sin DML ni GRANT. Los registros abiertos conservan sus
cuatro nuevos campos nulos. Ver consultas y pasos en ARRANQUE_LOCAL.md, sección 1.3.

Antes de cualquier futura llamada remota, el coordinador deberá persistir
marcarReconciliacion y confirmar, y solo después iniciar HTTP. Así un cierre
voluntario del lease no pierde la necesidad de recuperar un resultado incierto.

Los locks solo cubren la modificación local. No detienen a trabajadores pausados
fuera de la BD. Conservar idempotencia remota por UUID y validar el token al volver.
Los relojes de instancias deben estar sincronizados: el lease usa Instant UTC de
la JVM, no el reloj central de MySQL. La actualización persistida no retrocede
si el reloj local retrocede; eso no sustituye la sincronización de relojes.

## Errores

- Entradas inválidas: IllegalArgumentException o NullPointerException del contrato
  inmutable cuando falta un valor obligatorio, antes de insertar.
- ID local inexistente al mutar: ResourceNotFoundException.
- `SolicitudPedidoConflictException` con código:
  - CONTENIDO_DISTINTO
  - RECLAMACION_VIGENTE
  - TOKEN_NO_VIGENTE
  - SOLICITUD_RESUELTA
- Errores técnicos, deadlock, timeout de BD u optimismo fallido: propagar y rollback.
  Sin reintento automático ni traducción a fallo de stock.

El servicio de persistencia es interno. El contrato HTTP del checkout se documenta en [CHECKOUT_HTTP.md](CHECKOUT_HTTP.md).
El DTO de recuperación conserva JSON y UUID para futura reconciliación, no
información de una aplicación remota que esta etapa no ejecuta.

## Validación manual futura

No se ejecutaron estos pasos. Después del GRANT mínimo y con un llamador interno
Java autorizado (no un endpoint público añadido para pruebas):

1. Registrar una solicitud válida; comprobar fila PENDIENTE, token/vencimiento null,
   versión inicial 0, hash de 64 caracteres, JSON ordenado y UUID estable.
2. Repetir con array reordenado y clave en mayúsculas: mismo ID y UUID, sin nueva fila.
3. Reutilizar clave/comprador con cantidad distinta: CONTENIDO_DISTINTO.
4. Usar misma clave con otro comprador: solicitud independiente.
5. Registrar simultáneamente mismo contenido/clave: una fila confirmada, ambos
   resultados con ID y UUID ganadores. Con contenidos distintos, un ganador/conflicto.
6. Reiniciar y buscar por comprador/clave: recuperar JSON/UUID originales.
7. Reclamar simultáneamente una fila: un éxito y RECLAMACION_VIGENTE.
8. Renovar con token vigente; comprobar vencimiento no decreciente y versión.
9. Con token ajeno o expirado, liberar/renovar/marcar reconciliación debe fallar.
10. Esperar vencimiento y reclamar de nuevo: nuevo token, mismo UUID de stock,
    RECONCILIACION_PENDIENTE; el token anterior ya no puede modificar la fila.
11. Marcar reconciliación y liberar con token vigente: estado conservado, ambos
    campos del lease null; aparece en buscarDisponibles.
12. Verificar que no se insertan pedidos ni detalles y no hay cambios en Catálogo.

Usar las consultas Workbench de ARRANQUE_LOCAL.md para inspección de filas e índices.
No probar invocando CatalogoStockClient: la coordinación remota y la resolución
son etapas futuras. Compilación e inspección no prueban carreras reales en MySQL.
