# Ventas — configuración local y esquema manual (Etapa 4.6B.1)

Etapa 4.6B.2: el usuario confirmó SQL 04 aplicado y Hibernate validate correcto.
La materialización local requiere permisos adicionales de INSERT sobre pedidos
y detalle_pedidos; ver [MATERIALIZACION_PEDIDO.md](MATERIALIZACION_PEDIDO.md)
para permisos mínimos y verificaciones pendientes. No hay nuevo script SQL.

**Instalación existente:** el usuario confirmó SQL 03 aplicado y validado con
Hibernate. No repetir 01/02/03 si ya están aplicados. Se conserva `ddl-auto=validate`.
La etapa 4.6B.1 requiere aplicar manualmente SQL 04 antes del próximo arranque:
ver sección 1.3. El agente no ejecutó SQL.
El registro y la reclamación internos están documentados en
[SOLICITUDES_PEDIDO.md](SOLICITUDES_PEDIDO.md); necesitan el GRANT mínimo pendiente
de la sección 1.2 antes de probar escrituras.
Ver la sección **1.2** para la finalidad, restricciones y consultas de verificación.

Requisitos existentes: Java 21, MySQL 8.4, PowerShell y Maven Wrapper del
repositorio. Para el recorrido por Gateway también se necesitan Eureka y Gateway.
Para el entorno completo, usar por doble clic `../iniciar_checkpoint_local.bat`:
inicia Eureka, Identidad, Catálogo, Ventas y Gateway. Solicita la contraseña
`checkpoint_app` y, por separado, `checkpoint_ventas_app`, ambas ocultas.
Genera el JWT_SECRET humano compartido y prepara STOCK_SERVICE_JWT_SECRET
solo para Catálogo/Ventas. Desde Etapa 4.5 Ventas contiene un cliente interno que
emite JWT al invocarse; aún no hay flujo ni endpoint de pedidos que lo invoque.
Mantiene `ddl-auto=validate` y los permisos SELECT actuales. No ejecutar
simultáneamente ese lanzador y las consolas aisladas de este documento.
Ver [arranque Windows](../ARRANQUE_LOCAL_WINDOWS.md) para disponibilidad,
registro VENTA-SERVICE en Eureka, detención y login tras reiniciar.

**El servicio conserva la lógica heredada. Solo realizar las consultas GET
indicadas. No ejecutar POST, PUT o DELETE para simular compras.** La etapa 2
añade JWT local y bloquea las operaciones heredadas inseguras; las comprobaciones
negativas del bloqueo están en [SEGURIDAD_VENTAS.md](SEGURIDAD_VENTAS.md).
La etapa 3.1 incorpora únicamente el mapping persistente de pedidos, sin API,
checkout, pagos ni integración con Identidad/Catálogo.
La etapa 3.2 añade exclusivamente los cuatro GET documentados en
[CONSULTAS_PEDIDOS.md](CONSULTAS_PEDIDOS.md), con permisos en Ventas y Gateway.
La etapa 3.2 no requirió SQL adicional; 4.6A.1 sí añade SQL 03.
Para esta etapa de mapping se mantienen los permisos de lectura de MySQL.
No necesita arrancar Usuarios, Inventario, Promociones, Pedidos ni Despachos
para consultar la colección vacía.

## 1. Preparar MySQL manualmente

Esta preparación corresponde a la etapa 1. Si ya está validada, no repetirla.
Aplicar solo los scripts aditivos pendientes: 02 (sección 1.1) y 03 (sección 1.2).

Con una cuenta administradora, revisar y ejecutar una sola vez:

`sql/01_crear_checkpoint_store_ventas.sql`

Orden del archivo: crear `checkpoint_store_ventas`, crear `ventas`, crear
`detalle_ventas` con su FK local. No contiene datos de ejemplo. Si la base
ya existe, detenerse: no borrar, reemplazar ni continuar automáticamente.
El DDL hace commit implícito; tras un fallo revisar qué se aplicó.
No ejecutar `src/main/resources/db/changelog/db.changelog.sql`: es histórico.
No modificar `db_ventas`, `db_pedidos` ni `db_despachos`.

Después, crear una cuenta local propia para esta etapa. Sustituir el marcador
por una contraseña propia antes de ejecutar, sin guardarla en Git:

