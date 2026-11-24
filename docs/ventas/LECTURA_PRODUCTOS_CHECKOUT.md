# Etapa 4.6B.3B — prelectura confiable de Catálogo

Condiciones aprobadas: [CHECKPOINT_COMPRA_V1](CONDICIONES_COMPRA_V1.md).
El lanzador integrado y `application.properties.example` ya propagan la variable;
las decisiones comerciales descritas anteriormente como pendientes quedan resueltas.

## Contrato reutilizado

GET directo a `catalogo-service`, resuelto por `LoadBalancerClient`/Eureka:
`/api/productos/{id}`. Se reutilizan WebClient, Reactor Netty y discovery existentes.
Sin Gateway, token humano, credenciales de stock ni nuevos endpoints.
El GET existente permite acceso anónimo; la autoridad es Catálogo localizado por
el backend, no un body o URL del frontend. La confianza de transporte depende
del discovery y la red del despliegue (no se añade autenticación inexistente al GET).

El controlador devuelve `EntityModel<ProductoResponseDto>`: campos de producto
en raíz y enlaces HATEOAS ignorados. `obtenerPorId` solo encuentra habilitados;
404 puede significar inexistente O deshabilitado, y no se inventa una distinción.
No existe un campo separado `estado/publicado` en ese DTO: publicación se expresa
mediante `habilitado`; `disponible` equivale a habilitado y stock mayor que cero.

`CatalogoProductosClient.consultar` comprueba:
- ID entero exactamente igual al solicitado, no coerciones.
- `habilitado` y `disponible` booleanos explícitos.
- Stock entero no negativo, coherente con disponible y suficiente para la cantidad.
  Esta lectura no reserva ni garantiza stock posterior.
- Categoría conocida, campo formato explícito (nullable), plataforma no vacía
  para videojuegos y clasificación comercial admitida.
- Rechazo de JSON vacío, claves duplicadas, tokens sobrantes o contrato incompleto.

Ignora precios y demás campos; el precio definitivo sigue proviniendo únicamente
del snapshot APLICADA. No aplica IVA ni crea snapshots de stock ficticios.

## Preparación V1 y condiciones

El bean `PoliticaCheckoutCatalogo` consulta cada ID único de la solicitud canónica.
`DespachoFijoV1.clasificarProducto` comparte la misma regla con la validación
del snapshot definitivo:

| Productos | Despacho por pedido | Requisito |
|---|---:|---|
| Solo VIDEOJUEGO DIGITAL | 0 | Sin entrega obligatoria |
| Solo físicos | 3990 | Entrega completa |
| Mixto | 3990 | Entrega completa |
| VIDEOJUEGO sin formato o DIGITAL de otra categoría | Rechazo | METADATOS_INVALIDOS |
| 404 | Rechazo | PRODUCTO_INEXISTENTE_O_NO_PUBLICADO |

CONSOLA/ACCESORIO/FIGURA con NULL o FISICO son físicos. El comprador nunca aporta
la clasificación. El contexto congelado sigue siendo DESPACHO_FIJO_V1/1.

Se requiere configurar `CHECKOUT_VERSION_CONDICIONES` con el identificador
real de condiciones aprobadas/publicadas que aceptará el comprador.
No se inventa un documento ni una versión de consentimiento a partir de la tarifa.
Vacío produce `CONDICIONES_NO_CONFIGURADAS`; diferencia con la aceptación produce
`CONDICIONES_NO_ACEPTADAS`. Esto ocurre antes de HTTP/INSERT.
La aprobación de la tarifa V1 sigue vigente; este es el identificador de aceptación.

### Configuración de despliegue revisada

`../../venta-service/src/main/resources/application.properties` está ignorado por Git mediante
`application*.properties`. El archivo local contiene las propiedades siguientes,
pero no forma parte de los cambios versionables. Reproducirlas en la configuración
externa del entorno al desplegar:

```properties
catalogo.productos.connect-timeout-ms=2000
catalogo.productos.response-timeout-ms=5000
catalogo.productos.total-timeout-ms=15000
checkout.version-condiciones=${CHECKOUT_VERSION_CONDICIONES:}
```

