# Catálogo local: SQL manual y validación JPA

## Estrategia

El esquema se crea una sola vez desde MySQL Workbench con
`sql/crear_productos_checkpoint_store_catalogo.sql`. No hay migraciones
automáticas: se retiró la dependencia de Liquibase del POM y sus propiedades
de la configuración de Catálogo. No se incorpora otra herramienta.
`spring.jpa.hibernate.ddl-auto=validate` comprueba el esquema al arrancar;
`spring.sql.init.mode=never` desactiva la inicialización SQL de Spring.

El archivo heredado `src/main/resources/db/changelog/db.changelog.sql` queda
solo como referencia histórica inactiva. No ejecutarlo: contiene ferretería.
No está referenciado por la configuración y no hay Liquibase en el módulo
para procesarlo. El SQL nuevo está fuera del classpath de la aplicación.
No se crean tablas DATABASECHANGELOG ni DATABASECHANGELOGLOCK.

El script nuevo solo crea productos, con nombre de base explícito. No contiene
DROP, ALTER, DELETE, TRUNCATE ni INSERT. Si la base falta o la tabla ya existe,
falla; no reemplaza objetos existentes. No utiliza db_catalogo ni db_inventario.

## Modelo conservado

La tienda vende cuatro categorías: VIDEOJUEGO, CONSOLA, ACCESORIO y FIGURA.
`categoria` es obligatoria, sin valor por defecto. Se representa mediante
`CategoriaProducto`, con @Enumerated(STRING) y @JdbcTypeCode(VARCHAR) para
persistir los nombres en VARCHAR(20), evitando que Hibernate espere un ENUM
nativo de MySQL. El CHECK limita los cuatro valores; CAST AS BINARY exige
la escritura exacta incluso con la collation general insensible a mayúsculas.

Precio, oferta, stock, imagen, visibilidad y descripción son propiedades comunes.
rawgId, genero, plataforma y los restantes metadatos opcionales conservan su
nulabilidad. RAWG se usará únicamente para videojuegos en una etapa posterior.
Las otras categorías se crearán manualmente mediante la futura adaptación
de los endpoints administrativos normales de Catálogo.

Producto conserva id, nombre obligatorio, descripcion TEXT, plataforma, genero,
imagenUrl opcional, precio final entero positivo en CLP, precioOferta opcional
positivo, enOferta=false, stock=0 no negativo, habilitado=true y rawgId opcional.
Se mantiene marca nullable (100 caracteres) por compatibilidad.

La oferta activa exige precioOferta presente e inferior a precio. Sin oferta,
el precio efectivo es precio. No se agrega IVA. Stock y habilitado son
independientes. Se mantienen las validaciones Java y restricciones SQL.
Los defaults Java coinciden con los SQL; un null explícito no activa un default.
MySQL representa BOOLEAN como TINYINT(1).

POST y GET de `/api/productos` ya utilizan los campos nuevos de CheckPoint Store.
El POST usa ProductoCrearRequestDto; el DTO ProductoRequestDto sigue reservado
al PUT administrativo y reutiliza las validaciones del DTO de creación.
POST y PUT comparten el mapeo explícito de los campos editables, conservando
id y rawgId en edición. Las llamadas HTTP heredadas
a Inventario siguen pendientes de la consolidación; crear y consultar productos
no las ejecuta. Véase `PRUEBAS_CATALOGO_POSTMAN.md` para los contratos y ejemplos.
Hibernate validate no sustituye la revisión de CHECK y defaults con
SHOW CREATE TABLE.

## Actualización de la tabla local existente (vacía)

La base y la tabla inicial ya fueron creadas según lo informado por el usuario.
No volver a ejecutar `crear_productos_checkpoint_store_catalogo.sql`.
Con una cuenta administrativa en Workbench:

1. Comprobar que la tabla sigue vacía y aún no tiene categoria:

   ```sql
   SELECT COUNT(*) FROM checkpoint_store_catalogo.productos;
   SHOW COLUMNS FROM checkpoint_store_catalogo.productos LIKE 'categoria';
   ```

   Se esperan cero productos y ninguna columna coincidente. Si no se cumple,
   detenerse y revisar antes de aplicar el script.

2. Ejecutar una sola vez `sql/agregar_categoria_productos.sql`. Añade únicamente
   categoria y su restricción de valores; no modifica las demás columnas,
   no borra ni recrea tablas y no inserta datos. No requiere ampliar los permisos
   de checkpoint_app: el ALTER lo ejecuta la cuenta administrativa.

3. Revisar `SHOW CREATE TABLE checkpoint_store_catalogo.productos` y arrancar
   el servicio con el procedimiento descrito más abajo para comprobar JPA.
   La revisión del mapeo es estática; no acredita aún validación real de Hibernate.

## Instalaciones nuevas en MySQL Workbench

Estos pasos no se ejecutaron durante la adaptación.

