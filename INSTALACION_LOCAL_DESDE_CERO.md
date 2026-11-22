# Instalación local desde cero — Windows / Identidad y Catálogo

Auditoría estática sobre `main`, 22 de noviembre de 2026. No se arrancaron
servicios, instalaron dependencias, ejecutaron SQL ni crearon administradores
durante esta revisión. Los comandos siguientes son para el equipo receptor.

## 1. Resultado y requisitos

**El repositorio contiene lo necesario para preparar este entorno de forma
manual, sin copiar bases, secretos ni archivos privados de otro integrante.**
No es una instalación automática: hay que preparar MySQL, completar el esquema
de Catálogo con el paso 04 e inicializar una cuenta administrativa.
La ejecución en un Windows limpio todavía debe verificarse.

| Requisito | Necesidad y versión |
| --- | --- |
| JDK 21 | Obligatorio; los cuatro POM usan java.version=21. JRE solo no basta. Configurar JAVA_HOME al directorio del JDK y su bin en PATH. |
| Windows con cmd y Windows PowerShell 5.1 | Obligatorios para los BAT/PS1 y mvnw.cmd. El lanzador usa powershell.exe, no requiere PowerShell 7. No hay una edición mínima de Windows declarada en el proyecto; Windows 10/11 con estos componentes es el entorno previsto, no una matriz certificada. |
| Git para Windows | Obligatorio para clonar; no hay versión mínima específica declarada. Acceso al repositorio del equipo. |
| MySQL Server 8.4 | Referencia explícita de los scripts. Usar 8.4 para reproducibilidad; no sustituir por MariaDB/MySQL 5.7. Se usan utf8mb4_0900_ai_ci, CHECK, REGEXP_LIKE y REGEXP_REPLACE. |
| Cliente SQL y cuenta administradora de MySQL local | Obligatorios para crear esquemas/cuenta/permisos. Workbench es la opción documentada; no es obligatorio si se usa otro cliente compatible. |
| Internet inicial | Necesario para distribuciones Maven y dependencias de Maven Central; acceso al repositorio Git. Proxy/firewall pueden bloquear descargas. |
| Permisos Windows | Lectura/ejecución del clon, escritura en caché Maven, target de los módulos y carpeta local de imágenes. No se requiere ejecutar el backend como administrador de Windows. |
| Maven instalado globalmente | **No necesario**: los cuatro módulos incluyen mvnw.cmd y .mvn/wrapper/maven-wrapper.properties. |
| Postman | Opcional; cliente recomendado para verificación HTTP. |
| Node/npm y React/Vite | Solo para trabajar con el frontend; no necesarios para arrancar este backend. Sus versiones corresponden al repositorio frontend, no se deducen aquí. |
| RAWG API Key | Opcional; CRUD manual y subida local funcionan sin ella. |
| Docker, AWS, Liquibase, Flyway | No requeridos para este entorno. |

Versiones de Maven fijadas por los wrappers: Eureka **3.9.16**, Identidad
**3.9.15**, Catálogo y Gateway **3.9.14**. Los wrappers son `only-script`;
descargan la distribución la primera vez. Esta auditoría no descargó ni
comprobó disponibilidad de esos artefactos en otra red.

Comprobar en una consola nueva:

```powershell
git --version
java -version
javac -version
$PSVersionTable.PSVersion
```

Java y javac deben corresponder a 21. Comprobar MySQL desde el cliente:
`SELECT VERSION();`. No listar todas las variables de entorno: pueden contener secretos.

### Servicios y puertos

| Componente | Puerto local | Función |
| --- | --- | --- |
| MySQL | 3306 | Dos bases propias del compañero |
| Eureka | 8761 | Descubrimiento para rutas lb:// del Gateway |
| Identidad | 9096 | Registro, login y perfil |
| Catálogo | 9091 | Productos, imágenes y RAWG |
| API Gateway | 9090 | Único origen de API para React |
| Vite (opcional) | 5173 | Frontend |

