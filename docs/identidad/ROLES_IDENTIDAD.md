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
| Inventario y CRUD heredado `/api/usuarios/**` | ADMINISTRADOR |
| GET/PATCH `/api/v1/usuarios/me` | Autenticado con rol válido; Auth aplica userId propio |
| GET `/api/pedidos/usuario/{idUsuario}` | ADMINISTRADOR o VENDEDOR |
| POST `/api/pedidos`, PUT `/{id}/estado`, PUT `/{id}/cancelar` | ADMINISTRADOR |
| Ventas, Despachos y Promociones heredados | ADMINISTRADOR provisionalmente |
| Rutas restantes, incluyendo Reseñas/Soporte retirados | Denegadas |

Las excepciones existentes de OPTIONS y documentación se conservan.
VENDEDOR no obtiene permisos de escritura ni administración de usuarios,
inventario, productos u órdenes. No se han creado administradores ni vendedores.

## Bloqueo: compras y pedidos propios del CLIENTE

No basta con cambiar nombres de roles para habilitarlos de forma segura:

- `VentaController.procesarVenta()` obtiene `usuarioId` del JSON.
- GET de ventas por ID, usuario, correo, rango o listado no verifica propietario.
- `PedidoController.obtenerPorUsuario()` recibe el ID de la URL.
- `PedidoService.cancelarPedido()` comprueba estado, pero no propietario autenticado.
- Usuarios heredado no implementa `/api/usuarios/me`; se elimina su excepción
  autenticada. El perfil propio real sigue en `/api/v1/usuarios/me`.

Por eso CLIENTE no accede temporalmente por Gateway a Ventas/Pedidos heredados,
incluida la compra. Es una restricción explícita pendiente de resolver, no una
implementación completa del permiso final de compras autenticadas y pedidos propios.
No se compara el ID de Auth con el ID independiente de Usuarios como atajo.

El acceso no administrativo a Despachos/Promociones y las consultas de Ventas
por VENDEDOR no tienen un contrato acordado aquí: no se conceden permisos nuevos.
Se conserva únicamente acceso administrativo provisional donde ya existía.

## Dependencias y seguimiento

No se encontraron comparaciones operativas de ADMIN/OPERADOR en los demás
microservicios. Sí se requiere adaptar posteriormente Ventas y Pedidos para
usar la identidad validada y comprobar propiedad. Despachos también expone
consultas por pedido/estado y necesita un contrato de acceso propio definido.
Los accesos directos a servicios heredados y sus llamadas internas siguen
sin la protección local equivalente a Auth: el Gateway no corrige ese riesgo.

La entidad User no se modifica por restricción de alcance; su comentario
histórico menciona ADMIN/OPERADOR, pero no impone ni valida esos valores.
No se cambian rutas de descubrimiento, tablas ni otros servicios.

## Verificación

Compilar Auth (incluidas pruebas existentes, sin ejecutarlas) y Gateway.
Inspeccionar que los matchers específicos precedan al cierre general y que
se conserven claim `role`, prefijo `ROLE_` y registro exclusivo CLIENTE.

Las validaciones HTTP anteriores de Identidad no prueban esta nueva matriz.
Pendientes: repetir registro/login/perfil y catálogo anónimo; comprobar con
cuentas autorizadas futuras los permisos ADMINISTRADOR/VENDEDOR y sus rechazos.
No hay todavía cuentas de esos roles ni afirmación de validación HTTP de ellos.