```sql
CREATE USER 'checkpoint_ventas_app'@'localhost'
    IDENTIFIED BY 'REEMPLAZAR_POR_CONTRASENA_PROPIA';
GRANT SELECT ON checkpoint_store_ventas.*
    TO 'checkpoint_ventas_app'@'localhost';
```

SELECT es suficiente para validar el esquema y consultar en esta etapa.
No conceder CREATE, ALTER o DROP a la aplicación. Si la cuenta ya existe,
inspeccionar sus permisos en vez de recrearla o cambiar su contraseña.

Correspondencia revisada en código:

| Java | SQL |
| --- | --- |
| IDs `Long`, estrategia IDENTITY | BIGINT AUTO_INCREMENT |
| `Venta.usuarioId`, `DetalleVenta.productoId` | BIGINT NOT NULL, referencias externas sin FK |
| `Venta.fechaVenta` (`LocalDateTime`) | fecha_venta DATETIME(6) NOT NULL |
| Importes y cantidad `Integer` | INT NOT NULL |
| `DetalleVenta.venta`, obligatorio | venta_id NOT NULL, índice y FK a ventas |

Se conserva el modelo actual, sin añadir restricciones comerciales nuevas.
`CascadeType.ALL` es una operación JPA, no una cascada de borrado SQL.
La revisión estática no demuestra que Hibernate haya validado una base real.

### 1.1 Etapa 3.1 — añadir el modelo de pedidos

Con los servicios detenidos, usar una cuenta administradora para revisar y
ejecutar una sola vez `sql/02_crear_tablas_pedidos.sql` sobre MySQL 8.4.
El archivo utiliza nombres cualificados de `checkpoint_store_ventas` y crea
únicamente `pedidos` y `detalle_pedidos`. No ejecutar otra vez 01, ni crear
otra base, ni modificar `ventas`, `detalle_ventas` o las bases heredadas.
Si alguna tabla nueva ya existe, detenerse e inspeccionarla; no borrar ni
continuar automáticamente. El DDL hace commit implícito.

La cuenta de aplicación con `SELECT ON checkpoint_store_ventas.*` también
puede consultar las tablas nuevas y es suficiente para `ddl-auto=validate`.
No ampliar permisos en esta etapa. Antes del checkout deberán concederse los
permisos de escritura necesarios sobre las tablas nuevas mediante un paso
manual separado; no hacen falta permisos DDL para la aplicación.

#### Mapping y límites deliberados

- `Pedido` y `DetallePedido` son entidades nuevas independientes de Venta.
  `EntregaPedido` es un embebido: sus seis columnas viven en `pedidos`.
- Long se almacena como BIGINT; cantidad como INT; importes en CLP enteros.
  No hay tarifa predeterminada, IVA adicional, pagos ni cálculos automáticos.
- Enums: `@Enumerated(STRING)` + `@JdbcTypeCode(VARCHAR)`, no ENUM nativo MySQL.
  CHECK exige los nombres exactos en mayúsculas, incluso con colación insensible.
- Claves idempotentes y de operación: UUID textual canónico en minúsculas de
  36 caracteres. Hash: SHA-256 hexadecimal en minúsculas de 64 caracteres.
  SQL usa VARCHAR ASCII para estos tres campos; JPA los mapea como String/VARCHAR.
- La intención CREADO se persistirá después de preparar snapshots e importes:
  comprador, fechas, estados, totales, indicadores, claves y hash son NOT NULL,
  sin defaults que inventen esos valores. `@Version` es administrado por JPA.
- Entrega nullable para digitales; el CHECK exige destinatario/teléfono/dirección/
  región/comuna no vacíos para físicos o mixtos. Referencia es opcional (500).
  Para solo digitales, las seis columnas de entrega son NULL y despacho es 0.
- Motivo de cancelación (500) y las tres fechas de cancelación/cumplimiento
  son opcionales. No se exige fecha de cancelación durante REVERSION_PENDIENTE.
  No se restringen aún combinaciones entre estados comercial y técnico:
  las transiciones y la compensación necesitan reglas de servicio posteriores.
- Los detalles conservan referencia externa productoId y snapshots SKU (64),
  nombre (255), categoría (20), formato (10) y plataforma (255).
  VIDEOJUEGO exige formato y plataforma; otras categorías permiten ambos nulos
  y pueden conservar valores existentes de Catálogo.