El lanzador completo comprueba que 8761/9096/9091/9090 estén libres y que MySQL
acepte TCP en 127.0.0.1:3306. Comprobar TCP no valida contraseña ni tablas.
No cambiar los puertos para sortear un conflicto: identificar y detener la
instancia anterior que los ocupa. Con el lanzador los servicios escuchan en
127.0.0.1; no es un despliegue para acceso desde otros computadores.

**No arrancar** usuario-service, inventario-service, promocion-service,
venta-service, pedido-service ni despacho-service para este objetivo.
Las operaciones vigentes de Identidad/Catálogo no dependen de ellos.
El Gateway conserva sus rutas y Swagger puede mostrarlos: consultarlos sin
arrancarlos puede dar 503, pero eso no impide login ni CRUD de Catálogo.

## 2. Clonar main

Obtener la URL del repositorio del equipo (sin tokens incrustados) y ejecutar
desde la carpeta elegida:

```powershell
$url = Read-Host 'URL Git del backend proporcionada por el equipo'
git clone --branch main --single-branch $url checkpoint-store-backend
Set-Location checkpoint-store-backend
git status --short --branch
```

Todas las rutas siguientes son relativas a esta raíz. No copiar
application.properties/application.yml privados, carpetas target ni bases
del computador original. El lanzador completo carga directamente los
`.example` versionados mediante SPRING_CONFIG_LOCATION y una indicación
explícita de su formato; no hace falta renombrarlos.

Opcionalmente, antes del arranque, verificar cada wrapper con
`.\mvnw.cmd -v` desde su módulo. Esto sí puede descargar Maven.

## 3. MySQL nuevo: bases, cuenta y permisos

Instalar/iniciar MySQL 8.4 por los medios habituales del computador y conectar
Workbench con una cuenta administradora propia. Preparar todo **antes** del
primer arranque. No usar root como cuenta de la aplicación.

### 3.1 Crear bases vacías

Comprobar primero que los nombres no existen. Si existen, detenerse y revisar;
estas instrucciones no son un procedimiento de actualización ni borrado.

```sql
CREATE DATABASE checkpoint_store_identidad
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE checkpoint_store_catalogo
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

### 3.2 Crear checkpoint_app

En Workbench, Administration > Users and Privileges > Add Account:

- Login: `checkpoint_app`.
- Host local: `127.0.0.1` (el lanzador usa JDBC TCP a esa dirección).
- Definir una contraseña propia, fuerte y no vacía; introducirla en los campos
  de contraseña, no en un archivo SQL versionado ni en argumentos de consola.
- No asignar roles de administración global ni host `%`.
- Aplicar la creación.

Los documentos antiguos alternan `localhost` y `127.0.0.1` para el host de
cuenta. No son dos nombres intercambiables de una misma cuenta MySQL.
Conectar después como checkpoint_app usando TCP 127.0.0.1:3306 y comprobar
`SELECT USER(), CURRENT_USER();`: CURRENT_USER identifica la cuenta realmente
seleccionada por MySQL. Resolver cualquier diferencia con el administrador
local, sin abrir el acceso a `%`. En una instalación nueva se prepara
explícitamente la cuenta `checkpoint_app`@`127.0.0.1`.

Con la cuenta administradora de MySQL:

```sql
GRANT SELECT, INSERT, UPDATE, DELETE
  ON checkpoint_store_identidad.* TO 'checkpoint_app'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE
  ON checkpoint_store_catalogo.* TO 'checkpoint_app'@'127.0.0.1';
