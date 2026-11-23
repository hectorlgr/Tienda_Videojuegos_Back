# Ventas — configuración local y esquema manual (actualizado en etapa 3.2)

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
No requiere SQL adicional ni ampliar los permisos de lectura de MySQL.
No necesita arrancar Usuarios, Inventario, Promociones, Pedidos ni Despachos
para consultar la colección vacía.

## 1. Preparar MySQL manualmente

Esta preparación corresponde a la etapa 1. Si ya está validada, no repetirla:
aplicar únicamente el paso aditivo de la sección 1.1 para la etapa 3.1.

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