- Única FK: detalle_pedidos.pedido_id → pedidos.id, RESTRICT.
  Sin FK entre bases. Cascadas JPA solo PERSIST/MERGE, sin REMOVE/orphanRemoval.
- SQL comprueba subtotales y total final por fila. Los CAST a DECIMAL en CHECK
  evitan overflow de las operaciones intermedias; las columnas siguen BIGINT.
  Suma de detalles, composición de la cabecera, al menos una línea, autorización,
  generación de claves, transiciones y recuperación quedan para futuros servicios.
  No hay repositorios, endpoints, listeners ni callbacks que ejecuten estos flujos.
- El CHECK no sustituye validación de DTO/servicio. `validate` no demuestra que
  todas las restricciones e índices estén instalados: inspeccionar SHOW CREATE.

#### Convención temporal UTC

Las cinco fechas de Pedido usan **Instant** y JDBC TIMESTAMP_UTC en Hibernate,
con columna explícita DATETIME(6). El ejemplo conserva JDBC `serverTimezone=UTC`
y añade `hibernate.jdbc.time_zone=UTC`. MySQL DATETIME no guarda una zona:
su contenido se interpreta como UTC por este contrato, no por la zona del servidor.
No hay DEFAULT CURRENT_TIMESTAMP ni ON UPDATE; el futuro servicio asignará
Instant con precisión de microsegundos (por ejemplo, `truncatedTo(MICROS)`).
Las futuras respuestas deben serializar Instant como ISO-8601 UTC con `Z`;
no añadir `Z` a un LocalDateTime. Las entidades heredadas no se han cambiado.
Una sesión MySQL de verificación puede mostrar otra zona: no transforma por sí
sola DATETIME. Las escrituras manuales futuras deberán respetar UTC explícitamente.

#### Consultas de verificación (solo para ejecución manual posterior)

```sql
SELECT VERSION(), @@session.time_zone, @@global.time_zone;
SHOW TABLES FROM checkpoint_store_ventas;
SHOW CREATE TABLE checkpoint_store_ventas.pedidos;
SHOW CREATE TABLE checkpoint_store_ventas.detalle_pedidos;
SHOW INDEX FROM checkpoint_store_ventas.pedidos;
SHOW INDEX FROM checkpoint_store_ventas.detalle_pedidos;

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE,
       COLUMN_DEFAULT, DATETIME_PRECISION, EXTRA, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'checkpoint_store_ventas'
  AND TABLE_NAME IN ('pedidos', 'detalle_pedidos')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE, tc.ENFORCED
FROM information_schema.TABLE_CONSTRAINTS tc
JOIN information_schema.CHECK_CONSTRAINTS cc
  ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
 AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
WHERE tc.TABLE_SCHEMA = 'checkpoint_store_ventas'
  AND tc.TABLE_NAME IN ('pedidos', 'detalle_pedidos')
  AND tc.CONSTRAINT_TYPE = 'CHECK'
ORDER BY tc.TABLE_NAME, tc.CONSTRAINT_NAME;

SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME, UPDATE_RULE, DELETE_RULE
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = 'checkpoint_store_ventas'
  AND TABLE_NAME = 'detalle_pedidos';

SELECT COUNT(*) AS pedidos FROM checkpoint_store_ventas.pedidos;
SELECT COUNT(*) AS detalles FROM checkpoint_store_ventas.detalle_pedidos;
SHOW GRANTS FOR CURRENT_USER();
```

Esperado: cuatro tablas de negocio en total (las dos antiguas más las dos
nuevas), nuevas tablas vacías; PK BIGINT autoincrementales; cinco DATETIME(6)
en pedidos (dos obligatorias, tres opcionales); tres UNIQUE y cuatro índices
de consulta explícitos en el conjunto nuevo; FK local RESTRICT; CHECK ENFORCED=YES.
Los índices UNIQUE también son índices: no duplicarlos.
SHOW GRANTS con la cuenta de aplicación debe incluir SELECT sobre la base.

#### Validación mínima del arranque, después de revisar y aplicar 02

1. Usar la consola B indicada abajo, con el ejemplo actualizado y las variables
   ya utilizadas. Para validar solo mapping, establecer además
   `$env:EUREKA_CLIENT_ENABLED = 'false'` en esa consola dedicada: no hace falta
   iniciar otros microservicios para esta comprobación estructural.
