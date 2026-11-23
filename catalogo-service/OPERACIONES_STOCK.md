# Operaciones internas de stock — Núcleo Etapa 4.2

La API autenticada de Etapa 4.3 está documentada en
[`API_INTERNA_STOCK.md`](API_INTERNA_STOCK.md). Este documento describe el núcleo.

## Alcance y preparación

Núcleo Java/JPA independiente del adaptador HTTP de Etapa 4.3.
No modifica las operaciones administrativas, Producto, RAWG ni imágenes.

Script completo: `sql/07_crear_operaciones_stock.sql`. Es manual, aditivo y para
MySQL 8.4. El usuario confirmó su aplicación y validación con Hibernate al cerrar
4.2; no volver a ejecutarlo. Se conserva `spring.jpa.hibernate.ddl-auto=validate`.
El DDL tiene commit implícito: ante errores, detenerse e inspeccionar.

## API interna

Bean: `OperacionStockService`. Valores inmutables: `DatosOperacionStock`.

- `registrar(id, consumidor, solicitud)`: registra PENDIENTE o devuelve el registro
  existente si coincide. No aplica stock.
- `aplicar(id, consumidor)`: aplica una operación registrada. Si ya es definitiva,
   devuelve su estado sin modificarla; el adaptador HTTP traduce
  RECHAZADA/CANCELADA/REVERTIDA al contrato correspondiente.
- `consultar(id, consumidor)`: `Optional.empty()` significa inexistente, no cancelada.
- `revertir(id, consumidor, solicitud)`: restituye desde el snapshot aplicado o
  registra/cierra CANCELADA preventivamente.

El único consumidor permitido es `ConsumidorStock.VENTA_SERVICE`. El núcleo no
autentica: el adaptador de 4.3 lo deriva del principal de servicio validado,
nunca del body, JWT humano ni cabeceras de identidad arbitrarias.
No serializar este argumento como campo del contrato HTTP.

La solicitud es `Solicitud(1, List<Item>)`, con 1..100 productos, IDs Long positivos
y cantidades Integer positivas. No admite repetidos. UUID acepta mayúsculas y se
normaliza a minúsculas. Los productos se ordenan por ID.

El SHA-256 usa UTF-8 del JSON canónico v1, sin espacios:

```json
{"versionContrato":1,"items":[{"productoId":12,"cantidad":2},{"productoId":35,"cantidad":1}]}
```

MySQL puede reformatear el JSON almacenado: no calcular el hash a partir de su
representación textual. La canonización es independiente del mapper JSON.
La comparación idempotente comprueba hash, contenido estructurado y consumidor.

## Transacciones y carreras

Se utilizan `TransactionTemplate` privados, configurados una sola vez con
`PROPAGATION_REQUIRES_NEW`. Cada callback finaliza/confirmará antes de devolver.
No depende de proxies `@Transactional` ni de llamadas internas interceptadas.
Incluso si hay una transacción externa, queda suspendida.

No llamar este núcleo desde una transacción externa que ya bloquee productos u
operaciones: la suspensión no libera sus locks. El futuro coordinador HTTP debe
invocarlo sin esos bloqueos.

Registro A: consulta rápida, `EntityManager.persist` y flush (nunca merge).
Dos inserciones concurrentes compiten por la PK. El perdedor recibe una excepción
de integridad y sale de su transacción fallida antes de consultar el ganador en
otra transacción. Si no hay ganador visible, propaga el error original.
Deadlocks/timeouts no se convierten en éxito: el llamador reintenta el mismo ID.

Aplicación B: bloquea operación, luego productos individualmente en ID ascendente.
Valida todos los productos antes de modificar alguno. Guarda stock y snapshot
en el mismo commit. No llama a métodos administrativos por producto.

Reversión: intenta registrar CANCELADA si no existe; si encuentra una operación,
la bloquea y verifica nuevamente el contenido. Si aplicación gana el lock, la
reversión restituye; si cancelación gana, la aplicación posterior no descuenta.
Dos reversiones se serializan sobre la operación y solo una suma existencias.
Las cantidades restituidas proceden exclusivamente del snapshot persistido.
Productos deshabilitados siguen deshabilitados.

## Resultados y errores

Estados: PENDIENTE, APLICADA, RECHAZADA, REVERTIDA, CANCELADA.
No hay transición de un estado cerrado a PENDIENTE ni APLICADA.
APLICADA solo puede pasar a REVERTIDA. El snapshot no tiene setter y se conserva
sin cambios al revertir. El servicio devuelve records, no entidades administradas.

Rechazos persistidos, sin descuento:

- `PRODUCTO_INEXISTENTE`
- `PRODUCTO_DESHABILITADO`
- `STOCK_INSUFICIENTE`
- `METADATOS_INVALIDOS`
- `PRECIO_INVALIDO`
- `IMPORTE_FUERA_DE_RANGO`

No se lanzan excepciones para esos rechazos: así se confirma RECHAZADA.
Los precios son efectivos, finales CLP, Long; `multiplyExact` y `addExact`
detectan desbordamiento. No se agrega IVA.

