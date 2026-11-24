# Cliente interno de stock — Etapa 4.5

Implementación limitada a Ventas. Sin endpoints nuevos, compras, escrituras de
pedidos, compensaciones automáticas, cambios SQL o permisos de MySQL.
El cliente se invoca desde el coordinador durante el checkout. El arranque solo comprueba disponibilidad.

## Métodos

Bean `stock.CatalogoStockClient`:

```java
Mono<ResultadoStock> aplicar(UUID id, ContratoStock.Solicitud solicitud);
Mono<ResultadoStock> consultar(UUID id);
Mono<ResultadoStock> revertir(UUID id, ContratoStock.Solicitud solicitudOriginal);
Mono<ResultadoStock> reconciliar(UUID id); // alias explícito de consultar
```

El UUID siempre procede del coordinador, nunca se genera en este cliente.
La solicitud incluye solo `versionContrato: 1` e `items: [{productoId, cantidad}]`.
Los records validan 1..100 ítems, IDs/cantidades positivos y sin duplicados.
Se comprueba además el límite de 16 KiB. Valores inválidos locales lanzan excepción
antes de enviar HTTP. El orden original se conserva; Catálogo canonicaliza.

Cada `Mono` es frío: **cada suscripción constituye un intento explícito nuevo**
con el mismo UUID y solicitud, y un token recién emitido. No usar retry/repeat
indiscriminadamente en el llamador. No hay HTTP hasta que alguien se suscriba.
El coordinador deberá persistir intención/UUID antes de suscribir y efectuar
las llamadas fuera de transacciones que mantengan bloqueos de base de datos.

Correspondencia con Catálogo:

| Método | HTTP directo |
|---|---|
| aplicar | POST /internal/stock/operaciones/{uuid}/aplicar |
| consultar/reconciliar | GET /internal/stock/operaciones/{uuid} |
| revertir | POST /internal/stock/operaciones/{uuid}/revertir |

## Descubrimiento y límites

Se reutiliza Spring Cloud LoadBalancer con `choose("catalogo-service")`, que
selecciona una instancia del registro Eureka existente. El WebClient dedicado
envía directamente a esa instancia, sin Gateway ni URL suministrada por React.
El descubrimiento bloqueante se ejecuta en boundedElastic.

Se reutilizan las dependencias existentes de WebFlux, Reactor Netty, Eureka,
LoadBalancer y Spring Security/Nimbus. El builder HTTP heredado permanece intacto.
El transporte dedicado permite controlar reintentos y credenciales sin afectar
la lógica heredada:

- Conexión: 2 segundos (`stock.client.connect-timeout-ms`).
- Respuesta/inactividad de lectura: 10 segundos (`stock.client.response-timeout-ms`).
- Límite total por suscripción, incluyendo discovery y lectura: 15 segundos
  (`stock.client.total-timeout-ms`).
- Valores configurables positivos en el ejemplo de properties; defaults en Java.
- Máximo 512 KiB de respuesta en memoria, suficiente para 100 snapshots del
  contrato actual. Una respuesta mayor no se usa como resultado de negocio.
- Reactor Netty `disableRetry(true)`: desactiva incluso el reintento implícito
  por conexión cerrada. Sin filtros HTTP de retry de LoadBalancer ni `.retry`.
- No se siguen redirects ni se activa wiretap.

El límite total cancela la espera local; **no demuestra rollback remoto**.
El cliente no ejecuta sleeps ni reintentos basados en Retry-After. El coordinador
deberá programar consultas/reintentos acotados y conservar el UUID original.

## JWT exclusivo

`StockServiceTokenProvider` lee solo `System.getenv("STOCK_SERVICE_JWT_SECRET")`.
Sin valor válido, menor de 32 bytes UTF-8, solo espacios o igual al secreto humano
resuelto, Ventas falla al arrancar. No hay fallback al JWT humano.
La cadena es UTF-8 literal; si contiene Base64 no se decodifica.

El lanzador de cinco servicios ya comparte esta variable con Catálogo.
Para arranque aislado, usar la entrada oculta de `ARRANQUE_LOCAL.md`.
Nunca guardar la clave en properties, Git, scripts, temporales, argumentos o logs.

Cada intento emite HS256 con:

- iss = checkpoint-stock-services
- aud = [catalogo-stock]
- sub = venta-service
- scope = stock:operar
- iat = instante UTC actual truncado a segundos
- exp = iat + **240 segundos**, inferior al máximo de 300 de Catálogo
- Sin claim role, userId ni JWT del comprador.

El encoder privado no sustituye al decoder JWT humano ni publica un endpoint.
El token no se cachea ni persiste; vive temporalmente en la petición.
No se registran cuerpos, tokens, secretos o excepciones de transporte.
Sincronizar los relojes: Catálogo no admite iat futuro ni tolerancia de expiración.

## DTO y clasificación