2. Iniciar Ventas manualmente y comprobar arranque completo sin errores
   Schema-validation, manteniendo `ddl-auto=validate`. Una tabla ausente o un
   tipo incompatible debe impedir el arranque; no usar update/create.
3. Repetir los dos conteos de tablas nuevas: deben seguir en cero.
4. Detener Ventas y cerrar la consola (la desactivación de Eureka era puntual).

No hace falta Postman para este paso: no se añadió ninguna API. No repetir
compras ni intentar insertar datos para validar reglas todavía no implementadas.
Compilar e inspeccionar mapping/SQL no certifica ejecución real en MySQL.

### 1.2 Etapa 4.6A.1 — intención técnica durable

Script completo: [`sql/03_crear_solicitudes_pedido.sql`](sql/03_crear_solicitudes_pedido.sql).
Crear únicamente `checkpoint_store_ventas.solicitudes_pedido`, manualmente con
una cuenta administradora y los servicios detenidos. Sin semillas, cambios en
tablas existentes, permisos ni FK externas. Ante una tabla existente o error de
DDL, detenerse e inspeccionar: MySQL hace commit implícito.

`SolicitudPedido` conserva comprador autenticado, clave idempotente, solicitud
canónica versión 1, hash SHA-256 y UUID estable de stock antes de obtener precios.
No es un `Pedido`: no tiene importes, entrega ni snapshots comerciales, y no
aparece en las consultas actuales de pedidos. En 4.6A.2 se añaden repositorio y
servicio de persistencia/reclamación; no hay endpoints, tareas programadas ni
llamadas remotas para esta entidad.

#### Estados y reclamación

- `PENDIENTE`: trabajo registrado y todavía sin resolver.
- `RECONCILIACION_PENDIENTE`: requiere verificar el resultado remoto original;
  no significa rechazo de compra ni ausencia de descuento.
- `RESUELTA`: coordinación terminada. No equivale por sí sola a compra exitosa,
  pedido confirmado o stock aplicado. El criterio y resultado durable de
  resolución deberán definirse al implementar el coordinador.

Una reclamación se representa mediante `token_reclamacion` y `bloqueo_hasta`,
ambos nulos inicialmente o ambos informados. Puede existir para PENDIENTE o
RECONCILIACION_PENDIENTE; RESUELTA exige que ambos vuelvan a null.
No se duplica esa información con un estado RECLAMADA.
El vencimiento puede quedar en el pasado: el CHECK no impide recuperar trabajo.
Los índices `(estado, fecha_actualizacion, id)` y `(estado, bloqueo_hasta, id)`
permiten buscar pendientes y reclamaciones vencidas.

El servicio interno reclama con una transacción local corta, valida token/vigencia
y conserva el UUID remoto al recuperar. `@Version` no sustituye esas validaciones:
las actualizaciones SQL
directas/bulk deberán comprobar e incrementar la versión explícitamente.
Un arrendamiento vencido no detiene a un trabajador pausado; la idempotencia
remota por el mismo UUID sigue siendo necesaria. Ningún CHECK compara con NOW().
Antes de un futuro envío remoto deberá persistirse que puede necesitar
reconciliación; un timeout no deberá marcar la intención como fallo definitivo.

#### Inmutabilidad, canonicalización y correspondencia

La entidad expone getters, constructor inicial y transiciones protegidas de
reclamación, renovación, reconciliación y liberación; no permite resolver.
Comprador, claves, versión del contrato,
solicitud, hash y creación están marcados `updatable=false`.
`ContratoStock.Solicitud` es un record con lista inmutable: valida versión 1,
1..100 ítems positivos y sin duplicados. **No canonicaliza ni calcula el hash.**
El constructor de la entidad exige productos en orden ascendente por ID, UUID
tipados y hash hexadecimal en minúsculas de longitud 64. SolicitudPedidoService
ordena los productos, calcula el hash y compara hash y contenido ante repeticiones.
No obtener comprador de JSON ni de headers arbitrarios: solo del JWT validado.

El constructor no persiste, no genera UUID ni calcula precios. Inicia PENDIENTE,
sin reclamación, y trunca el Instant recibido a microsegundos para asignar
exactamente el mismo instante a creación y actualización.