SHOW GRANTS FOR 'checkpoint_app'@'127.0.0.1';
```

No necesita CREATE/ALTER/DROP/INDEX ni privilegios globales. No hace falta
FLUSH PRIVILEGES tras CREATE USER/GRANT hechos por las herramientas de MySQL.
El lanzador solicita **una** contraseña y usa esta misma cuenta para las dos bases.

### 3.3 Orden exacto de esquema para una base nueva

Ejecutar con la cuenta administradora de MySQL; todavía no iniciar Java.

1. Seleccionar `checkpoint_store_identidad`:
   `USE checkpoint_store_identidad;`.
2. Ejecutar el bloque **CREATE TABLE users** de
   `identidad-service/ESQUEMA_IDENTIDAD.md` completo. Está en Markdown, no
   existe un `.sql` independiente de Identidad. No ejecutar el archivo Markdown
   entero como SQL.
3. Ejecutar completo
   `catalogo-service/sql/crear_productos_checkpoint_store_catalogo.sql`.
   Crea productos y producto_imagenes mediante nombres cualificados.
4. Ejecutar `catalogo-service/sql/03_validar_producto_formulario.sql`
   como revisión de solo lectura. En las bases vacías no hay incidencias de datos.
   **Excepción importante:** su última consulta devuelve cualquier índice único
   que contenga rawg_id, incluido el compuesto legítimo. En el esquema nuevo
   puede devolver `uq_producto_variante_rawg`; eso no es un error ni motivo
   para borrarlo. La consulta final de 05 distingue correctamente el índice
   único de una sola columna.
5. Ejecutar una vez
   `catalogo-service/sql/04_exigir_producto_completo.sql`.
   Es necesario aun en una base nueva: el CREATE inicial deja codigo nullable
   y **no instala** `uq_producto_codigo`. El paso 04 añade NOT NULL, UNIQUE de
   SKU y CHECK por categoría.
6. Ejecutar `catalogo-service/sql/05_validar_variantes_rawg.sql` como revisión
   final: SHOW INDEX debe mostrar el índice SKU y el compuesto RAWG; las
   consultas de datos incompletos/duplicados y de UNIQUE(rawg_id) deben quedar
   sin filas.

El aviso «FASE FUTURA: NO aplicar con los DTO/endpoints actuales» de 04 es
histórico. Los DTO actuales y ProductoService ya exigen el formulario completo;
la actualización al inicio de `sql/README_FORMULARIO_PRODUCTO.md` lo confirma.
No hay que esperar otra implementación para ejecutar 04 sobre una base vacía.

**No ejecutar después del CREATE nuevo:**

| Archivo | Motivo |
| --- | --- |
| agregar_categoria_productos.sql | Solo tabla antigua sin categoria; el CREATE nuevo ya la incluye. |
| 01_ampliar_producto_formulario.sql | Solo esquema antiguo sin campos de formulario/tabla de imágenes. Duplicaría columnas y tabla. |
| 02_preparar_codigos_producto.sql | Propone SKU para filas antiguas mediante SELECT; en una base vacía no hay datos que completar. |
| 06_unicidad_variantes_rawg.sql | Solo esquema existente sin índice/CHECK de variantes. El CREATE nuevo **ya los instala**. |
| catalogo-service/src/main/resources/db/changelog/db.changelog.sql | Esquema y productos históricos de ferretería, incompatible con el actual. |

Los otros `.sql` encontrados pertenecen a `usuario-service`, `inventario-service`,
`venta-service`, `pedido-service` y `despacho-service`, bajo
`src/main/resources/db/changelog/`: contienen tablas/datos heredados y no forman
parte de esta instalación. No ejecutarlos ni iniciar migradores.
No hay un script versionado que cree automáticamente usuario y bases: esos
pasos son manuales según esta sección.

### 3.4 Validación estructural y limitaciones

```sql
SHOW CREATE TABLE checkpoint_store_identidad.users;
SHOW CREATE TABLE checkpoint_store_catalogo.productos;
SHOW CREATE TABLE checkpoint_store_catalogo.producto_imagenes;
SHOW INDEX FROM checkpoint_store_catalogo.productos;
```

Revisar:

- Identidad: users con id BIGINT autoincremental; email/password/role/nombre
  VARCHAR(255), teléfono VARCHAR(30), dirección VARCHAR(255), región/comuna
  VARCHAR(100); `uk_users_email`.
- Catálogo: campos del modelo Producto, enums VARCHAR (no enum nativo),
  descripcion TEXT, precios/stock INT, booleanos MySQL BOOLEAN, rawg_id BIGINT,
  URLs VARCHAR(2048) y campos específicos del formulario.
- `codigo` VARCHAR(64) NOT NULL con `uq_producto_codigo`, incluso para ocultos.
- `uq_producto_variante_rawg(rawg_id, plataforma, formato)` y su CHECK.
  **No** UNIQUE(rawg_id) individual. Los manuales con rawg_id NULL pueden coexistir.
- SKU/plataforma usan utf8mb4_0900_ai_ci (insensible a mayúsculas y acentos).
- producto_imagenes: FK a productos, URL, orden 1–4, UNIQUE(producto_id, orden),
  restricciones de URL y borrado/reasignación RESTRICT.
- CHECK de precios/ofertas, stock no negativo, categoría, formato y campos
  específicos. La plataforma obligatoria del VIDEOJUEGO manual se valida en
  Java; el CHECK de 04 no contiene esa regla adicional.

Los nombres, tipos y tamaños se contrastaron con User, Producto y ProductoImagen.
No se detectó una columna o tabla faltante para `ddl-auto=validate`.
**La confirmación efectiva requiere arrancar contra MySQL 8.4 limpio.**
Hibernate valida el mapping; no sustituye SHOW CREATE/SHOW INDEX ni garantiza
por sí solo que estén todas las restricciones comerciales. En particular,
no confiar en un arranque correcto para dar por instalado el UNIQUE de SKU.

Ambos ejemplos usan `spring.jpa.hibernate.ddl-auto=validate` y
`spring.sql.init.mode=never`. No generan el esquema. DDL hace commit implícito:
si un archivo falla a mitad, inspeccionar qué sentencias se aplicaron antes
de repetir; no borrar tablas ni cambiar a ddl-auto=update para ocultar el fallo.

## 4. Crear el primer ADMINISTRADOR

El mecanismo existe: AdminBootstrapRunner + AdminBootstrapService. Requiere
simultáneamente perfil único `bootstrap-admin` y BOOTSTRAP_ADMIN_ENABLED=true.
Verifica loopback, nombre de base y validate. Guarda BCrypt; no eleva un
CLIENTE ni cambia una contraseña existente. Si ya existe ese email **o algún
ADMINISTRADOR**, omite la creación.

El lanzador de doble clic **deshabilita** bootstrap y limpia perfiles:
no sirve para crear el primer administrador. Hacer este paso separado, una
sola instancia, antes del arranque normal.

Abrir una consola Windows PowerShell dedicada en la raíz del clon, sin
transcripción y sin configuraciones Spring heredadas del computador anterior.
No usar setx ni guardar secretos en perfiles/archivos. El ejemplo carga el
`.example` versionado directamente, por lo que no requiere application.properties
privado. Eureka no es necesario para este arranque puntual:

```powershell
$env:SPRING_CONFIG_LOCATION = 'file:./src/main/resources/application.properties.example[.properties]'
$env:SPRING_PROFILES_ACTIVE = 'bootstrap-admin'
$env:BOOTSTRAP_ADMIN_ENABLED = 'true'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '9096'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
$env:SPRING_SQL_INIT_MODE = 'never'
$env:EUREKA_CLIENT_ENABLED = 'false'
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/checkpoint_store_identidad'
$env:DB_USERNAME = 'checkpoint_app'

