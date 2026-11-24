# Coordinador interno — Etapa 4.6C

Actualización 4.7: [CHECKOUT_HTTP.md](CHECKOUT_HTTP.md) expone `comprar` mediante
POST autenticado para CLIENTE. `continuar(id)` permanece interno; no se añaden
schedulers ni endpoints administrativos de recuperación.

## API y confianza

Bean `CoordinadorPedidoService`, sin endpoints ni tareas automáticas:

- `comprar(compradorId, claveIdempotencia, items, entrada)`: identidad confiable
  aportada por el futuro controlador JWT, nunca tomada de un body arbitrario.
- `continuar(solicitudId)`: recuperación técnica explícita, únicamente para código
  interno autorizado. No es una consulta pública por ID ni autoriza compradores.

Ambos rechazan una transacción activa del llamador. Devuelven un record
`Resultado(estado, codigo, solicitud)` con vista durable inmutable:
RESUELTA, RECUPERABLE, OCUPADA o REVISION_REQUERIDA. El resultado terminal completo
está en `solicitud.resultado`; PEDIDO_CREADO contiene el ID persistido.
Conflictos de contenido y errores de infraestructura pueden propagarse como
excepciones: no se transforman en rechazos comerciales.

## Flujo exacto

1. `comprar` reutiliza `registrarConCheckout`: compara identidad/contenido antes
   de consultar Catálogo. Solo una intención nueva hace prelectura y prepara V1.
2. Confirma el INSERT completo (checkout, hashes y UUID original) en REQUIRES_NEW.
3. `continuar` relee la solicitud por ID. Si ya está RESUELTA devuelve lo persistido,
   sin stock HTTP; para PEDIDO_CREADO verifica pedido, ID, comprador, clave y hash.
4. Reclama por 2 minutos usando los mecanismos existentes. Un lease vigente ajeno
   devuelve OCUPADA; una resolución concurrente se recupera.
5. Una TX breve renueva el claim y registra RECONCILIACION_PENDIENTE **antes**
   de cualquier stock HTTP. Si había decisión de compensar la conserva.
6. Consulta/reconcilia siempre el UUID original.
7. Si corresponde aplicar, otra TX comprueba claim, checkout y ausencia de pedido,
   renueva y confirma la autorización local antes del POST idempotente.
8. APLICADA pasa al materializador existente. Pedido, detalles y resolución se
   confirman juntos; no se implementa otra persistencia de pedidos.
9. Estados no terminales se dejan recuperables, liberando solo con token vigente.
   La decisión de compensar nunca se borra al liberar/reclamar.

Máximo por invocación: un GET, un aplicar y, solo ante fallo comercial definitivo,
un revertir. No hay bucles, polling ni retries automáticos de POST. Si ya existe
decisión de compensar: como máximo GET + revertir, nunca aplicar.
El cliente conserva su deadline configurado (15 s por defecto). El coordinador
añade techo de 60 s por llamada y renueva el lease antes de cada llamada.
No crea otro UUID de stock.

## Estados remotos

| Evidencia | Acción sin decisión de compensación | Con COMPENSACION_PENDIENTE |
|---|---|---|
| APLICADA | Materializar/reintentar materialización | Revertir mismo UUID |
| RECHAZADA | RECHAZO_DEFINITIVO | RECHAZO_DEFINITIVO, sin restitución |
| PENDIENTE | Un aplicar explícito con UUID/contenido originales | Revertir/cerrar |
| INEXISTENTE (404 reconocido por cliente) | Un aplicar idempotente, NO asumir que nunca hubo intento | Cierre preventivo con revertir |
| REVERTIDA | Revisión: cierre sin decisión local | COMPENSACION_CONFIRMADA |
| CANCELADA | Revisión: cierre sin decisión local | CIERRE_PREVENTIVO_CONFIRMADO |
| Timeout, caída, respuesta incierta o error técnico remoto | Recuperable; próxima entrada empieza consultando | Conserva decisión, próxima entrada consulta |
| Conflictos remotos, autenticación o solicitud inválida | Revisión requerida | Revisión, sin deshacer decisión |