| Java / JPA | SQL 03 |
|---|---|
| id Long / IDENTITY | BIGINT AUTO_INCREMENT, PK |
| compradorId Long | BIGINT NOT NULL, CHECK positivo |
| UUID de entrada normalizado a String | VARCHAR(36) ascii_bin, CHECK UUID minúscula |
| versionContrato int | INT NOT NULL, CHECK = 1 |
| ContratoStock.Solicitud / JdbcTypeCode(JSON) | JSON NOT NULL, JSON_SCHEMA_VALID |
| hash String | VARCHAR(64) ascii_bin, CHECK SHA-256 hexadecimal |
| estado EnumType.STRING + JdbcTypeCode(VARCHAR) | VARCHAR(30) ascii_bin, CHECK de estados |
| version Long / @Version | BIGINT NOT NULL, CHECK >= 0 |
| tokenReclamacion String | VARCHAR(36) ascii_bin nullable |
| Instant / TIMESTAMP_UTC | DATETIME(6), bloqueo nullable; creación/actualización obligatorias |

Hibernate inicializa la versión optimista al persistir; el constructor la deja
sin asignar. No hay defaults SQL, callbacks temporales ni auditoría automática.
El mapping JSON reutiliza los records y el soporte Hibernate/Jackson disponibles,
sin dependencias nuevas. El JSON admite exactamente versión, IDs y cantidades;
su versión siempre coincide con la columna porque ambas solo admiten 1.

La unicidad `(comprador_id, clave_idempotencia)` reserva la clave por comprador;
`operacion_stock_id` es globalmente único en esta tabla. El mismo UUID deberá
copiarse posteriormente al pedido materializado, sin regenerarlo.
SQL valida estructura JSON, pero no orden por ID, ausencia de IDs repetidos,
correspondencia matemática del hash ni inmutabilidad histórica. Estas garantías
dependen del modelo y del servicio interno; `updatable=false` no impide SQL manual.
La futura creación de pedidos deberá comprobar coherencia entre las dos tablas.

#### Consultas Workbench (solo lectura, para después de ejecutar 03)

```sql
SHOW CREATE TABLE checkpoint_store_ventas.solicitudes_pedido;
SHOW INDEX FROM checkpoint_store_ventas.solicitudes_pedido;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT,
       EXTRA, DATETIME_PRECISION, CHARACTER_SET_NAME, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'checkpoint_store_ventas'
  AND TABLE_NAME = 'solicitudes_pedido'
ORDER BY ORDINAL_POSITION;

SELECT tc.CONSTRAINT_NAME, tc.ENFORCED, cc.CHECK_CLAUSE
FROM information_schema.TABLE_CONSTRAINTS tc
JOIN information_schema.CHECK_CONSTRAINTS cc
  ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
 AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
WHERE tc.TABLE_SCHEMA = 'checkpoint_store_ventas'
  AND tc.TABLE_NAME = 'solicitudes_pedido'
  AND tc.CONSTRAINT_TYPE = 'CHECK'
ORDER BY tc.CONSTRAINT_NAME;

SELECT COUNT(*) AS solicitudes
FROM checkpoint_store_ventas.solicitudes_pedido;

SELECT id, comprador_id, clave_idempotencia, version_contrato,
       hash_solicitud, operacion_stock_id, estado, version,
       token_reclamacion, bloqueo_hasta, fecha_creacion, fecha_actualizacion,
       JSON_PRETTY(solicitud) AS solicitud
FROM checkpoint_store_ventas.solicitudes_pedido
ORDER BY id;

SELECT id, estado, operacion_stock_id, bloqueo_hasta
FROM checkpoint_store_ventas.solicitudes_pedido
WHERE estado IN ('PENDIENTE', 'RECONCILIACION_PENDIENTE')
  AND (bloqueo_hasta IS NULL OR bloqueo_hasta <= UTC_TIMESTAMP(6))
ORDER BY estado, bloqueo_hasta, id;

SHOW GRANTS FOR CURRENT_USER();
```

Esperado al instalar: tabla vacía, PK, dos UNIQUE, dos índices secundarios,
CHECK ENFORCED y 13 columnas. La consulta de candidatos no reclama filas ni
garantiza exclusividad. Para revisar permisos de la aplicación, SHOW GRANTS
debe ejecutarse conectado como checkpoint_ventas_app.

