# Ventas — seguridad de las consultas y del checkout

`venta-service` atiende el [checkout](CHECKOUT_HTTP.md) y las [consultas de pedidos](CONSULTAS_PEDIDOS.md). Bajo `/api/ventas` se conservan las lecturas administrativas.

## JWT y respuestas

- Ventas ahora es un Resource Server stateless, como Identidad y Catálogo.
- `JWT_SECRET` es externo y obligatorio, sin valor privado ni default incorporado.
  Cargar el ejemplo como indica ARRANQUE_LOCAL.md. Usar el mismo texto en
  Identidad, Gateway y Ventas, con al menos 32 bytes UTF-8; no decodificar Base64.
- Solo HS256. Spring verifica firma y validez temporal con sus validadores
  estándar (incluida su tolerancia de reloj).
- Como Identidad, Ventas exige `exp`, `userId` numérico entero positivo dentro
  del rango Long y `role` exactamente ADMINISTRADOR, VENDEDOR o CLIENTE.
  Identidad emite el email como `sub`; no se usa un ID del body/header para autenticar.
- `role` se convierte a autoridad `ROLE_...`. No se aceptan X-Role ni X-User-Id
  como autenticación. No hay sesión, login de formulario ni HTTP Basic.
- Petición de negocio sin Bearer válido: **401**. Token válido de un rol sin
  permiso, o una operación bloqueada: **403**, incluso siendo ADMINISTRADOR.
- Son respuestas de los filtros estándar de Spring Security, anteriores al
  controlador. No se garantiza JSON ni el formato ApiErrorResponse; no intentar
  deserializar un cuerpo vacío. No se cambia el código de negocio para simular éxito.

Gateway sigue verificando firma/tiempo/rol con su configuración existente;
Ventas valida además identidad y expiración presente. En un GET administrativo
permitido por Gateway, Ventas también rechaza tokens sin esos claims.
Gateway puede rechazar antes una ruta bloqueada: no es una prueba del validador local.

## Lecturas autorizadas de ventas

El path interno y público es el mismo. Solo cambia el puerto, 9094 o 9090.

| Método y path | Permiso |
| --- | --- |
| GET /api/ventas | ADMINISTRADOR |
| GET /api/ventas/{id} | ADMINISTRADOR |
| GET /api/ventas/usuario/{usuarioId} | ADMINISTRADOR; nunca consulta propia de CLIENTE |
| GET /api/ventas/rango-fechas | ADMINISTRADOR |

No existe endpoint de cancelación en VentaController. Las demás rutas/métodos
de negocio, incluido PATCH o una futura ruta de cancelación, quedan denegados
por defecto hasta aprobar un contrato seguro. HEAD no se habilita como alias
de GET. Los parámetros de consulta no alteran la regla de autorización.

Excepciones sin datos comerciales: GET de OpenAPI
`/api/ventas/v3/api-docs` y sus subpaths; Swagger UI directo; OPTIONS para
preflight y despacho interno de errores. No se abre `/error` externamente.
El documento OpenAPI de Ventas incluye los pedidos. La consulta por correo y las escrituras retiradas de `/api/ventas` carecen de mapeo.
Un Bearer inválido puede producir 401 incluso en una ruta pública.

Gateway tiene la misma lista de lecturas administrativas y el bloqueo del
resto de `/api/ventas`, antes de sus excepciones genéricas de documentación.
No se cambia la seguridad ni las rutas de otros dominios.

## Matriz manual Postman — pendiente de ejecución

Usar dos valores de `baseUrl` y ejecutar cada fila en ambos:

- `http://127.0.0.1:9094`
- `http://127.0.0.1:9090`

Requisitos: versiones modificadas de Ventas y Gateway iniciadas por el usuario,
descubrimiento operativo y JWT vigentes de cuentas existentes de los tres roles
emitidos por Identidad con el mismo secreto. Obtener tokens por el flujo existente;
no recrear usuarios ni repetir la validación de la etapa 1.
Seleccionar **No Auth** para casos sin token y **Bearer Token** para los demás.

| Solicitud (añadir baseUrl) | Sin token | Bearer malformado/firma alterada | CLIENTE | VENDEDOR | ADMINISTRADOR |
| --- | --- | --- | --- | --- | --- |
| GET /api/ventas | 401 | 401 | 403 | 403 | 200, colección vacía |
| GET /api/ventas/1 | 401 | 401 | 403 | 403 | 404, base vacía |
| GET /api/ventas/usuario/1 | 401 | 401 | 403 | 403 | 200, colección vacía |
| GET /api/ventas/rango-fechas?fechaInicio=2026-01-01&fechaFin=2026-12-31 | 401 | 401 | 403 | 403 | 200, colección vacía |
| GET /api/ventas/cliente/email/cliente@example.com | 401 | 401 | 403 | 403 | 403 |
| POST /api/ventas | 401 | 401 | 403 | 403 | 403 |
| PUT /api/ventas/1 | 401 | 401 | 403 | 403 | 403 |
| DELETE /api/ventas/1 | 401 | 401 | 403 | 403 | 403 |
| PATCH /api/ventas/1 | 401 | 401 | 403 | 403 | 403 |

Para las pruebas negativas de POST/PUT puede conservarse este cuerpo histórico. La autorización rechaza la solicitud antes de procesar su contenido:

```json
{
  "usuarioId": 1,
  "costoDespacho": 0,
  "direccion": "Direccion de prueba de bloqueo",
  "detalles": [{"productoId": 1, "cantidad": 1, "precioUnitario": 1}]
}
```

PATCH puede usar `{}` y DELETE no requiere body. Las filas de la matriz para operaciones retiradas comprueban la denegación por defecto. Un error de negocio o un 5xx no demuestra el bloqueo esperado.

Casos adicionales centrados en este cambio:

1. GET /api/ventas con token expirado más allá de la tolerancia estándar:
   401 en ambos puertos.
2. GET /api/ventas sin token, agregando X-Role=ADMINISTRADOR y X-User-Id=1:
   401 en ambos; con token CLIENTE y esos headers: 403.
3. POST /api/ventas con JWT ADMINISTRADOR y body `{}`: 403 en ambos,
   no un error de DTO. Esto distingue el bloqueo previo al controlador.
4. GET /api/ventas/v3/api-docs sin token: esperar 200 en ambos puertos y comprobar que solo aparecen las lecturas vigentes bajo `/api/ventas`.
5. Si se dispone de una herramienta local de fixtures JWT de desarrollo:
   GET /api/ventas con role ADMINISTRADOR y firma de desarrollo válida, variando
   ausencia de exp, ausencia de userId, userId textual, cero, fraccionario o
   fuera de rango: 401 de Ventas, directo y a través de Gateway.
   Token con algoritmo distinto de HS256: 401. No compartir el secreto con
   páginas web de generación de tokens. Sin fixtures, registrar estos casos
   como pendientes; alterar solo el payload no prueba validación de claims.

No hay SQL nuevo ni cambios de permisos MySQL en esta etapa. Los casos anteriores
no deben crear ventas ni detalles. No repetir creación de base/tablas.

## Verificación realizada al implementar

Revisión estática de controladores, orden de matchers y validación JWT;
compilación de Ventas y Gateway sin iniciar servicios ni efectuar HTTP.
La validación funcional de seguridad queda pendiente de la matriz anterior.
