# Roles definitivos y límites de la adaptación

Valores operativos únicos: `ADMINISTRADOR`, `VENDEDOR`, `CLIENTE`.
El claim se mantiene como `role` (String); Auth y Gateway lo convierten con
prefijo `ROLE_`. No se cambian HS256, userId, subject, expiración ni BCrypt.

Auth conserva el campo String de la entidad y la tabla actual. El registro
público persiste únicamente CLIENTE mediante una constante de servidor; no
recibe rol en el DTO. Login rechaza roles fuera de la lista y el emisor no
genera tokens con ellos. Ambos Resource Servers rechazan claims de rol
desconocidos, incluyendo listas y nombres heredados.

No se ejecuta SQL ni se crean cuentas privilegiadas. Las cuentas CLIENTE de
CheckPoint Store y sus tokens vigentes con userId válido siguen siendo compatibles.
Los tokens antiguos con ADMIN/OPERADOR requieren sustitución; no se traducen
automáticamente ni se actualizan cuentas de ferretería.

## Permisos del Gateway

| Rutas | Permiso |
| --- | --- |
| POST `/auth/login`, `/auth/register/cliente` | Público, contrato intacto |
| GET `/api/productos`, `/api/productos/{id}` | Público |
| POST productos, PUT/DELETE producto por ID | ADMINISTRADOR |
| POST `/api/v1/admin/usuarios` | ADMINISTRADOR |
| GET/PATCH `/api/v1/usuarios/me` | Autenticado con rol válido; Auth aplica userId propio |
| POST `/api/pedidos` y GET `/api/pedidos/mis-pedidos` | CLIENTE |
| GET `/api/pedidos/{id}` | CLIENTE para un pedido propio o ADMINISTRADOR |
| GET `/api/admin/pedidos` y `/api/admin/pedidos/{id}` | ADMINISTRADOR |
| Lecturas autorizadas de `/api/ventas` | ADMINISTRADOR |
| Rutas sin autorización explícita | Denegadas |

Las excepciones existentes de OPTIONS y documentación se conservan.
VENDEDOR no obtiene permisos de escritura ni administración de usuarios,
productos ni pedidos.

El checkout obtiene el comprador del JWT. Ventas comprueba la propiedad del pedido para las consultas de CLIENTE. Véanse [checkout](../ventas/CHECKOUT_HTTP.md), [consultas de pedidos](../ventas/CONSULTAS_PEDIDOS.md) y [lecturas administrativas de Ventas](../ventas/SEGURIDAD_VENTAS.md).

## Verificación

Compilar Auth (incluidas pruebas existentes, sin ejecutarlas) y Gateway.
Inspeccionar que los matchers específicos precedan al cierre general y que
se conserven claim `role`, prefijo `ROLE_` y registro exclusivo CLIENTE.

Las validaciones HTTP anteriores de Identidad no prueban esta nueva matriz.
Pendientes: repetir registro/login/perfil y catálogo anónimo; comprobar con
cuentas autorizadas futuras los permisos ADMINISTRADOR/VENDEDOR y sus rechazos.
Las comprobaciones HTTP por rol siguen pendientes en esta matriz.