#### Permisos posteriores y límites

El SELECT existente sobre `checkpoint_store_ventas.*` cubre esta tabla y permite
validar el mapping. Antes de probar el registro/reclamación internos, una cuenta
administradora deberá autorizar **INSERT y UPDATE únicamente sobre
checkpoint_store_ventas.solicitudes_pedido** para la cuenta local
`checkpoint_ventas_app` correspondiente. No se requieren DELETE ni permisos DDL.
No se incluye GRANT en SQL 03 ni se ejecutó durante esta tarea.
Preparado para ejecución manual posterior, tras confirmar que la cuenta real
es `checkpoint_ventas_app`@`localhost`:

```sql
GRANT INSERT, UPDATE ON checkpoint_store_ventas.solicitudes_pedido
TO 'checkpoint_ventas_app'@'localhost';
```

No recrear la cuenta, cambiar contraseña ni ampliar permisos sobre otras tablas.
Si el host de la cuenta instalada es diferente, verificarlo con el administrador
antes de adaptar el GRANT; no crear una segunda cuenta por accidente.

La compilación e inspección no validan MySQL ni la serialización real de JSON.
Después de aplicar 03 se podrá comprobar arranque Hibernate con validate y que
la tabla sigue vacía si no se invoca el servicio. Registro, deduplicación y leases
podrán probarse con un llamador Java autorizado después del GRANT; todavía no por
HTTP. Resolución y coordinación con Catálogo no están implementadas.
La versión 1 de intención solo conserva productos;
entrega y aceptación comercial requerirán un diseño posterior antes del checkout.

### 1.3 Etapa 4.6B.1 — checkout, compensación y resultado

Script completo: [`sql/04_ampliar_solicitudes_pedido_checkout.sql`](sql/04_ampliar_solicitudes_pedido_checkout.sql).
Ejecutar una sola vez, manualmente, con escritores detenidos y cuenta administradora.
No repetir 01/02/03. El script solo hace ALTER sobre solicitudes_pedido.
Se conserva `ddl-auto=validate`: sin las nuevas columnas Ventas no podrá arrancar.
No hay cambios de permisos ni de configuración de credenciales.

Antes del ALTER:

```sql
SHOW CREATE TABLE checkpoint_store_ventas.solicitudes_pedido;
SELECT estado, COUNT(*) AS cantidad
FROM checkpoint_store_ventas.solicitudes_pedido
GROUP BY estado;
SELECT id, operacion_stock_id
FROM checkpoint_store_ventas.solicitudes_pedido
WHERE estado = 'RESUELTA';
```

Si existen RESUELTA, detenerse: SQL 04 exige resultado terminal y no inventa uno
para filas anteriores. La aplicación previa no tenía método de resolución.
Si las columnas ya existen o hubo un error, inspeccionar antes de reintentar.
DDL hace commit implícito; no confiar en un rollback de una sesión de Workbench.

Después del ALTER, validar:

```sql
SHOW CREATE TABLE checkpoint_store_ventas.solicitudes_pedido;
SHOW INDEX FROM checkpoint_store_ventas.solicitudes_pedido;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, CHARACTER_SET_NAME, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'checkpoint_store_ventas'
  AND TABLE_NAME = 'solicitudes_pedido'
ORDER BY ORDINAL_POSITION;

SELECT tc.CONSTRAINT_NAME, tc.ENFORCED, cc.CHECK_CLAUSE
FROM information_schema.TABLE_CONSTRAINTS tc
JOIN information_schema.CHECK_CONSTRAINTS cc
  ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
 AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
WHERE tc.TABLE_SCHEMA = 'checkpoint_store_ventas'
  AND tc.TABLE_NAME = 'solicitudes_pedido'
  AND tc.CONSTRAINT_TYPE = 'CHECK'
ORDER BY tc.CONSTRAINT_NAME;

SELECT id, estado, hash_solicitud, hash_checkout, codigo_compensacion,
       JSON_PRETTY(checkout) AS checkout,
       JSON_PRETTY(resultado) AS resultado,
       token_reclamacion, bloqueo_hasta
FROM checkpoint_store_ventas.solicitudes_pedido
ORDER BY id;
```