$dbSecret = Read-Host 'Contraseña MySQL de checkpoint_app' -AsSecureString
$env:DB_PASSWORD = (New-Object System.Net.NetworkCredential('', $dbSecret)).Password
$env:BOOTSTRAP_ADMIN_EMAIL = Read-Host 'Correo propio del administrador'
$env:BOOTSTRAP_ADMIN_NOMBRE = Read-Host 'Nombre del administrador'
$adminSecret = Read-Host 'Contraseña propia del administrador (máximo 72 bytes UTF-8)' -AsSecureString
$env:BOOTSTRAP_ADMIN_PASSWORD = (New-Object System.Net.NetworkCredential('', $adminSecret)).Password
$bytes = New-Object byte[] 64
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
[Array]::Clear($bytes, 0, $bytes.Length)
$rng.Dispose()

try {
    Push-Location identidad-service
    try {
        .\mvnw.cmd "-Dmaven.test.skip=true" spring-boot:run
    } finally {
        Pop-Location
    }
} finally {
    Remove-Item Env:BOOTSTRAP_ADMIN_ENABLED, Env:BOOTSTRAP_ADMIN_EMAIL,
      Env:BOOTSTRAP_ADMIN_NOMBRE, Env:BOOTSTRAP_ADMIN_PASSWORD,
      Env:DB_PASSWORD, Env:DB_USERNAME, Env:DB_URL, Env:JWT_SECRET,
      Env:SPRING_PROFILES_ACTIVE, Env:SPRING_CONFIG_LOCATION,
      Env:SERVER_ADDRESS, Env:SERVER_PORT,
      Env:SPRING_JPA_HIBERNATE_DDL_AUTO, Env:SPRING_SQL_INIT_MODE,
      Env:EUREKA_CLIENT_ENABLED -ErrorAction SilentlyContinue
    $dbSecret.Dispose()
    $adminSecret.Dispose()
}
```

El entorno del proceso contiene temporalmente secretos en texto; no imprimirlo.
El JWT_SECRET puntual no necesita conservarse: no se usará ese arranque para
obtener tokens del entorno normal.

Al ver «Inicialización administrativa completada», detener Identidad con
Ctrl+C, confirmar terminación del lote si se solicita y comprobar que Java
liberó 9096. Si indica «omitida», verificar la cuenta antes de continuar; no
significa que se haya creado el administrador solicitado. Cerrar la consola
dedicada después de la limpieza. Si se interrumpe antes del finally, cerrar
la consola y detener el hijo Java, no dejar el bootstrap ejecutándose.

Con la cuenta SQL administradora, verificar sin consultar hashes:

```sql
SELECT id, nombre, email, role
FROM checkpoint_store_identidad.users
WHERE role = 'ADMINISTRADOR';
```

La contraseña debe ser propia, no vacía, y no exceder 72 bytes UTF-8.
Email válido y nombre son obligatorios (hasta 255 caracteres). No hay
contraseña predeterminada ni creación de ADMINISTRADOR por registro público.
Más detalles: `identidad-service/INICIALIZAR_ADMIN_LOCAL.md`.

## 5. Arranque normal con doble clic

1. Mantener MySQL iniciado, tablas/permisos preparados y bootstrap detenido.
2. Abrir **iniciar_checkpoint_local.bat** desde el Explorador.
3. Introducir contraseña MySQL de checkpoint_app en el prompt oculto.
4. Introducir RAWG_API_KEY propia en el prompt oculto o pulsar Enter para omitir.
   Si ya existe en el entorno, el lanzador reutiliza esa clave sin imprimirla.
5. Esperar Eureka → Identidad → Catálogo → Gateway y el mensaje de disponibilidad.
   Hay ventanas separadas de logs y esperas de hasta 180 segundos por comprobación.

El BAT llama `iniciar_identidad.ps1 -IncluirCatalogo` con Windows PowerShell
sin perfil y ExecutionPolicy Bypass solo para ese proceso. Una política
corporativa puede impedirlo; no se modifica la política global del computador.

### Configuración realmente utilizada

- Eureka: `eureka-server/src/main/resources/application.properties`.
- Identidad y Catálogo: sus `application.properties.example`.
- Gateway: `api-gateway/src/main/resources/application.yml.example`.
- Rutas administrativas de Catálogo: además se registran desde
  `api-gateway/.../config/CatalogoAdminRoutesConfig.java`, no necesitan un YAML privado.
- El modo completo neutraliza overrides de datasource, perfiles/configuración,
  puertos y validación; fija validate, init=never y desactiva migradores.
- Genera 64 bytes aleatorios con RNG criptográfico y convierte a texto Base64.
  Identidad, Catálogo y Gateway heredan exactamente ese mismo JWT_SECRET.
  Cada nuevo arranque invalida tokens antiguos: volver a hacer login.
- Eureka no recibe secretos DB/JWT. Identidad recibe DB_URL/DB_USERNAME/DB_PASSWORD;
  Catálogo recibe CATALOGO_DB_USERNAME/CATALOGO_DB_PASSWORD; Gateway no recibe DB.
  RAWG_API_KEY se asigna solo al proceso de Catálogo y se retira antes de Gateway.
- Las variables se heredan por los hijos; no se escriben secretos en argumentos
  Maven ni configuraciones. Al terminar el lanzador restaura su entorno previo.
- El modo antiguo sin `-IncluirCatalogo` espera configuraciones privadas
  application.properties/application.yml. **No usar ese modo para esta guía.**

### Imágenes

El lanzador define por defecto
`%LOCALAPPDATA%\CheckPointStore\imagenes` y
`CATALOGO_PUBLIC_BASE_URL=http://localhost:9090`.
Respeta valores previos de CATALOGO_IMAGENES_DIR/CATALOGO_PUBLIC_BASE_URL:
si vienen de otra sesión, revisar o retirar esos overrides antes de arrancar.