`ContratoStock` contiene records de solicitud, operación, snapshot y detalles.
No depende de entidades de Catálogo o Pedido. Importes Long CLP finales, fechas
Instant. Estados PENDIENTE/APLICADA/RECHAZADA/REVERTIDA/CANCELADA. Categorías y
formatos coinciden con el contrato de Catálogo.

La respuesta `Operacion` corresponde exactamente a:
`operacionId`, `versionContrato`, `estado`, `codigo`, `snapshot`,
`fechaCreacion`, `fechaActualizacion`, `fechaAplicacion`, `fechaCierre`.
Se verifican UUID, versión, estado/HTTP, código, fechas, snapshot obligatorio
en APLICADA/REVERTIDA, sumas/subtotales y productos únicos. Para POST también se
comparan IDs/cantidades del snapshot contra la solicitud original.
Ningún precio procede de React; solo se aceptan los precios del snapshot remoto.

`ResultadoStock` incluye UUID, tipo, HTTP si se conoce, código sanitizado y
operación validada cuando existe. No conserva mensajes SQL, headers ni cuerpo crudo.

| Respuesta/situación | Tipo |
|---|---|
| 200 APLICADA (incluida repetición) | APLICADA |
| 202 PENDIENTE de aplicar / 200 PENDIENTE de consultar | PENDIENTE |
| 409 RECHAZADA de aplicar / 200 RECHAZADA en GET/reversión | RECHAZADA, con código de negocio |
| 409 CANCELADA/REVERTIDA al aplicar; 200 en GET/reversión | CANCELADA/REVERTIDA; snapshot conservado si corresponde |
| 409 OPERACION_CONTENIDO_DISTINTO o CONSUMIDOR_DISTINTO | CONFLICTO_IDEMPOTENCIA |
| 409 REVERSION_PRODUCTO_INEXISTENTE o STOCK_FUERA_DE_RANGO | CONFLICTO_REVERSION |
| 401/403 | AUTENTICACION_INTERNA; no renovar/reintentar automáticamente |
| 404 OPERACION_INEXISTENTE al consultar | INEXISTENTE |
| 400/413/415 con código reconocido | SOLICITUD_INVALIDA |
| Sin instancia, conexión rechazada/DNS, 502/503/504 | CATALOGO_NO_DISPONIBLE |
| Timeout, conexión interrumpida, cuerpo ausente/malformado/incompatible | RESULTADO_INCIERTO |
| Otros 5xx o error técnico no clasificado | ERROR_TECNICO |

Un fallo leyendo el body devuelve RESULTADO_INCIERTO con HTTP si ya se recibió.
El timeout total lleva TIMEOUT_STOCK; una lectura fallida puede llevar
RESPUESTA_NO_INTERPRETABLE. Nunca se transforma en RECHAZADA por inferencia.

`requiereReconciliacion()` es false solo para estados terminales validados:
APLICADA, RECHAZADA, REVERTIDA, CANCELADA. Para los demás es una señal conservadora,
no ejecuta la consulta ni determina que sea seguro repetir una escritura.
Incluso una solicitud inválida actual no resuelve el estado de intentos anteriores.

## Reconciliación

1. Ante timeout/5xx/conexión perdida, conservar intención y UUID.
2. Invocar `reconciliar(uuid)` con el mismo UUID: solo hace GET.
3. APLICADA: recuperar snapshot; no generar otra operación para la misma compra.
4. PENDIENTE: decidir continuar/revertir desde el coordinador durable.
5. RECHAZADA/REVERTIDA/CANCELADA: respetar resultado definitivo.
6. INEXISTENTE: no prueba que un POST retrasado no vaya a llegar. Para abandonar
   con seguridad se requiere cierre preventivo, usando solicitud original.
7. Conflicto de identidad/contenido: corregir coordinación; no cambiar UUID
   automáticamente para eludir idempotencia.
8. AUTENTICACION_INTERNA: corregir clave/claims/reloj; no usar credenciales humanas.

No existe todavía un recuperador durable ni persistencia de intentos en Ventas.
La consulta explícita es la pieza disponible para implementarlos en otra etapa.

## Verificación y límites

Compilación Java 21 e inspección del contrato, autenticación, límites y errores.
Sin nuevas pruebas unitarias, servicios, SQL ni peticiones HTTP durante esta tarea.
No se afirma integración funcional en ejecución.

Pendiente en un entorno autorizado: arranque con secreto compartido, descubrimiento
Eureka, interoperabilidad JWT, serialización del snapshot, 409 de ambos formatos,
reversión repetida, indisponibilidad, timeout tras commit y reconciliación por UUID.
El checkout invoca al cliente mediante el coordinador. Para aislar los fallos de transporte de esta matriz se requiere un entorno de prueba controlado.
Eureka es infraestructura confiable: restringir su acceso, y usar TLS/red interna
para credenciales en despliegue. El secreto HS256 compartido debe mantenerse
fuera del frontend y de Gateway.