1. Conectarse al MySQL 8.4 local con una cuenta administrativa y comprobar:

   ```sql
   SELECT VERSION(), @@hostname, @@port, CURRENT_USER();
   SELECT SCHEMA_NAME
   FROM information_schema.SCHEMATA
   WHERE SCHEMA_NAME = 'checkpoint_store_catalogo';
   ```

   Si la base ya existe, detenerse y revisar su contenido. No borrarla ni
   intentar reconvertir automáticamente una base preparada con Liquibase.

2. Si no existe, crear únicamente la nueva base:

   ```sql
   CREATE DATABASE `checkpoint_store_catalogo`
     CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   ```

3. Abrir en Workbench `catalogo-service/sql/crear_productos_checkpoint_store_catalogo.sql`
   y ejecutar el archivo completo una sola vez con la cuenta administrativa.
   No ejecutar el SQL histórico. El script usa el nombre cualificado de la
   tabla, independientemente del esquema seleccionado en Workbench.

4. Conceder a la cuenta existente los permisos de operación:

   ```sql
   GRANT SELECT, INSERT, UPDATE, DELETE
   ON `checkpoint_store_catalogo`.*
   TO 'checkpoint_app'@'127.0.0.1';

   SHOW GRANTS FOR 'checkpoint_app'@'127.0.0.1';
   ```

   No crea usuarios ni cambia contraseñas. No requiere FLUSH PRIVILEGES.
   La aplicación no necesita CREATE, ALTER, INDEX o DROP porque no gestiona DDL.
   Este GRANT solo añade permisos sobre la nueva base; no elimina permisos
   anteriores de la cuenta. Revisar SHOW GRANTS para conocer sus permisos
   efectivos sin revocar accesos de otros servicios en esta tarea.

## Arranque aislado desde Windows PowerShell

La configuración privada anterior todavía contiene la conexión heredada:
no se cambiaron URL, usuario ni contraseña. Solo se retiraron sus propiedades
Liquibase y se desactivó la inicialización SQL. Sigue ignorada por Git.
No usar un arranque sin selección explícita de configuración.

El ajuste mínimo seguro consiste en cargar directamente el ejemplo revisado
con `spring.config.location`, que sustituye las ubicaciones predeterminadas.
El sufijo `[.properties]` indica a Spring cómo interpretar el archivo .example.
Así se evita cargar la conexión antigua sin sobrescribir secretos.

Desde una PowerShell nueva, sin variables SPRING_DATASOURCE_* o
SPRING_JPA_* previas que sobrescriban el ejemplo:

```powershell
Set-Location 'C:\Users\hecto\Documents\Proyectos\Proyecto Checkpoint\checkpoint-store-backend\catalogo-service'

$env:CATALOGO_DB_USERNAME = 'checkpoint_app'
$credencial = Get-Credential -UserName $env:CATALOGO_DB_USERNAME -Message 'Credenciales MySQL de catálogo'
$env:CATALOGO_DB_PASSWORD = $credencial.GetNetworkCredential().Password

# JWT_SECRET debe estar cargado desde la misma fuente privada que Identidad/Gateway.
if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    throw 'Falta JWT_SECRET compartido. Consulte SEGURIDAD_CATALOGO.md.'
}

.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.config.location=file:./src/main/resources/application.properties.example[.properties] --eureka.client.enabled=false"
```

El ejemplo conecta a `127.0.0.1:3306/checkpoint_store_catalogo`, puerto HTTP 9091.
No tiene createDatabaseIfNotExist ni credenciales embebidas. No se necesita
Eureka, Gateway ni Inventario para validar el arranque. Se requiere Java 21.
Ahora también se requiere JWT_SECRET compartido, incluso para arrancar solo
Catálogo. No se cambiaron los scripts BAT ni la configuración privada.
Esperar el inicio correcto de Spring Boot y ausencia de errores de validación
JPA. Si falta una tabla o hay tipos incompatibles, corregir el esquema manual
antes de continuar; no cambiar validate por update/create.

Después de detener el servicio con Ctrl+C:

```powershell
Remove-Item Env:CATALOGO_DB_PASSWORD
Remove-Variable credencial
```

## Comprobación de solo lectura en Workbench

```sql
SHOW TABLES FROM checkpoint_store_catalogo;
SHOW CREATE TABLE checkpoint_store_catalogo.productos;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'checkpoint_store_catalogo'
  AND TABLE_NAME = 'productos'
ORDER BY ORDINAL_POSITION;

SELECT COUNT(*) AS productos_iniciales
FROM checkpoint_store_catalogo.productos;
```

En una base nueva se espera solo productos, inicialmente vacía, con sus CHECK,
clave primaria, longitudes y defaults. En una conexión Workbench realizada
con la cuenta de aplicación, SELECT CURRENT_USER() permite comprobar que
MySQL está utilizando la cuenta esperada.

Los registros iniciales de las cuatro categorías podrán añadirse posteriormente mediante
INSERT manuales para la demostración académica. Esta etapa no incluye semillas
ni instrucciones de inserción.

La compilación y revisión estática no prueban persistencia real. Esta se
comprobará al ejecutar manualmente el SQL y arrancar Spring Boot contra MySQL.