El lanzador no crea la carpeta ni concede ACL. ImagenService usa
Files.createDirectories al guardar una subida. La cuenta Windows que ejecuta
Java debe tener permiso de creación/escritura; normalmente lo tiene en su
LocalAppData. Eso debe probarse en el equipo receptor. No hace falta una ruta
del computador original, un directorio dentro de Git ni AWS/S3.
Las imágenes permanecen en disco al detener servicios; MySQL guarda sus URLs.

## 6. Pruebas mínimas en Postman

Base: **http://localhost:9090**. No guardar contraseñas ni tokens en colecciones
compartidas/exportadas. Los cuerpos detallados y cuatro ejemplos de producto
están en **catalogo-service/CONTRATO_FRONTEND_CATALOGO.md**.

### Sin administrador

- GET `/api/productos`: 200, colección HATEOAS. Vacía inicialmente;
  consumir `_embedded?.productos ?? []`.
- GET `/api/productos?categoria=CONSOLA`: 200.
- GET `/api/productos/{id}`: 404 si todavía no existe; solo publica habilitados,
  incluidos los de stock cero.
- GET de una imagen ya subida: público. En una instalación vacía no hay archivos.
- POST `/auth/register/cliente`: registro público de CLIENTE si se desea verificarlo
  con credenciales propias; no concede ADMINISTRADOR.
