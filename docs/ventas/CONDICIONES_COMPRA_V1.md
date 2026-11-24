# Condiciones comerciales aprobadas — CHECKPOINT_COMPRA_V1

## Las seis condiciones

1. Los precios proceden de Catálogo, sin IVA adicional.
2. El total final, incluido despacho, no puede superar `limiteTotalClp` aceptado.
3. Despacho fijo de 3990 CLP para físico/mixto y 0 CLP para digital puro.
4. La compra requiere stock suficiente.
5. La aceptación del comprador es explícita y corresponde a `CHECKPOINT_COMPRA_V1`.
6. Es una simulación académica: no existen cobros ni despachos reales en esta versión.

## Configuración reproducible

La variable no es un secreto:

```text
CHECKOUT_VERSION_CONDICIONES=CHECKPOINT_COMPRA_V1
```

`iniciar_checkpoint_local.bat` invoca `iniciar_identidad.ps1 -IncluirCatalogo`.
El script conserva el entorno anterior, neutraliza la variable heredada y asigna
el valor aprobado justo antes de `Start-ServiceWindow 'venta-service' 9094`.
El proceso cmd/Maven de Ventas hereda una copia; el script elimina la variable
después de crear ese proceso y restaura el entorno original al finalizar.
Los otros servicios no reciben la asignación de Ventas.

El arranque integrado carga el archivo versionable
`venta-service/src/main/resources/application.properties.example`, que contiene:

```properties
checkout.version-condiciones=${CHECKOUT_VERSION_CONDICIONES:}
```

Esto no depende del `application.properties` privado ignorado por Git.
No cambia secretos JWT, credenciales MySQL ni el modo de arranque de otros servicios.
Sin variable, el backend mantiene el rechazo de nuevas preparaciones.

En otro entorno, establecer esa variable en el proceso/contenedor de Ventas y
asegurar que su configuración Spring incluya el enlace anterior (o definir
explícitamente `checkout.version-condiciones=CHECKPOINT_COMPRA_V1`).
Por ejemplo, antes de arrancar Ventas desde PowerShell:

```powershell
$env:CHECKOUT_VERSION_CONDICIONES = 'CHECKPOINT_COMPRA_V1'
```

## Integridad de la aceptación

El comprador debe proporcionar explícitamente la aceptación y esta versión;
configurar el servidor no equivale a aceptar por él. No se añade pantalla React
ni aceptación automática.

- `DatosCheckoutSolicitud.Aceptacion` exige aceptación true.
- `PoliticaCheckoutCatalogo` compara la versión exacta con la configurada antes
  de consultar Catálogo y rechaza una versión diferente.
- La versión se conserva en `checkout.entrada.aceptacion.versionCondiciones` y
  forma parte de `hash_checkout`. `contextoServidor` conserva la política
  `DESPACHO_FIJO_V1`, su versión y el costo; son identificadores distintos.
- El INSERT confirma solicitud, checkout, hashes y UUID conjuntamente.
- Los reintentos comparan el contenido completo y recuperan la intención original
  antes de consultar configuración comercial o Catálogo. No dependen de que los
  productos sigan publicados y no actualizan silenciosamente el contexto.
- El registro técnico antiguo sin checkout sigue bloqueado para descuento.

## Comprobación manual posterior — no ejecutada en esta tarea

1. Arrancar con `iniciar_checkpoint_local.bat`, con el esquema/permisos ya preparados,
   y comprobar que Catálogo y Ventas estén registrados en Eureka.
2. Usar un llamador Java interno autorizado, sin transacción activa, para invocar
   únicamente `PoliticaCheckoutCatalogo.preparar(solicitud, entrada)`, con IDs reales
   publicados y aceptación explícita de CHECKPOINT_COMPRA_V1. Esto permite observar
   la lectura GET sin registrar intención, descontar stock ni crear pedido.
3. Comprobar digital puro (0), físico y mixto (3990 y entrega requerida), e ID
   inexistente/deshabilitado (rechazo). Una aceptación false o versión distinta
   debe rechazarse; un fallo de lectura no debe producir preparación válida.
4. En una validación posterior de persistencia autorizada, comprobar recuperación
   de una clave existente con su contenido original sin GET, incluso si cambian
   las condiciones configuradas o la publicación de productos.

Para comprobar la integración desde HTTP, seguir el [contrato de checkout](CHECKOUT_HTTP.md). Los GET públicos de Catálogo solo verifican la lectura del catálogo.

## Validaciones pendientes del checkout

Validar en ejecución discovery, contrato JSON, permisos/roundtrip MySQL y carreras
idempotentes. Comprobar la integración del checkout autenticado con el coordinador, la reconciliación y la compensación, y verificar la presentación de las condiciones y su aceptación explícita. La tarifa y el identificador V1
ya están aprobados; no queda pendiente inventar o aprobar otro identificador.
