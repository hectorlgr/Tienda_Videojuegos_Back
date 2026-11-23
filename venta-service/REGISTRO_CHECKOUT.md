# Registro durable de checkout — Etapa 4.6B.3A

## API interna

`SolicitudPedidoService.registrarConCheckout(compradorId, claveIdempotencia, items, entrada)`
recibe identidad confiable (futuro JWT), UUID, productos/cantidades y
`DatosCheckoutSolicitud.Entrada`. No recibe contexto servidor, tarifa, precios,
clasificación física/digital ni UUID de stock del llamador.

Retorna `SolicitudPedidoInterna`, ahora también con checkout completo,
hashCheckout, codigoCompensacion y resultado. Buscar, listar y reclamar usan la
misma vista inmutable; tras reinicio se reconstruye desde MySQL, sin caché local.

## Política realmente disponible

La inspección encontró la regla digital de despacho cero y la obligación de
entrega para físicos/mixtos. No encontró tarifa física aprobada, versiones de
condiciones aprobadas ni fuente local confiable de composición por producto.
El costo recibido del comprador en ventas heredadas NO es una política utilizable.
La presencia/ausencia de dirección tampoco acredita composición.

Se define el puerto backend `PoliticaCheckout.preparar(solicitud, entrada)`.
Su implementación debe ser un bean Spring interno de lógica/configuración local
confiable, que:

1. Acredite la composición completa de los productos, sin confiar en React.
2. Compruebe la versión de condiciones aceptada.
3. Determine la política aprobada y su aplicabilidad a la dirección/composición.
4. Devuelva `Preparacion(composicion, contexto)` con costo y versiones explícitos.
5. Rechace falta de configuración/evidencia mediante `PreparacionCheckoutException`.
   No debe hacer HTTP, descontar stock ni persistir otra intención.

**No se instala una política inventada ni un bean permisivo.** Sin ese bean,
las solicitudes NUEVAS, incluso las supuestamente digitales cuya composición no
puede acreditarse, fallan con `POLITICA_NO_CONFIGURADA`, antes de insertar nada.
El registro atómico está implementado; habilitar nuevas preparaciones reales
requiere aportar esa política autorizada. La aplicación puede arrancar sin ella.

`Preparacion` exige contexto y composición conocidos y despacho cero para DIGITAL.
El servicio exige entrega para FISICA/MIXTA y rechaza despacho mayor al límite
aceptado. No conoce todavía precios; la validación del total final sigue siendo
responsabilidad de la materialización con snapshot Catálogo.

## Hashes y validación

- Stock: se conserva exactamente el algoritmo previo de `hash_solicitud`.
- Checkout: `HashCheckout.calcular` implementa el sobre v1 definido en
  SOLICITUDES_PEDIDO.md, con `entrada`, `solicitud`, `versionContratoCheckout`.
- Claves lexicográficas en TODOS los objetos, productos ordenados por ID,
  enteros decimales sin espacios, booleanos/null literales y UTF-8.
- SHA-256 hexadecimal minúscula. No se usa el texto JSON devuelto por MySQL.
- Textos sin trim, case folding ni normalización Unicode; comillas y barra
  inversa escapadas, controles mediante `\u00xx` minúsculo, Unicode conservado.
  Se rechazan sustitutos UTF-16 aislados para evitar hashes de texto reemplazado.
- Incluye entrega/referencia, límite y aceptación/versionCondiciones; excluye
  contexto servidor, fechas, lease, resultado y UUID de operación.
- El contrato tipado exige 1–100 productos únicos, IDs/cantidades positivos,
  límite Long positivo, aceptación true, entrega completa si está presente,
  textos no blancos y longitudes compatibles con SQL 04.
- Entrega null y referencia null se serializan explícitamente; `JsonInclude.ALWAYS`
  conserva esos miembros exigidos por el schema. JSON inválido no es entrada
  pública: no se añade deserializador HTTP.

## Transacciones e idempotencia

1. Validar y canonicalizar entrada; calcular ambos hashes.
2. Leer la identidad comprador/clave en una transacción independiente.
3. Si existe, comparar contenido completo y ambos hashes; devolver los originales.
   Esto sucede ANTES de consultar política vigente, incluso si fue retirada.
4. Preparar contexto confiable fuera de la transacción de escritura.
5. REQUIRES_NEW: segunda lectura; si aún no existe, un INSERT con solicitud,
   ambos hashes, checkout completo, UUID nuevo y PENDIENTE. Flush y commit.
6. Una carrera la decide UNIQUE(comprador_id, clave_idempotencia). Tras el rollback
   del perdedor, otra transacción lee al ganador y compara contenido completo.
   Su contexto y UUID prevalecen aunque ambos preparadores vieran políticas distintas.
   Si no hay ganador, propagar el fallo; no ocultar otra violación de integridad.

Una falla tipada de preparación también hace una última lectura por si otro
trabajador confirmó entretanto. No hay UPDATE del checkout ni del estado en registro.
Reintentar una RESUELTA o COMPENSACION_PENDIENTE idéntica recupera su estado
original, nunca la reabre ni ejecuta acciones.

| Escenario revisado por inspección | Resultado |
|---|---|
| Mismo contenido, distinto orden de productos | Misma solicitud canónica y hashes; original. |
| Misma clave, cambio dirección/referencia/cantidad/límite/versión aceptada | CONTENIDO_DISTINTO. |
| Aceptación false o entrega incompleta | Entrada inválida, antes de persistir. |
| Política cambiada/retirada, reintento idéntico | Checkout/contexto/UUID originales, sin consultar política. |
| Registro antiguo sin checkout | CHECKOUT_HISTORICO_AUSENTE; no completar ni sobrescribir. |
| Dos inserciones con mismo contenido | UNIQUE y recuperación posterior al rollback. |
| Dos inserciones con distinto contenido | Ganador conservado; perdedor con conflicto. |
| Reinicio/respuesta perdida tras commit | Lectura durable devuelve el mismo UUID y checkout. |

El antiguo `registrar` queda deprecated y sigue produciendo solo intenciones
técnicas sin checkout. Rechaza recuperar un checkout comercial comparando
únicamente productos. `exigirCheckoutParaDescuento()` permanece intacto y bloquea
esas intenciones incompletas. Reclamar no autoriza descuentos; el futuro
coordinador debe exigir siempre esa barrera y el token vigente.

## Alcance y validación pendiente

No cambia SQL 01–04, repositorios, permisos ni mecanismos de claim. El INSERT
existente sobre solicitudes cubre las columnas; no se necesitan permisos nuevos.
No se invoca materialización, Catálogo ni compensación.

La compilación Java 21 y diff check no prueban MySQL. Quedan pendientes pruebas
reales de concurrencia, roundtrip JSON, reinicio y rollback con un proveedor
autorizado. Antes de integrar HTTP faltan las decisiones de política descritas,
el controlador que obtenga comprador desde JWT y la coordinación de stock.
