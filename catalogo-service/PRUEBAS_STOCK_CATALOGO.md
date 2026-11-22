# Movimientos administrativos de stock

Implementación preparada; no se ejecutaron SQL, servicios ni peticiones de
movimiento durante esta tarea. Usar productos de prueba existentes al validar.

## Contratos HTTP

- POST `/api/productos/{id}/stock/entradas`
- POST `/api/productos/{id}/stock/salidas`

Authorization: Bearer {{adminToken}}, Content-Type: application/json.
Body en ambos casos:

```json
{"cantidad": 2}
```

Cantidad Integer obligatoria > 0. Se rechazan fracciones, null, cero, negativos
y números fuera del rango Integer. Respuesta 200: ProductoResponseDto completo,
incluyendo stock, precioEfectivo y disponible. Solo se modifica stock.

| Caso | Respuesta |
| --- | --- |
| Cantidad válida y operación aplicable | 200 |
| Cantidad inválida o JSON mal formado | 400 |
| Producto inexistente con entrada válida | 404 |
| Stock insuficiente | 409 |
| Entrada que excede Integer.MAX_VALUE (2147483647) | 409 |
| Conflicto de bloqueo traducido por JPA/Spring | 409 |
| Sin JWT válido | 401 |
| CLIENTE o VENDEDOR | 403 |

Ambas rutas exigen ADMINISTRADOR en Catálogo y Gateway. Gateway recibió solo
esos dos matchers POST; no se autorizaron otras rutas.
Los administradores pueden ajustar también productos ocultos: estas operaciones
no son compras. Nunca habilitan/deshabilitan productos ni modifican precio,
oferta, categoría, rawgId u otros atributos. Al llegar a cero el producto puede
seguir visible. No se llama a Inventario.

## Concurrencia y cambio obligatorio del PUT

El repositorio usa @Lock(PESSIMISTIC_WRITE) sobre la consulta por ID. En MySQL
con InnoDB la fila se bloquea para escritura hasta terminar la transacción.
La lectura bloqueante obtiene el stock sobre el que se calcula cada movimiento.
Incremento y descuento tienen @Transactional; se valida cantidad antes de la
lectura y suficiencia/rango bajo el bloqueo, antes de cambiar stock.
El incremento calcula en long para evitar overflow antes de convertir a Integer.

El mismo bloqueo se usa en PUT, DELETE y los métodos internos heredados de
habilitación/deshabilitación. El helper de alta rechaza entidades con ID para
evitar que se use como un merge alternativo sin bloqueo. Las altas HTTP
siguen generando ID nuevo. No hay llamadas HTTP dentro de estas transacciones.

**PUT /api/productos/{id} ahora exige stockEsperado**, además del stock nuevo.
Si actual != esperado, devuelve 409 antes de aplicar cualquier campo. El
bloqueo por sí solo no detectaría un formulario cargado antes de un movimiento.
Ejemplo conceptual: leído=5; nuevo stock deseado=8; enviar stockEsperado=5 y
stock=8 con el resto del DTO completo. Ver PRUEBAS_ACTUALIZACION_CATALOGO.md.
Omitir stockEsperado/null/negativo da 400. No afecta al POST de creación.

Esto protege el saldo frente a formularios cuyo stock quedó desactualizado;
no es un versionado general de producto ni detecta cambios de precio/visibilidad
si el stock sigue igual, o movimientos que vuelven al mismo saldo.
El alcance no incorpora versiones, reservas ni historial de movimientos.

La lógica local descontarStock(id, cantidad) está separada del controlador
para futura integración autenticada de Ventas. No hay acceso CLIENTE ni
credenciales de servicio nuevas. La futura compra deberá definir disponibilidad,
precios oficiales y política de reintentos. Los movimientos actuales NO son
idempotentes: repetir un POST exitoso aplica otra entrada/salida. Ante un timeout
de red no repetir a ciegas; comprobar el estado antes de decidir.

## Pruebas Postman pendientes

Primero baseUrl=http://localhost:9091; repetir con http://localhost:9090.
Usar productoId existente y guardar la respuesta inicial para comparar los
campos que no son stock. Las pruebas positivas modificarán ese producto
cuando el usuario las ejecute.

1. GET del producto habilitado: anotar stock S.
2. POST entradas cantidad=3: 200 y stock=S+3.
3. POST salidas cantidad=2: 200 y stock=S+1.
4. POST salidas cantidad=stockActual: 200, stock=0, habilitado sin cambios.
   GET público debe seguir mostrándolo si estaba habilitado.
5. Con stock=0, POST salidas cantidad=1: 409, sin cambios.
6. Probar cantidad omitida/null/0/-1/1.5: 400. ID inexistente y cantidad=1: 404.
7. Probar sin token, token inválido o expirado: 401. CLIENTE/VENDEDOR: 403.
   Repetir ambas rutas, en ambos puertos. X-Role: ADMINISTRADOR no sustituye JWT.
8. Repetir un movimiento sobre un producto de prueba deshabilitado: 200,
   conserva habilitado=false. GET público sigue en 404; comparar la respuesta
   del movimiento con el estado administrativo conocido.
9. Si se decide comprobar el límite, preparar un producto de prueba mediante
   PUT válido con stock=Integer.MAX_VALUE y stockEsperado correcto. Entrada=1:
   409 sin overflow ni cambios. Esta preparación no se ejecutó.
10. Probar POST a otra subruta no autorizada de Gateway: no debe concederse
    permiso por las dos reglas nuevas (con JWT válido se espera 403).

## Pruebas de concurrencia pendientes

Enviar las solicitudes simultáneamente, no una después de recibir la otra.
El Runner secuencial no demuestra concurrencia. Se pueden disparar dos
pm.sendRequest desde un script de Postman, esperando ambas respuestas antes
de consultar el saldo, o utilizar dos clientes simultáneos.

- Dos entradas (+3 y +4), stock inicial=5: ambas 200, saldo final=12.
- Dos salidas de 4, stock inicial=5: una 200 y otra 409 por insuficiencia,
  saldo final=1 (en ausencia de conflictos de bloqueo).
- PUT con stockEsperado=5/stock=10 frente a salida de 4, saldo inicial=5:
  si PUT bloquea primero, ambos pueden terminar 200 y saldo=6; si salida
  termina primero, salida=200, PUT=409 y saldo=1. Nunca aceptar el formulario
  obsoleto después de la salida.
- Dos PUT con esperado=5 y nuevos saldos diferentes de 5: solo uno debe
  tener éxito; el segundo detecta el nuevo saldo y devuelve 409.
- DELETE frente a entrada de 3: después de completar ambos, producto oculto
  y stock inicial+3. El movimiento no debe revertir la baja lógica.

Ante un 409 de bloqueo, la operación se revierte; revisar el mensaje y el
estado final. Los resultados anteriores presuponen operaciones normales sin
timeout/deadlock. La compilación y revisión estática no prueban los bloqueos
reales: comprobarlos con MySQL 8.4 y Spring Boot al ejecutar esta matriz.