- GET `/api/admin/productos` sin Bearer: 401.

### Login administrativo

```http
POST http://localhost:9090/auth/login
Content-Type: application/json
```

```json
{
  "email": "<correo propio usado en bootstrap>",
  "password": "<contraseña propia usada en bootstrap>"
}
```

Son marcadores, no credenciales para copiar literalmente. Esperado 200,
`{"token":"<JWT>"}`. En Postman usar Authorization > Bearer Token para las
peticiones siguientes. No enviar roles en headers ni JSON.

| Solicitud con Bearer ADMINISTRADOR | Resultado esperado |
| --- | --- |
| GET /api/v1/usuarios/me | 200, cuenta propia sin hash |
| GET /api/admin/productos | 200; incluye habilitados/deshabilitados y stock cero |
| POST /api/productos | 201 con un body válido del contrato y SKU propio libre |
| GET /api/admin/productos/{id} | 200 incluso deshabilitado; 404 inexistente |
| PUT /api/productos/{id} | 200 con body completo y stockEsperado leído; admite reactivar |
| POST /api/productos/imagenes/subir | 201 con imagenUrl |
| GET /api/productos/rawg/buscar?query=portal&page=1 | 200 solo con RAWG configurado y operativo |

Para la subida: Body > form-data, clave **archivo**, tipo **File**, seleccionar
JPEG/PNG/WebP válido de hasta 5 MiB. No establecer Content-Type manualmente:
Postman/navegador añade el boundary. Guardar la URL devuelta en el JSON de
producto; la subida sola no vincula el archivo a un producto.

