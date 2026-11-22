# Ventas — configuración local y esquema manual (actualizado en etapa 2)

Requisitos existentes: Java 21, MySQL 8.4, PowerShell y Maven Wrapper del
repositorio. Para el recorrido por Gateway también se necesitan Eureka y Gateway.
No usar el lanzador general: todavía no incorpora Ventas.

**El servicio conserva la lógica heredada. Solo realizar las consultas GET
indicadas. No ejecutar POST, PUT o DELETE para simular compras.** La etapa 2
añade JWT local y bloquea las operaciones heredadas inseguras; las comprobaciones
negativas del bloqueo están en [SEGURIDAD_VENTAS.md](SEGURIDAD_VENTAS.md).
No implementa pedidos nuevos, pagos ni integración con Identidad/Catálogo.
No necesita arrancar Usuarios, Inventario, Promociones, Pedidos ni Despachos
para consultar la colección vacía.

## 1. Preparar MySQL manualmente

Esta preparación corresponde a la etapa 1. Si ya está validada, no repetirla:
la etapa 2 no requiere SQL ni cambios de esquema.

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
$env:EUREKA_URL = 'http://127.0.0.1:8761/eureka/'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
$env:SPRING_SQL_INIT_MODE = 'never'
$env:SPRING_LIQUIBASE_ENABLED = 'false'
try {
    .\mvnw.cmd "-Dmaven.test.skip=true" spring-boot:run
} finally {
    Remove-Item Env:VENTAS_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:JWT_SECRET -ErrorAction SilentlyContinue
    $dbSecret = $null
    $jwtSecret = $null
}
```

El ejemplo fija loopback, puerto 9094 y JDBC a `checkpoint_store_ventas`,
sin `createDatabaseIfNotExist`. Requiere `VENTAS_DB_USERNAME`,
`VENTAS_DB_PASSWORD` y `JWT_SECRET`; `EUREKA_URL` tiene default local.
JWT_SECRET debe ser exactamente el mismo texto que usan Identidad y Gateway,
de al menos 32 bytes UTF-8 (no se decodifica Base64); solo se acepta HS256.
Sin secreto, o con uno demasiado corto, el arranque debe fallar.

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

Esperado: únicamente las dos tablas de negocio, sin tablas de control del
migrador; PK autoincrementales, columnas obligatorias, FK e índice del detalle.
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