Los tres timeouts también tienen esos valores por defecto en el cliente Java.
La propiedad de condiciones tiene valor vacío por defecto: el servicio puede
arrancar, pero las preparaciones nuevas se rechazan antes de HTTP e INSERT.
El valor aprobado es `CHECKPOINT_COMPRA_V1`. El lanzador integrado lo asigna solo
a Ventas y el `.example` versionable contiene el enlace a la variable.
También se necesita discovery habilitado y una URL Eureka accesible en cada
entorno. La configuración local inspeccionada apunta a
`http://eureka-server:8761/eureka/`; su accesibilidad no se probó.

### Decisiones aprobadas por el usuario

- Las seis condiciones y su identificador `CHECKPOINT_COMPRA_V1` constan en
  [CONDICIONES_COMPRA_V1.md](CONDICIONES_COMPRA_V1.md).
- `CHECKOUT_VERSION_CONDICIONES=CHECKPOINT_COMPRA_V1` queda configurado en el lanzador.
- La presentación al comprador se integrará con el futuro checkout público;
  esta etapa no implementa esa interfaz ni acepta condiciones por el comprador.

La tarifa 0/3990 y la entrega obligatoria para físicos/mixtos ya están aprobadas.
El identificador de condiciones no se deduce automáticamente de
`DESPACHO_FIJO_V1`. Actualmente se acepta una versión configurada para intenciones
nuevas; los reintentos durables conservan su aceptación anterior sin revalidarla
contra la configuración vigente.

## Orden, transacciones y carreras

1. `registrarConCheckout` valida/canonicaliza y busca comprador/clave en una TX corta.
2. Si existe, compara entrada/productos/hashes y devuelve checkout/contexto/UUID
   originales, sin consultar Catálogo ni la configuración actual de condiciones.
   Tampoco reabre RESUELTA ni COMPENSACION_PENDIENTE.
3. Si es nueva, comprueba que el llamador NO tenga una TX activa. Rechaza una TX
   externa en vez de suspenderla conservando conexión o locks durante HTTP.
4. Prelectura y cálculo fuera de TX. Máximo cuatro GET simultáneos, 100 productos.
5. TX corta independiente: segunda lectura y INSERT atómico completo.
6. UNIQUE sigue decidiendo carreras; rollback y recuperación en otra TX.
   Un fallo tipado de preparación también relee al posible ganador antes de propagarse.

Cliente sin retries de Reactor/Netty ni redirecciones. Timeouts configurables:
conexión 2000 ms, respuesta 5000 ms y **15000 ms globales para todo el conjunto**,
incluido discovery, no 15000 por producto. Cuerpo máximo 128 KiB por respuesta.
Timeout/indisponibilidad/discovery fallido no crean una intención incompleta.
401/403 se distinguen de inexistencia; no se propagan cuerpos ni credenciales.
No se realiza ninguna llamada a CatalogoStockClient.

## Consistencia posterior

El catálogo puede cambiar después de la prelectura. Materialización conserva
validación de cobertura exacta, categoría/formato, política y costo contra APLICADA.
No sobrescribe contexto ni tarifa. Si la composición cambia de modo que altera
el despacho, rechaza el pedido: el futuro coordinador debe reconciliar y decidir
compensación durable con token/bloqueo vigente antes de revertir el UUID original.
No se implementa esa coordinación. Cambios de productos que no alteren la tarifa
siguen siendo evaluados según los snapshots definitivos; esta etapa no persiste
una copia de los metadatos preliminares.

## Revisión y límites

Revisados por inspección: digital puro, físico, mixto, 404, no publicado, sin stock,
contrato incompleto, formatos no admitidos y límite inferior al costo de despacho.
La comparación del total final (productos + despacho) permanece en materialización.
Reintentos tras cambio de catálogo/política/configuración recuperan lo persistido
antes de HTTP. Productos ya despublicados no impiden recuperar una intención.

Pendientes: validar la integración contra
servicios/MySQL en ejecución. La compilación Java 21 no acredita funcionamiento
de Eureka, HTTP, JSON real ni carreras concurrentes.
No se ejecutaron HTTP, SQL, descuentos, reversiones o creación de pedidos.

Cierre de revisión tras reanudar: cambios previos preservados; configuración,
orden de idempotencia, guardas de transacción y reglas de clasificación revisados.
La compilación anterior con Java 21.0.11 terminó en BUILD SUCCESS. En la revisión
de cierre solo se amplió esta documentación, sin cambios Java que exigieran
recompilar. Quedan pendientes las verificaciones reales de integración descritas.