Para comprobar visibilidad con datos de prueba: DELETE `/api/productos/{id}`
devuelve 204 y oculta, no borra; GET público pasa a 404, GET administrativo
sigue en 200. Reactivar con PUT completo, habilitado=true y stockEsperado
obtenido del GET administrativo. Estas pruebas **sí modifican datos** al ejecutarlas.

Con CLIENTE/VENDEDOR, las rutas administrativas, escrituras, subida y RAWG
deben devolver 403. Sin token válido, 401. No crear un VENDEDOR mediante SQL
ni alterar roles para esta comprobación: usar el mecanismo administrativo
existente cuando se requiera una cuenta adicional.

Sin RAWG_API_KEY, los endpoints RAWG devuelven el error controlado 503
RAWG_NO_CONFIGURADO; eso no es fallo de instalación del CRUD. Una clave
rechazada, cuota agotada o fallo de red también impide RAWG sin afectar al
catálogo local. RAWG siempre requiere ADMINISTRADOR, aun para buscar.

## 7. React/Vite

- Consumir exclusivamente Gateway: por ejemplo
  `VITE_API_BASE_URL=http://localhost:9090`.
- Abrir Vite desde `http://localhost:5173` o `http://127.0.0.1:5173`.
  Otro puerto/origen no está permitido. `localhost` no sirve para acceso
  remoto desde el equipo de otro integrante: cada uno ejecuta su backend local.
- CORS único en SecurityConfig de Gateway: GET/POST/PUT/DELETE/OPTIONS;
  Authorization y Content-Type; sin cookies, maxAge 600.
- Usar Authorization: Bearer para operaciones protegidas, JSON para login/CRUD,
  FormData con archivo para subir. No usar credentials:include ni mode:no-cors.
- OPTIONS válido se procesa antes de exigir JWT. Eso no autoriza el POST real:
  las reglas ADMINISTRADOR permanecen en Gateway y directamente en Catálogo.
- El panel usa GET `/api/admin/productos` y `/api/admin/productos/{id}`;
  la tienda usa los GET públicos.
- RAWG_API_KEY/JWT_SECRET/contraseña MySQL jamás son variables VITE_.
- **Limitación vigente:** PATCH de perfil existe en backend, pero PATCH no está
  en la lista CORS. La edición de perfil cross-origin requeriría una tarea
  posterior; no bloquea login, GET de perfil ni el PUT de productos.

El equipo informó OPTIONS/GET desde localhost:5173 validados previamente.
Esta auditoría no repitió HTTP ni ejecutó React. En el nuevo equipo verificar
en DevTools un GET público, login JSON, un POST con Bearer y una subida FormData:
preflight exitoso cuando corresponda y respuesta real legible sin error CORS.

## 8. Problemas habituales y puntos pendientes