Conflictos de identidad/contenido: `OPERACION_CONSUMIDOR_DISTINTO` y
`OPERACION_CONTENIDO_DISTINTO`. La identidad no permitida se rechaza previamente.
La reversión fallida por producto ausente o desbordamiento lanza conflicto
(`REVERSION_PRODUCTO_INEXISTENTE` / `REVERSION_STOCK_FUERA_DE_RANGO`) y conserva
APLICADA. Nunca se devuelve stock parcialmente.

Errores técnicos de aplicación provocan rollback completo y conservan PENDIENTE,
salvo que otro proceso complete/cancele después. Una pérdida de conexión al commit
puede dejar resultado desconocido para el llamador: consultar antes de concluir.
La consulta refleja el estado confirmado visible en ese instante; puede cambiar.

## Correspondencia entidad / DDL

- ID String: VARCHAR(36), PK; consumidor enum STRING/VARCHAR(64).
- Versión int: INT; hash String: VARCHAR(64).
- Solicitud y aplicación: records con `@JdbcTypeCode(JSON)` / JSON.
- Estado enum STRING con JDBC VARCHAR explícito: VARCHAR(20), no enum nativo.
- Código de rechazo String: VARCHAR(64), nullable.
- Cuatro fechas Instant/TIMESTAMP_UTC: DATETIME(6); creación/actualización obligatorias.
- Índice `(estado, fecha_actualizacion)`, mismo nombre en JPA/DDL.
- Solicitud, hash, consumidor, versión e ID no actualizables por JPA.

Los CHECK validan estados, UUID/hash, consumidor, versión, estructura JSON,
presencia de resultado/rechazo/cierre y cronología. El servicio garantiza
unicidad/orden de productos, correspondencia de cantidades e importes y hash.
Los registros son independientes; el consumidor no tiene restricción UNIQUE.
No hay FK hacia productos ni otros servicios, ni borrado/purga automática.
Las escrituras SQL directas quedan fuera de las garantías transaccionales del servicio.

## Verificación posterior en Workbench

Con la tabla ya creada, estas consultas son solo
lectura; no crean operaciones ni invocan la lógica Java.

```sql
SELECT VERSION();

SHOW CREATE TABLE checkpoint_store_catalogo.operaciones_stock;

SHOW INDEX FROM checkpoint_store_catalogo.operaciones_stock;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, DATETIME_PRECISION,
       CHARACTER_SET_NAME, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'checkpoint_store_catalogo'
  AND TABLE_NAME = 'operaciones_stock'
ORDER BY ORDINAL_POSITION;

SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE, tc.ENFORCED
FROM information_schema.TABLE_CONSTRAINTS tc
JOIN information_schema.CHECK_CONSTRAINTS cc
  ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
 AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
WHERE tc.TABLE_SCHEMA = 'checkpoint_store_catalogo'
  AND tc.TABLE_NAME = 'operaciones_stock'
  AND tc.CONSTRAINT_TYPE = 'CHECK'
ORDER BY tc.CONSTRAINT_NAME;

SELECT operacion_id, consumidor, version_contrato, estado, hash_solicitud,
       codigo_rechazo, fecha_creacion, fecha_actualizacion,
       fecha_aplicacion, fecha_cierre,
       JSON_PRETTY(solicitud) AS solicitud,
       JSON_PRETTY(resultado_aplicacion) AS snapshot
FROM checkpoint_store_catalogo.operaciones_stock
ORDER BY fecha_creacion, operacion_id;

SELECT estado, COUNT(*) AS cantidad
FROM checkpoint_store_catalogo.operaciones_stock
GROUP BY estado;

SELECT o.operacion_id, o.estado, d.producto_id, d.cantidad,
       d.precio_unitario, d.subtotal, p.stock, p.habilitado
FROM checkpoint_store_catalogo.operaciones_stock o
JOIN JSON_TABLE(o.resultado_aplicacion, '$.items[*]' COLUMNS (
    producto_id BIGINT PATH '$.productoId',
    cantidad INT PATH '$.cantidad',
    precio_unitario BIGINT PATH '$.precioUnitario',
    subtotal BIGINT PATH '$.subtotal'
)) AS d ON TRUE
LEFT JOIN checkpoint_store_catalogo.productos p ON p.id = d.producto_id
ORDER BY o.operacion_id, d.producto_id;
```

Una tabla vacía tras el DDL es lo esperado. El stock actual por sí solo no prueba
el descuento histórico: otras operaciones o movimientos administrativos pueden
haberlo cambiado.

## Límites de la verificación de esta etapa

Compilación Java 21 e inspección estática no prueban la ejecución del DDL, el
mapeo JSON en MySQL ni las carreras reales. No se ejecutan SQL, servicios, HTTP ni
pruebas unitarias nuevas. No hay recuperador automático: un PENDIENTE se recupera
mediante una futura llamada interna a aplicar o revertir con la misma identidad.
Conservar operaciones y tombstones CANCELADA mientras sean posibles reintentos.

La matriz manual HTTP de 4.3 está en `API_INTERNA_STOCK.md`; no fue ejecutada por
el agente. Incluye identidad de servicio, JWT humanos, idempotencia y carreras.
Ventas, pedidos y recuperación entre servicios pertenecen a etapas posteriores.