Esperado: 17 columnas; cuatro nuevas nullable (checkout/resultado JSON, hash y
código VARCHAR(64)); mismos PK, dos UNIQUE y dos índices secundarios.
CHECK del estado ampliado y nuevos CHECK ENFORCED; los de stock, fechas y leases
siguen presentes. Filas abiertas anteriores mantienen los cuatro campos NULL.

Los campos JSON se mapean mediante records inmutables y `@JdbcTypeCode(JSON)`.
Los dos VARCHAR(64) corresponden a String; checkout/hash no actualizables por JPA.
No cambia @Version ni la precisión DATETIME(6) de las fechas.
El contrato completo y los hashes se describen en SOLICITUDES_PEDIDO.md.

Pendiente: ejecución real del ALTER y arranque Hibernate con validate, roundtrip
JSON e inspección de estados/constraints. No se ejecutaron en esta tarea.
No hay flujo de checkout, descuentos, materialización o compensación que pueda
probarse por HTTP. Los métodos de lease existentes mantienen COMPENSACION_PENDIENTE.
El registro antiguo aún crea intenciones sin checkout, no aptas para descontar.

## 2. Configuración y arranque aislado

Abrir consolas PowerShell dedicadas desde la raíz del repositorio, sin perfiles,
variables Spring heredadas ni transcripción de secretos. No usar `setx`.
Los comandos siguientes son para ejecución manual posterior a la revisión.

El ejemplo se carga **explícitamente**, reemplazando las ubicaciones por defecto;
no copiar ni cargar el `application.properties` local heredado. Variables externas
de mayor prioridad pueden sobrescribir propiedades: usar el entorno limpio indicado.

### Consola A — Eureka

```powershell
Set-Location eureka-server
$env:SPRING_CONFIG_LOCATION = 'file:./src/main/resources/application.properties'
.\mvnw.cmd "-Dmaven.test.skip=true" spring-boot:run
```

Comprobar Eureka en `http://127.0.0.1:8761` antes de seguir.

### Consola B — Ventas

```powershell
Set-Location venta-service
$env:SPRING_CONFIG_LOCATION = 'file:./src/main/resources/application.properties.example[.properties]'
$env:VENTAS_DB_USERNAME = 'checkpoint_ventas_app'
$dbSecret = Read-Host 'Contraseña MySQL de Ventas' -AsSecureString
$env:VENTAS_DB_PASSWORD = (New-Object System.Net.NetworkCredential('', $dbSecret)).Password
$jwtSecret = Read-Host 'JWT_SECRET de la instalación existente' -AsSecureString
$env:JWT_SECRET = (New-Object System.Net.NetworkCredential('', $jwtSecret)).Password
$stockSecret = Read-Host 'STOCK_SERVICE_JWT_SECRET compartido con Catalogo (distinto del humano)' -AsSecureString
$env:STOCK_SERVICE_JWT_SECRET = (New-Object System.Net.NetworkCredential('', $stockSecret)).Password
$env:EUREKA_URL = 'http://127.0.0.1:8761/eureka/'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
$env:SPRING_SQL_INIT_MODE = 'never'
$env:SPRING_LIQUIBASE_ENABLED = 'false'
try {
    .\mvnw.cmd "-Dmaven.test.skip=true" spring-boot:run
} finally {
    Remove-Item Env:VENTAS_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:JWT_SECRET -ErrorAction SilentlyContinue
    Remove-Item Env:STOCK_SERVICE_JWT_SECRET -ErrorAction SilentlyContinue
    $stockSecret.Dispose()
    $dbSecret = $null
    $jwtSecret = $null
}
```

El ejemplo fija loopback, puerto 9094 y JDBC a `checkpoint_store_ventas`,
sin `createDatabaseIfNotExist`. Requiere `VENTAS_DB_USERNAME`,
`VENTAS_DB_PASSWORD` y `JWT_SECRET`; `EUREKA_URL` tiene default local.
JWT_SECRET debe ser exactamente el mismo texto que usan Identidad y Gateway,
de al menos 32 bytes UTF-8 (no se decodifica Base64); solo se acepta HS256.
Desde 4.5 también requiere STOCK_SERVICE_JWT_SECRET del entorno: al menos 32 bytes
UTF-8, diferente del secreto humano e idéntico al de Catálogo. Sin un secreto válido,
el arranque falla. El lanzador de cinco servicios ya lo proporciona automáticamente.
Detalles del cliente y recuperación: [CLIENTE_STOCK.md](CLIENTE_STOCK.md).