| Síntoma | Comprobación / solución |
| --- | --- |
| Java ausente o versión incorrecta | JDK 21, JAVA_HOME y PATH en una consola nueva; mvnw.cmd no instala Java. |
| Wrapper no descarga / tarda demasiado | Acceso HTTPS a Maven Central, proxy y caché local. El primer arranque puede superar 180 s; esperar el build, detener lo abierto y reintentar con la caché preparada. No iniciar otra copia sobre puertos ocupados. |
| MySQL no acepta TCP | Servicio MySQL iniciado en 127.0.0.1:3306; el lanzador no lo inicia. |
| Access denied | Contraseña de checkpoint_app, host real de cuenta con CURRENT_USER y GRANT en ambas bases. No usar root en el lanzador. |
| Public Key Retrieval/SSL de Connector/J | Los ejemplos no añaden allowPublicKeyRetrieval ni configuran certificados. Revisar autenticación/TLS del MySQL local con su administrador; esta compatibilidad requiere prueba real. No cambiar a una contraseña débil ni desactivar seguridad global para ocultarlo. |
| Error schema-validation | Revisar DDL de users/productos/producto_imagenes, esquema seleccionado, tipos y permisos. No habilitar ddl-auto=update. |
| Error duplicate column/index al preparar esquema | Se ejecutó un paso para base existente o se repitió DDL. Inspeccionar SHOW CREATE; no ejecutar 01/06 sobre el CREATE actualizado. |
| SKU duplicados bajo concurrencia | Confirmar que se aplicó 04 y existe uq_producto_codigo; comprobarlo en MySQL, no solo en Java. |
| Bootstrap omitido | Ya existe email o ADMINISTRADOR. No sirve para elevar roles ni restablecer contraseñas. |
| Bootstrap no se ejecuta con doble clic | Es deliberado. Usar el procedimiento puntual de sección 4. |
| 401 después de reiniciar | JWT_SECRET cambió; repetir login. |
| 403 administrativo | La cuenta no es ADMINISTRADOR; revisar rol del usuario bootstrap. |
| 503 Gateway | Revisar servicio y registro en Eureka; esperar descubrimiento. No probar rutas de módulos heredados apagados. |
| Subida 500 | Permisos/espacio en carpeta local de imágenes y overrides heredados. |
| Imagen rota | URL pública guardada, archivo local presente, puerto Gateway; cambiar la base pública no reescribe URLs antiguas. |
| Error CORS | Origen exacto, método/header permitido, ambos servicios operativos. Postman no valida restricciones de navegador. |

### Hallazgos documentales / mejoras propuestas, no implementadas

- No hay instalador único ni SQL de usuario/bases, pero sí DDL de tablas y
  mecanismo bootstrap; la preparación manual de esta guía cubre ese hueco.
- El CREATE inicial de Catálogo no es final para SKU: requiere 04. A futuro
  convendría consolidar un DDL inicial final y actualizar los comentarios
  históricos, sin aplicar 06 a instalaciones nuevas.
- La última consulta de 03 puede señalar el índice compuesto RAWG legítimo.
  Se propone corregirla posteriormente; aquí se explica cómo interpretarla
  usando la comprobación específica de 05.
- El lanzador no comprueba ACL de imágenes por adelantado ni detiene hijos al
  fallar. La subida real y el cierre manual forman parte de la aceptación.
- No se detectó dependencia imprescindible de un secreto/ruta privada del
  autor. Quedan condicionantes del equipo: permisos Windows/MySQL, políticas
  de ejecución, TLS/autenticación MySQL y disponibilidad de descargas.

## 9. Detener los servicios

Ctrl+C en las terminales, en este orden: **Gateway → Catálogo → Identidad →
Eureka**. Si cmd pregunta si termina el trabajo por lotes, confirmar; luego
cerrar esas ventanas. Cerrar solo el lanzador **no detiene** servicios.
Si el arranque falló parcialmente, los hijos ya iniciados siguen abiertos:
detenerlos antes de reintentar. Comprobar que los puertos Java quedaron libres.
MySQL es un servicio independiente; detenerlo solo si no lo necesita otra tarea.

## 10. Qué se verificó y qué falta ejecutar

**Estático:** POM/wrappers, archivos versionados usados por el BAT/PS1,
variables heredadas, bootstrap y validaciones, entidades/DDL, inventario de SQL,
seguridad/CORS/rutas Gateway, rutas administrativas e implementación de imágenes.
La revisión no utilizó secretos ni configuraciones locales privadas.

**Aceptación pendiente en otro computador:** descarga inicial y compilación de
los cuatro módulos, ejecución del DDL con MySQL 8.4, cuenta/permisos efectivos,
arranque Hibernate validate, bootstrap real, login/roles, persistencia e índices,
escritura/lectura de imágenes, React/CORS y RAWG con una clave válida si se usará.
No se certifica una instalación ejecutada por haber inspeccionado el código.

Único archivo creado por esta auditoría: `INSTALACION_LOCAL_DESDE_CERO.md`.
No se modificaron implementación, SQL, lanzadores, configuración ni frontend.