Un 404 no acredita inexistencia histórica de efectos: reenviar aplicar con la
misma identidad es seguro por la idempotencia remota, no por el 404. Si otro POST
está retrasado, Catálogo serializa esa operación. CANCELADA/REVERTIDA son terminales
remotos y bloquean descuentos posteriores del mismo UUID.

Se validan UUID, estado/código/status y cobertura de snapshots en materialización
y cierre. Un snapshot incompleto/contradictorio no confirma pedido ni autoriza
compensación automática. Las intenciones históricas sin checkout no pueden aplicar.
No se emite RECHAZO_LOCAL por timeout, autenticación, 404 o rollback.

## Compensación durable y mutuamente excluyente

Solo los fallos comerciales tipados de materialización autorizan evaluar
compensación: falta de entrega, despacho digital/físico incompatible, total por
encima del límite o overflow al sumar despacho.

`MaterializacionPedidoService.motivoCompensacion` reutiliza exactamente las
validaciones existentes, sin SQL ni efectos. Después del rollback, el servicio
de solicitudes adquiere de nuevo el lock de la fila, exige token vigente,
comprueba ausencia de pedido por operación y comprador/clave y revalida el
incumplimiento contra APLICADA y el checkout durable. Persiste motivo y
COMPENSACION_PENDIENTE, confirma, y solo entonces el coordinador autoriza revertir.

La autorización de reversión vuelve a verificar decisión, token y ausencia de
pedido en otra TX corta. Timeout durante reversión conserva la decisión y se
reconcilia en una futura invocación; no se declara éxito sin confirmación.
REVERTIDA conserva motivo; CANCELADA también. RECHAZADA es evidencia terminal sin
descuento: se cierra con motivo NULL, como exige SQL 04, sin reabrir la intención.
`Resultado` serializa pedidoId null explícitamente para los CHECK JSON.

Se distingue costo V1 físico contradictorio (`DESPACHO_FISICO_INVALIDO`, comercial)
de una política/versión desconocida (`CONTEXTO_DESPACHO_INVALIDO`, revisión).
No se inventa una tarifa para una versión histórica desconocida.

## Concurrencia, errores y recuperación

- Todas las nuevas mutaciones usan lock de solicitud, token y vencimiento; guard
  beforeCommit vuelve a comprobar vigencia. La TX local tiene timeout de 10 s.
- Los UNIQUE de solicitudes y pedidos siguen siendo la autoridad contra carreras.
- La materialización y la decisión de compensar comparten lock: nunca pueden
  confirmarse ambas decisiones para una misma solicitud usando este flujo.
- Un trabajador pausado puede ejecutar HTTP después de perder su lease. No hay
  garantía de tiempo real entre una autorización local y un envío remoto. La
  seguridad depende del bloqueo local para decisiones, del token para escrituras
  y de la idempotencia/cierre remoto con UUID original, no solo del lease.
- Si un sucesor decidió compensar, un aplicar tardío no permite materializar;
  revertir serializa/restaura o cierra preventivamente. Si ya se creó pedido, el
  sucesor no puede decidir compensación ni autorizar revertir.
- Error transitorio MySQL durante materialización: rollback, sin reversión
  automática. Si se puede persistir la liberación se devuelve RECUPERABLE.
  Otros fallos de escritura/commit se propagan; el lease queda para expirar.
- Si MySQL falla al decidir compensación, no se envía revertir. Si el commit
  quedó incierto, `continuar` consulta la decisión durable antes de actuar.
- Si Ventas cae después de aplicar pero antes de guardar pedido, permanece
  RECONCILIACION_PENDIENTE; al vencer el claim, `continuar` obtiene APLICADA y
  vuelve a materializar usando los snapshots históricos.
