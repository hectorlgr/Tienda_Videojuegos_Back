# Pruebas de Identidad en Windows/PowerShell

## Estado y requisitos

Configuración preparada; **no se ha ejecutado SQL, iniciado servicios ni probado
HTTP**. Los comandos siguientes son para una ejecución posterior autorizada.

- JDK 21, `JAVA_HOME` correcto y `java` en PATH.
- PowerShell 5.1 o posterior, Git y acceso a Maven Central si faltan artefactos.
  Cada módulo incluye `mvnw.cmd`; no hace falta instalar Maven global.
- MySQL 8 con cliente MySQL o Workbench, y Postman (o Swagger en el navegador).
- Puertos libres: MySQL 3306, Eureka 8761, Auth 9096 y Gateway 9090.

POM revisados, sin cambios: Auth Boot 4.0.5/Cloud 2025.1.1; Gateway
Boot 3.3.5/Cloud 2023.0.3; Eureka Boot 4.0.6/Cloud 2025.1.1. Todos usan Java 21.
La interoperabilidad real entre estos módulos queda pendiente del arranque.

## Base exclusiva y usuario de aplicación (preparación manual posterior)

Nombre propuesto: `checkpoint_store_identidad`. Si ya existe y no está vacía,
detenerse y confirmar su origen; no reutilizar ninguna base de ferretería.

Un administrador de MySQL deberá:

1. Crear la base con `utf8mb4` y `utf8mb4_unicode_ci`.
2. Crear la tabla `users` utilizando el DDL de `auth-service/ESQUEMA_IDENTIDAD.md`.
3. Crear en Workbench > Administration > Users and Privileges un usuario local
   `checkpoint_app` con contraseña propia, aleatoria y no vacía. Restringir el
   host al computador local, no `%`. Confirmar el host de cuenta que corresponda
   a la conexión TCP local (`localhost` o `127.0.0.1`).
4. Conceder solo SELECT, INSERT, UPDATE y DELETE sobre esa base. No conceder
   administración global ni CREATE/ALTER/DROP. Auth no debe usar root.

Referencia SQL para el administrador, **no ejecutada en esta tarea**:

```sql
CREATE DATABASE checkpoint_store_identidad
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- Crear users según ESQUEMA_IDENTIDAD.md y el usuario desde Workbench.
-- Ajustar el host al usuario creado, sin ampliar permisos a otras bases:
GRANT SELECT, INSERT, UPDATE, DELETE
  ON checkpoint_store_identidad.* TO 'checkpoint_app'@'localhost';
```

JPA usa `validate`, nunca `create`, `create-drop` ni `update`; SQL init está
deshabilitado. Una tabla inexistente o incompatible impedirá el arranque:
corregir el esquema nuevo manualmente, no cambiar a actualización automática.

## Archivos y variables

Los archivos locales ya están preparados en este equipo y están ignorados.
En otro equipo, desde la raíz del repositorio, copiar **solo si faltan**:

```powershell
if (!(Test-Path 'auth-service/src/main/resources/application.properties')) {
    Copy-Item 'auth-service/src/main/resources/application.properties.example' 'auth-service/src/main/resources/application.properties'
}
if (!(Test-Path 'api-gateway/src/main/resources/application.yml')) {
    Copy-Item 'api-gateway/src/main/resources/application.yml.example' 'api-gateway/src/main/resources/application.yml'
}
git check-ignore -v auth-service/src/main/resources/application.properties api-gateway/src/main/resources/application.yml
```

No usar `git add -f` para estas configuraciones. Los `.example` son públicos y
solo contienen referencias a variables. Spring no carga automáticamente `.env`.

En una consola PowerShell padre, situada en la raíz de `checkpoint-store-backend`:

```powershell
$repo = (Get-Location).Path
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/checkpoint_store_identidad'
$env:DB_USERNAME = 'checkpoint_app'
$cred = Get-Credential -UserName $env:DB_USERNAME -Message 'Contraseña del usuario MySQL exclusivo de CheckPoint Store'
$env:DB_PASSWORD = $cred.GetNetworkCredential().Password
$env:EUREKA_URL = 'http://127.0.0.1:8761/eureka/'

# Generar UNA VEZ por sesión y compartir por herencia con Auth y Gateway.
# 32 bytes criptográficamente aleatorios, representados como 44 caracteres ASCII.
# Ambos servicios usan esos 44 bytes UTF-8 literalmente; NO decodifican Base64.
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
if ([Text.Encoding]::UTF8.GetByteCount($env:JWT_SECRET) -lt 32) {
    throw 'JWT_SECRET insuficiente'
}
```