Se retiraron los starters de Liquibase de ejecución y pruebas del POM de Ventas.
El changelog histórico queda como archivo inerte: no hay dependencia que lo
ejecute y el ejemplo además establece `spring.liquibase.enabled=false`.
`spring.sql.init.mode=never` desactiva inicialización SQL y `validate` solo
comprueba el mapping: no crea ni altera tablas. No hay cargadores automáticos
de datos en las fuentes de Ventas.

Si falta una tabla o hay incompatibilidad, el arranque debe fallar. Revisar
el SQL; nunca cambiar Hibernate a `update` o `create` para ocultar el problema.

### Consola C — Gateway

Usar el mismo secreto externo de la instalación de Identidad que emitió el JWT;
no generar uno diferente si se pretende reutilizar un token existente.

```powershell
Set-Location api-gateway
$env:SPRING_CONFIG_LOCATION = 'file:./src/main/resources/application.yml.example[.yaml]'
$env:EUREKA_URL = 'http://127.0.0.1:8761/eureka/'
$jwtSecret = Read-Host 'JWT_SECRET de la instalación existente' -AsSecureString
$env:JWT_SECRET = (New-Object System.Net.NetworkCredential('', $jwtSecret)).Password
try {
    .\mvnw.cmd "-Dmaven.test.skip=true" spring-boot:run
} finally {
    Remove-Item Env:JWT_SECRET -ErrorAction SilentlyContinue
    $jwtSecret = $null
}
```

Esperar a que Eureka registre `VENTA-SERVICE` y Gateway descubra la instancia.
Las rutas se conservan; las reglas de Ventas en Gateway reflejan el bloqueo
temporal definido en SEGURIDAD_VENTAS.md.

## 3. Comprobaciones manuales exactas

### MySQL: antes y después del arranque y de los GET

```sql
SHOW TABLES FROM checkpoint_store_ventas;
SHOW CREATE TABLE checkpoint_store_ventas.ventas;
SHOW CREATE TABLE checkpoint_store_ventas.detalle_ventas;
SHOW INDEX FROM checkpoint_store_ventas.detalle_ventas;
SELECT COUNT(*) AS ventas FROM checkpoint_store_ventas.ventas;
SELECT COUNT(*) AS detalles FROM checkpoint_store_ventas.detalle_ventas;
```

Esperado: las dos tablas heredadas y, tras aplicar 02, las dos tablas de pedidos,
sin tablas de control del migrador; PK autoincrementales, columnas obligatorias,
FK e índices de detalles.
Ambos conteos deben ser 0 antes y después. La estructura debe permanecer igual.
Hibernate no sustituye la inspección de restricciones con SHOW CREATE TABLE.

### Postman

1. `GET http://127.0.0.1:9094/api/ventas`, sin Authorization: esperado **401**
   desde la etapa 2.
2. `GET http://127.0.0.1:9090/api/ventas`, sin Authorization: esperado 401,
   porque Gateway mantiene su seguridad.
3. Mismo GET por ambos puertos, con Bearer JWT ADMINISTRADOR vigente emitido por
   la instalación existente y firmado con el mismo secreto: esperado 200,
   colección sin ventas. Los enlaces pueden diferir del acceso directo.
   Una colección vacía puede omitir `_embedded`; no exigir un JSON `[]`.
4. Repetir los conteos MySQL: ambos deben continuar en 0.

Con solo estos tres servicios no hay login operativo. Si no se dispone de
un JWT administrativo vigente, dejar pendiente el paso 3 hasta obtenerlo
por el flujo existente de Identidad; no abrir rutas ni fabricar tokens para
esta validación. El 401 no demuestra por sí solo el enrutamiento a Ventas.

Las consultas por email están bloqueadas porque todavía llaman a Usuarios
heredado. Consultar la matriz de seguridad para verificar roles y bloqueos.

## 4. Límites de lo comprobado

La compilación y la comparación Java/DDL son verificaciones estáticas.
No equivalen a validación en MySQL ni a pruebas HTTP. Registrar los resultados
manuales anteriores después de revisar y aplicar el esquema.
