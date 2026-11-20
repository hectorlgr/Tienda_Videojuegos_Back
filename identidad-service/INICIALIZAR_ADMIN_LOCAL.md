# Inicialización puntual del primer administrador

Implementación preparada, **no ejecutada**. No se ha creado ninguna cuenta.
Solo para el MySQL local nuevo de CheckPoint Store, nunca para producción.

## Activación y controles

Se usa un ApplicationRunner de Spring Boot con dos condiciones simultáneas:
- Perfil activo `bootstrap-admin`.
- Variable `BOOTSTRAP_ADMIN_ENABLED=true` (propiedad `bootstrap.admin.enabled`).

Por defecto no se activa: no se han añadido esas opciones a ninguna
configuración operativa ni ejemplo. El runner exige además:
- Ningún otro perfil activo.
- `server.address=127.0.0.1`.
- JDBC MySQL en localhost/127.0.0.1, base `checkpoint_store_identidad`.
- `spring.jpa.hibernate.ddl-auto=validate`.

Esos controles evitan usos accidentales; quien controla el proceso y sus
variables debe mantenerlos fuera de despliegues de producción. No son una
prueba de que cualquier servidor en loopback sea un entorno de desarrollo.

La cuenta se construye con rol fijo ADMINISTRADOR, nombre y correo; contraseña
BCrypt mediante el HashService existente. Reutiliza las validaciones del DTO
de registro (correo válido, nombre y contraseña no vacíos), longitudes de
almacenamiento y límite de contraseña de 72 bytes UTF-8.

Si el correo existe, no se cambia nada, sea CLIENTE o cualquier otro rol.
Si ya existe un ADMINISTRADOR, tampoco crea otra cuenta. No permite elevar
clientes ni restablecer contraseñas. El guardado es transaccional y UNIQUE
protege correos duplicados; ante una colisión concurrente se aborta sin
sobrescribir. Ejecutar una sola instancia: la comprobación de administrador
existente no es un bloqueo distribuido entre inicializaciones con correos distintos.

No hay endpoint administrativo. Las credenciales se leen solo de las variables
de entorno indicadas; no se pasan por JSON ni argumentos de línea de comandos.
Los mensajes propios del mecanismo no incluyen correo, contraseña ni hash.

## Preparación manual posterior

1. Revisar esta implementación antes de ejecutarla.
2. Tener la base y tabla nuevas ya preparadas y las variables DB_URL,
   DB_USERNAME, DB_PASSWORD y JWT_SECRET configuradas según
   `../PRUEBAS_IDENTIDAD_LOCAL.md`. Mantener el mismo JWT_SECRET del Gateway.
3. Detener Auth normal para liberar el puerto 9096. No iniciar otras instancias
   de bootstrap. No hace falta arrancar Usuarios ni Ventas.
4. Confirmar que no hay DEBUG/TRACE de SQL/bindings ni transcripción de secretos
   habilitados. La configuración normal no imprime SQL.

Desde una consola PowerShell dedicada, en la raíz del repositorio:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'bootstrap-admin'
$env:BOOTSTRAP_ADMIN_ENABLED = 'true'
$env:BOOTSTRAP_ADMIN_EMAIL = Read-Host 'Correo del primer administrador'
$env:BOOTSTRAP_ADMIN_NOMBRE = Read-Host 'Nombre del primer administrador'
$secret = Read-Host 'Contraseña propia del administrador (máximo 72 bytes UTF-8)' -AsSecureString
$credential = New-Object System.Net.NetworkCredential('', $secret)
$env:BOOTSTRAP_ADMIN_PASSWORD = $credential.Password

try {
    Push-Location identidad-service
    try {
        .\mvnw.cmd "-Dmaven.test.skip=true" spring-boot:run
    } finally {
        Pop-Location
    }
} finally {
    Remove-Item Env:BOOTSTRAP_ADMIN_ENABLED, Env:BOOTSTRAP_ADMIN_EMAIL, Env:BOOTSTRAP_ADMIN_NOMBRE, Env:BOOTSTRAP_ADMIN_PASSWORD, Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
    Remove-Variable credential, secret -ErrorAction SilentlyContinue
}
```

El runner se ejecuta una vez al arrancar, no en cada petición. Al ver
“Inicialización administrativa completada” u “Inicialización omitida”, detener
Auth inmediatamente con Ctrl+C. El `finally` retira las variables temporales;
verificar que también terminó el proceso hijo Java. Si se interrumpe la consola
antes del finally, cerrar esa consola y detener el proceso hijo.

No guardar la contraseña en archivos, historial, colecciones Postman o Git.
Las variables de entorno son texto en la memoria del proceso; usar una cuenta
Windows confiable y cerrar la consola dedicada al terminar.

## Verificación posterior

- Reiniciar Auth normalmente **sin** perfil ni habilitación bootstrap.
- Login con Postman mediante `/auth/login`: esperar 200 y JWT con
  `role=ADMINISTRADOR` y el userId persistido. No publicar el token.
- GET `/api/v1/usuarios/me`: esperar 200 y los datos de esa cuenta sin hash.
- En MySQL local, inspeccionar solo id, nombre, email y role mediante Workbench,
  sin seleccionar/exportar password. Confirmar que las cuentas CLIENTE
  existentes conservan sus datos y roles.
- Una repetición deliberada con el mismo correo debe informar inicialización
  omitida; no debe cambiar la contraseña ni duplicar la cuenta.
- Si se usa por error un correo CLIENTE, debe omitir la operación, nunca elevarlo.
- El registro público sigue fijando CLIENTE aunque se envíe un campo role.

No se han realizado estas comprobaciones HTTP/MySQL. Los errores de ejecución
son deliberadamente genéricos para no divulgar SQL o credenciales; antes de
reintentar revisar el esquema y los valores externos sin publicarlos.