No imprimir, guardar en archivos versionados ni exportar estos valores a
colecciones Postman. El secreto no se ha generado durante esta tarea.
Las variables viven en la consola y sus procesos hijos. No configurar cada
servicio con un secreto generado independientemente. Al regenerarlo, reiniciar
ambos servicios y volver a iniciar sesión: los tokens anteriores no servirán.

Si MySQL usa otro puerto/host o exige TLS, el desarrollador deberá proporcionar
el JDBC URL y certificados adecuados. No deshabilitar TLS ni habilitar
`allowPublicKeyRetrieval` indiscriminadamente para ocultar errores de conexión.
Revisar también variables Spring preexistentes que puedan sobrescribir
datasource, puerto, secreto o `ddl-auto`.

## Arranque posterior, sin Docker

Después de preparar y comprobar manualmente la base nueva y las variables,
iniciar desde la **misma consola padre** en este orden. Cada ventana heredará
las variables, incluido el mismo JWT_SECRET.

1. Eureka:

```powershell
Start-Process powershell.exe -WorkingDirectory "$repo\eureka-server" -ArgumentList '-NoExit', '-Command', '.\mvnw.cmd "-Dmaven.test.skip=true" spring-boot:run'
```

Esperar el arranque y abrir `http://127.0.0.1:8761/`. Eureka funciona standalone:
no se registra a sí mismo ni busca otros servidores.

2. Auth:

```powershell
Start-Process powershell.exe -WorkingDirectory "$repo\auth-service" -ArgumentList '-NoExit', '-Command', '.\mvnw.cmd "-Dmaven.test.skip=true" spring-boot:run'
```

Esperar validación del esquema y registro `AUTH-SERVICE` en Eureka, puerto 9096.

3. Gateway:

```powershell
Start-Process powershell.exe -WorkingDirectory "$repo\api-gateway" -ArgumentList '-NoExit', '-Command', '.\mvnw.cmd "-Dmaven.test.skip=true" spring-boot:run'
```

Esperar registro `API-GATEWAY`, puerto 9090, y refresco del catálogo Eureka.
Todos escuchan solo en loopback. Los clientes publican 127.0.0.1 en Eureka.
No usar `build-all.bat`, `clean-all.bat` ni `iniciar_servicios.bat`: para esta
prueba bastan los tres comandos Maven anteriores.

El Gateway usa rutas explícitas; el locator automático está deshabilitado para
evitar aliases por nombre de servicio. Todas las rutas heredadas se conservan,
pero sus servicios no necesitan arrancarse para probar Identidad.

## Pruebas con Postman o Swagger

Base principal: `http://127.0.0.1:9090`. Para repetir la protección local de Auth,
usar `http://127.0.0.1:9096` con exactamente las mismas rutas.
Postman: Body > raw > JSON, `Content-Type: application/json`.

Swagger Gateway: `http://127.0.0.1:9090/doc/swagger-ui.html`, seleccionar
**8. Auth Service**. OpenAPI: `/auth/v3/api-docs`.
Swagger directo Auth: `http://127.0.0.1:9096/swagger-ui/index.html`.
Las documentaciones de otros servicios pueden fallar porque no están iniciados.

1. Sin Bearer, `POST /auth/register/cliente` con `nombre`, `email` y `password`.
   Usar un correo ficticio exclusivo de prueba, por ejemplo `cliente-a@example.test`,
   y una contraseña propia de prueba no publicada (máximo 72 bytes UTF-8).
   Esperado: **201** con `message`, sin perfil duplicado en Usuarios.
2. `POST /auth/login` con ese correo y contraseña. Esperado: **200** con
   `{"token":"..."}`. Con contraseña incorrecta: **401**.