- Si cae después de crear pedido o pierde la respuesta, la repetición recupera
  PEDIDO_CREADO sin stock HTTP. Si cae después de decidir compensar, continúa esa
  decisión, nunca intenta materializar.
- Un token vencido/obsoleto no se usa para liberar ni actualizar. Se relee estado
  y devuelve OCUPADA o el terminal confirmado por otro trabajador.

SQL 02–04 es suficiente; no cambian tablas, índices ni restricciones.
La clasificación inicial puede quedar desactualizada: sigue siendo definitiva
la evidencia de Catálogo APLICADA. No se altera el contexto para forzar un pedido.

## Nuevos métodos internos de soporte

`SolicitudPedidoService`: `buscarPorId`, `prepararConsulta`, `autorizarAplicacion`,
`decidirCompensacion`, `autorizarReversion`, `confirmarCierreRemoto`,
`dejarRecuperable`. Los métodos mutantes confirman transacciones independientes
antes de devolver. Ninguno hace HTTP.

`SolicitudPedido`: transiciones `decidirCompensacion` y `resolverSinPedido`.
Esta última no admite PEDIDO_CREADO; esa resolución permanece exclusivamente en
la transacción de materialización. No hay resolución genérica con un ID arbitrario.

## Permisos y requisitos antes de pruebas

Para la cuenta/host real `checkpoint_ventas_app`:
- SELECT en solicitudes_pedido, pedidos y detalle_pedidos.
- INSERT/UPDATE en solicitudes_pedido; INSERT en pedidos y detalle_pedidos.
- Si UPDATE es por columnas, Hibernate actualiza estado, version,
  token_reclamacion, bloqueo_hasta, fecha_actualizacion, codigo_compensacion y resultado.
- No se requieren UPDATE/DELETE en pedidos/detalles ni permisos DDL.

No se inspeccionaron ni concedieron GRANT reales. MySQL debe conservar SQL 01–04
y Hibernate validate. Configurar condiciones CHECKPOINT_COMPRA_V1, Eureka y
STOCK_SERVICE_JWT_SECRET compartido con Catálogo, distinto del JWT humano. El
cliente existente emite HS256 con scope stock:operar; el coordinador no emite
tokens ni crea otra implementación de stock.

## Validación manual posterior (no ejecutada)

Con un llamador interno autorizado, datos controlados y permisos correctos:
1. Invocar comprar para digital, físico y mixto. Verificar un pedido completo,
   resultado con mismo ID, despacho 0/3990 y un único descuento.
2. Repetir misma clave/contenido y concurrencia de dos trabajadores: original o
   OCUPADA, jamás duplicación. Modificar entrada: conflicto antes de stock.
3. Provocar timeout tras aplicación y reiniciar Ventas antes de materializar;
   tras vencimiento, continuar(id) debe recuperar APLICADA y el pedido original.
4. Simular fallo de INSERT/commit local: ninguna reversión automática; restaurar
   MySQL y continuar. Comprobar ambos casos de resultado de commit desconocido.
5. Obtener un total Catálogo mayor que el límite: comprobar primero decisión
   COMPENSACION_PENDIENTE, luego restitución y resultado terminal confirmado.
6. Interrumpir después de esa decisión y durante revertir: continuar siempre
   compensa/reconcilia, preservando UUID y motivo.
7. Pausar un trabajador hasta perder claim: sus escrituras deben fallar; ejecutar
   el sucesor y revisar exclusión entre pedido y compensación.
8. Comprobar snapshots contradictorios, cierre remoto sin decisión local, errores
   de autenticación y solicitudes sin checkout: revisión sin materialización.

La revisión de estos escenarios fue estática. Java 21 compila, pero no se
validaron ejecución HTTP, concurrencia real, MySQL ni interrupciones de proceso.
Sin endpoints, scheduler, stock real, SQL, servicios iniciados o commits en esta tarea.
