# JWT local en Catálogo

## Implementación

Se reutiliza el enfoque servlet de Identidad: Spring Security OAuth2 Resource
Server, NimbusJwtDecoder, SecretKeySpec con los bytes UTF-8 del secreto y HS256.
No se decodifica el secreto como Base64, no se genera otro y no hay llamadas
HTTP a Identidad por petición. Solo se añade el starter Resource Server,
con versiones administradas por Spring Boot 4.0.5.

Se valida firma, exp (obligatorio) y fechas mediante JwtValidators.createDefault
(incluido nbf si existe y la tolerancia temporal estándar de Spring Security).
El claim role debe ser un String con ADMINISTRADOR, VENDEDOR o CLIENTE.
Solo ese claim firmado se transforma en ROLE_*. No se confía en X-Role,
X-User-Role ni otras cabeceras de identidad.
Sin sesiones, login por formulario, HTTP Basic ni autenticación por cookies.
CSRF está deshabilitado porque se utiliza exclusivamente Bearer.

## Reglas y rutas revisadas

| Método / ruta | Acceso en Catálogo |
| --- | --- |
| GET /api/productos | Público |
| GET /api/productos?categoria=... | Público; query no altera autorización |
| GET /api/productos/{id} | Público |
| POST /api/productos | ADMINISTRADOR |
| POST /api/productos/{id}/stock/entradas | ADMINISTRADOR, también en Gateway |
| POST /api/productos/{id}/stock/salidas | ADMINISTRADOR, también en Gateway |
| PUT /api/productos/{id} | ADMINISTRADOR; edición local de productos |
| DELETE /api/productos/{id} | ADMINISTRADOR; baja lógica local e idempotente |
| POST, PUT, PATCH, DELETE /api/productos/** | ADMINISTRADOR, también subrutas |
| GET /api/productos/v3/api-docs y subrutas | Público |
| GET /swagger-ui.html y /swagger-ui/** | Público |
| OPTIONS /** | Público, sin conceder acceso al método de escritura |
| Resto | Denegado por defecto |

Se permite el despacho ERROR interno para conservar errores funcionales,
sin publicar una ruta externa /error. No hay controlador PATCH ni rutas
HTTP de agotar/habilitar en este módulo actualmente: la regla protege
esos métodos/subrutas sin crear funcionalidades. Rutas nuevas fuera del
espacio de productos quedan denegadas hasta revisar su política explícita.

Los filtros públicos de habilitado y la disponibilidad no cambian.
Un Bearer inválido enviado incluso a un GET público se rechaza con 401;
para probar acceso anónimo elegir No Auth y quitar Authorization heredado.
Los 401/403 los emite Spring Security (pueden tener cuerpo vacío y
WWW-Authenticate), no el GlobalExceptionHandler de errores de negocio.

## Secreto compartido y configuración local

El ejemplo contiene `jwt.secret=${JWT_SECRET}`, sin valor predeterminado.
El decoder también admite JWT_SECRET directamente cuando la configuración
privada anterior no define jwt.secret. Si falta el secreto o tiene menos de
32 bytes UTF-8, el servicio no debe arrancar. No imprimir ni versionar el valor.

Referencias revisadas, sin modificar otros módulos:

- Identidad: jwt.secret=${JWT_SECRET}.
- Gateway: spring.security.oauth2.resourceserver.jwt.secret-key=${JWT_SECRET}.
- Catálogo: jwt.secret=${JWT_SECRET}.

Usar exactamente el mismo valor de la fuente privada existente en los tres
procesos. Las variables se heredan al iniciar cada proceso; cambiar una terminal
no cambia el entorno de procesos que ya están ejecutándose. No generar una
clave para Catálogo ni regenerar la utilizada por los otros servicios.
Comprobar localmente que no hay overrides de jwt.secret/secret-key en archivos
privados, argumentos o variables como JWT_SECRET/SPRING_APPLICATION_JSON
que seleccionen valores distintos; no copiar secretos en capturas o reportes.

Para cargar en PowerShell el valor existente sin dejarlo en el historial:

```powershell
$secreto = Read-Host 'JWT_SECRET existente de Identidad y Gateway' -AsSecureString
$credencialJwt = [System.Net.NetworkCredential]::new('', $secreto)
$env:JWT_SECRET = $credencialJwt.Password
Remove-Variable secreto, credencialJwt
if ([System.Text.Encoding]::UTF8.GetByteCount($env:JWT_SECRET) -lt 32) {
    throw 'JWT_SECRET requiere al menos 32 bytes UTF-8'
}
```

Después seguir `PERSISTENCIA_LOCAL.md` para las variables de MySQL y el arranque
con el ejemplo explícito. No se cambian los BAT ni se sobrescriben secretos.
No hace falta Eureka para la prueba directa; para probar 9090, arrancar
Catálogo con Eureka habilitado y esperar su registro, siguiendo el arranque
existente. No usar --eureka.client.enabled=false para esa segunda fase.

La comprobación funcional de clave compartida consiste en obtener un JWT
ADMINISTRADOR real de Identidad y usar exactamente ese Bearer en 9091 y 9090.
Si 9091 acepta una petición administrativa válida y 9090 también, ambas
validaciones han aceptado la firma del token emitido por Identidad.
Un GET público sin token no demuestra coincidencia de secretos.
Al terminar y detener el proceso, puede retirarse la variable de esta terminal:
`Remove-Item Env:JWT_SECRET`.

## Pruebas Postman preparadas (no ejecutadas)

Variables: baseUrl, adminToken, clienteToken, vendedorToken, productoId
(producto habilitado existente), idInexistente (confirmado inexistente).
Obtener tokens mediante el login real de Identidad para cuentas de cada rol;
no escribir ni compartir sus contraseñas en esta documentación.

Primero baseUrl=http://localhost:9091; después repetir con
baseUrl=http://localhost:9090, Catálogo registrado en Eureka y Gateway disponible.

| Petición | Credenciales | Resultado esperado |
| --- | --- | --- |
| GET /api/productos | No Auth | 200 |
| GET /api/productos?categoria=CONSOLA | No Auth | 200 |
| GET /api/productos/{{productoId}} | No Auth | 200 |
| GET de ID oculto/inexistente | No Auth | 404 |
| POST /api/productos, JSON válido | No Auth | 401 |
| POST /api/productos, JSON válido | Bearer malformado/firma alterada | 401 |
| POST /api/productos, JSON válido | JWT expirado (más allá de tolerancia) | 401 |
| POST /api/productos, JSON válido | Bearer CLIENTE | 403 |
| POST /api/productos, JSON válido | Bearer VENDEDOR | 403 |
| POST /api/productos, JSON válido | Bearer ADMINISTRADOR | 201 |
| PUT/DELETE /api/productos/{{idInexistente}} | No Auth | 401 |
| PUT/DELETE /api/productos/{{idInexistente}} | CLIENTE o VENDEDOR | 403 |
| PUT/DELETE /api/productos/{{idInexistente}} | ADMINISTRADOR | 404 funcional |
| PATCH /api/productos/{{productoId}} | No Auth | 401 |
| PATCH /api/productos/{{productoId}} | CLIENTE o VENDEDOR | 403 |
| GET /api/productos | Bearer inválido | 401 |

Para PUT con administrador enviar el DTO completo válido:
`{"nombre":"Comprobación","categoria":"ACCESORIO","descripcion":"Sin cambios reales: ID inexistente","marca":"Marca","precio":1000,"stock":0,"stockEsperado":0,"enOferta":false,"habilitado":true}`.
Para comprobar la baja lógica con un producto de prueba existente, seguir
la sección DELETE de PRUEBAS_CATALOGO_POSTMAN.md. El stock se conserva y el producto puede reactivarse mediante PUT.

PATCH no está implementado: ADMINISTRADOR supera la autorización en 9091,
pero no garantiza éxito funcional (el handler heredado puede devolver error).
Gateway actualmente no permite PATCH: con administrador válido devuelve 403.
No se modificó Gateway para ampliar esos permisos.

Para comprobar que no se confía en cabeceras, repetir POST sin Bearer añadiendo
X-Role: ADMINISTRADOR y X-User-Role: ADMINISTRADOR: debe seguir en 401.
Con Bearer CLIENTE y esas cabeceras debe seguir en 403.
La verificación de tokens sin exp, con rol ajeno a los tres permitidos o
algoritmo diferente requiere tokens de prueba firmados de manera controlada;
el resultado esperado es 401. No se generan tokens ni claves en esta tarea.

El cuerpo válido del POST puede ser cualquiera de los cuatro ejemplos de
PRUEBAS_CATALOGO_POSTMAN.md. La prueba positiva 201 inserta un producto cuando
se ejecute manualmente; no se ejecutó durante esta implementación.
Compilar y revisar reglas no sustituye la validación HTTP de esta matriz.

Gateway incorpora exclusivamente las dos reglas POST de entradas/salidas de
stock para ADMINISTRADOR, manteniendo denegadas otras subrutas no autorizadas.
La ruta existente /api/productos/** ya las enruta a Catálogo. No cambian
Identidad ni permisos de CLIENTE/VENDEDOR. Véase PRUEBAS_STOCK_CATALOGO.md.