3. Copiar el token a Authorization > Bearer Token en Postman, o Authorize en
   Swagger. No enviar el token ni las contraseñas a decodificadores web externos.
4. `GET /api/v1/usuarios/me`: **200**, solo `id`, `nombre`, `email`, `telefono`,
   `direccion`, `region`, `comuna`. Los contactos nuevos serán inicialmente null.
5. `PATCH /api/v1/usuarios/me`, por ejemplo:

```json
{
  "telefono": "+56 9 1234 5678",
  "direccion": "Dirección ficticia de prueba",
  "region": "Región Metropolitana",
  "comuna": "Santiago"
}
```

Esperado: **200** con perfil actualizado. Repetir GET para confirmar.
Campos omitidos o null no cambian; `""` limpia el campo opcional y guarda NULL.
Límites: teléfono 30, dirección 255, región/comuna 100 unidades String Java.

Casos negativos:
- Sin token, token alterado, expirado o sin userId válido: **401** en GET/PATCH.
- Nombre omitido/contraseña demasiado larga en registro: **400**.
- Correo duplicado: **400**, sin sobrescribir la cuenta.
- PATCH con longitud excesiva o JSON malformado: **400**, sin cambios parciales.
- Cuenta inexistente con token válido: **404**; no borrar cuentas como parte
  de estas pruebas iniciales para fabricar ese escenario.
- Registro administrativo: no debe crear cuentas; la ruta está cerrada.

### Aislamiento entre cuentas

Registrar B con otro correo ficticio y guardar su token por separado.
1. GET con token A y token B devuelve IDs distintos.
2. Con token A, enviar PATCH con un teléfono diferente y añadir al JSON
   `"id": <ID de B>`, `"email": "otro@example.test"`, `"role": "ADMIN"`.
   Esos campos no pertenecen al DTO: si se ignoran, solo cambia el teléfono de A;
   si el deserializador los rechaza, habrá 400 y ningún cambio.
3. GET con B debe conservar su perfil intacto. Nombre, correo y rol de A no cambian.
4. Repetir GET/PATCH con `?userId=<ID de B>` y cabecera `X-User-Id: <ID de B>`:
   nunca seleccionan B; la única identidad válida es el claim firmado de A.
5. `/api/v1/usuarios/<ID de B>` no es un endpoint habilitado. No debe devolver
   ni modificar el perfil, aunque el solicitante tenga token válido.

### Comprobación MySQL (solo lectura, después de las pruebas)

Conectar mediante Workbench como usuario de aplicación y comprobar primero:

```sql
SELECT DATABASE(), CURRENT_USER();
SELECT id, nombre, email, telefono, direccion, region, comuna
FROM checkpoint_store_identidad.users
WHERE email IN ('cliente-a@example.test', 'cliente-b@example.test');
```

Comparar con GET antes/después del PATCH, especialmente la cuenta B.
No seleccionar ni copiar `password`; no hacen falta consultas sobre hashes.

## Bloqueos y diagnóstico

- Faltan confirmar instalación/estado MySQL, base nueva, esquema, usuario,
  contraseña y disponibilidad de puertos; nada de ello se ha creado o iniciado.
- Sin variables, Auth/Gateway deben fallar al arrancar, no usar secretos por defecto.
- `Schema-validation` indica esquema ausente/incompatible: revisar solo la base nueva.
- 503 del Gateway: comprobar AUTH-SERVICE en Eureka y esperar descubrimiento.
- 401 con token recién emitido: comprobar mismo JWT_SECRET en ambos procesos
  sin imprimirlo, URL de login utilizada y reloj del equipo.
- Ventas sigue dependiendo de usuario-service: estas pruebas no validan compras.
- La configuración de rutas y seguridad se ha inspeccionado estáticamente;
  no hay todavía resultado de HTTP, JDBC ni descubrimiento en ejecución.

Al finalizar una futura sesión, detener cada proceso con Ctrl+C. No eliminar
datos ni ejecutar limpiezas globales. Retirar las variables de la consola padre:

```powershell
Remove-Item Env:JWT_SECRET, Env:DB_PASSWORD -ErrorAction SilentlyContinue
Remove-Variable cred, bytes -ErrorAction SilentlyContinue
```
