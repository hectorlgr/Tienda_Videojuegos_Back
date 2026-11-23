# Solicitudes durables — Etapa 4.6A.2

Solo servicio interno de persistencia. Sin endpoints, tareas programadas, pedidos
comerciales, resolución ni invocaciones a CatalogoStockClient.
SQL 03 permanece intacto y ya fue validado manualmente por el usuario.
Para ejecutar escrituras falta el GRANT mínimo de `ARRANQUE_LOCAL.md`, sección 1.2.

## API interna

Bean `SolicitudPedidoService`, respuesta inmutable `SolicitudPedidoInterna`.
Nunca devuelve una entidad JPA administrada.

| Método | Comportamiento |
|---|---|
| `registrar(Long compradorId, String claveIdempotencia, List<ContratoStock.Item> items)` | Confirma intención nueva o recupera la existente con contenido idéntico. |
| `buscar(Long compradorId, String claveIdempotencia)` | Optional; recupera JSON, hash, UUID de stock, estado, lease y versión originales. |
| `buscarPendientes(int limite)` | PENDIENTE y RECONCILIACION_PENDIENTE, incluso reclamadas. |
| `buscarDisponibles(int limite)` | Los mismos estados, sin lease o con vencimiento alcanzado. |
| `reclamar(Long id, Duration duracion)` | Bloquea fila, comprueba disponibilidad y asigna un token nuevo. |
| `renovar(Long id, UUID token, Duration duracion)` | Solo token coincidente y aún vigente; nunca acorta el vencimiento. |
| `marcarReconciliacion(Long id, UUID token)` | Solo token vigente; conserva el lease y persiste RECONCILIACION_PENDIENTE. |
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

Al recuperar un lease vencido se marca conservadoramente RECONCILIACION_PENDIENTE:
el dueño anterior podría haber comenzado trabajo. El UUID de stock no cambia.
Renovar, liberar o marcar reconciliación con token obsoleto/expirado falla aunque
todavía nadie haya reclamado de nuevo. El dueño debe reclamar y obtener otro token.
No se reinicia RECONCILIACION_PENDIENTE a PENDIENTE ni se marca RESUELTA.

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

No se añade traducción HTTP: aún no existe endpoint para este servicio.
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
