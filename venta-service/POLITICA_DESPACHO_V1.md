# Despacho fijo V1 — regla aprobada

Actualización 4.6B.3B: prelectura integrada en
[LECTURA_PRODUCTOS_CHECKOUT.md](LECTURA_PRODUCTOS_CHECKOUT.md).
`PoliticaCheckoutCatalogo` es el preparador instalado; las referencias siguientes
al cliente/bean pendiente son historia de la etapa de regla pura.

`DespachoFijoV1` es lógica pura interna, sin HTTP, persistencia ni bean de
preparación automática. Identificador `DESPACHO_FIJO_V1`, versión de política 1,
contrato comercial 1. Compatible con SQL 04 sin migración.

| Composición acreditada por Catálogo | Despacho CLP por pedido | Entrega |
|---|---:|---|
| Digital puro | 0 | No requerida |
| Físico puro | 3990 | Obligatoria |
| Mixto | 3990 | Obligatoria |

No se multiplica por cantidad ni por número de productos. Sin tarifas regionales,
precios locales ni IVA adicional. `costo(composicion)` calcula el importe y
`contexto(composicion)` construye el valor versionado, sin persistirlo.

## Clasificación confiable y bloqueo previo

`clasificar(snapshot)` utiliza categoría/formato del contrato Catálogo:
VIDEOJUEGO DIGITAL es digital; VIDEOJUEGO FISICO es físico;
CONSOLA/ACCESORIO/FIGURA con NULL o FISICO son físicos.
Se sigue rechazando DIGITAL para categorías distintas de VIDEOJUEGO.
El contrato tipado exige formato y plataforma para videojuegos y snapshot no vacío.
Este método no autentica procedencia: solo debe recibir evidencia del backend.

Antes del registro, `ContratoStock.Item` solo aporta ID y cantidad.
No existe cliente de metadatos previo ni réplica local confiable en Ventas.
Catálogo sí expone categoría/formato en `ProductoResponseDto` y consultas GET
de productos; no se han invocado ni integrado en esta tarea.
El snapshot de una operación APLICADA llega DESPUÉS de descontar, por lo que no
sirve para autorizar la preparación inicial ni como motivo para descontar primero.

Por ello no se registra un bean `PoliticaCheckout` que suponga composición.
`registrarConCheckout` continúa rechazando solicitudes nuevas con
`POLITICA_NO_CONFIGURADA` (falta el preparador completo, no la tarifa aprobada).
Los reintentos de solicitudes existentes se recuperan antes de consultar política.
No se infiere composición desde dirección o indicadores del frontend.
La barrera `exigirCheckoutParaDescuento()` permanece intacta.

### Ajuste mínimo pendiente

Integrar una lectura backend de metadatos de Catálogo ANTES del INSERT y de
cualquier descuento, fuera de la transacción local:

1. Obtener por canal confiable ID/categoría/formato de cada producto solicitado,
   comprobando cobertura exacta y rechazando metadatos ausentes/no admitidos.
2. Con esa evidencia, implementar el preparador `PoliticaCheckout` que use V1,
   verifique entrega para físicos y la versión de condiciones aceptada. No
   aceptar snapshots ni clasificación enviados por React como autoridad.
3. Congelar el costo aplicable junto con todo el checkout en el INSERT existente.
   No hace falta modificar SQL 04 para guardar el costo conocido.
4. Revalidar contra el snapshot definitivo: los metadatos pueden cambiar entre
   consulta y descuento. No sobrescribir el costo; detener materialización ante
   contradicción y dejar al coordinador tratarla explícitamente.

La identificación/publicación de las condiciones que el comprador acepta y el
contrato operativo de esa lectura aún deben integrarse. La aprobación de esta
tarifa no acredita por sí sola ninguna composición de productos.

## Materialización

Tras validar UUID y cobertura exacta, usa la clasificación del snapshot confirmado.
Para la versión persistida DESPACHO_FIJO_V1/1 calcula 0 o 3990 y exige igualdad
con el costo congelado. Versiones desconocidas o costo físico contradictorio
producen `CONTEXTO_DESPACHO_INVALIDO`, sin corregir checkout ni crear pedido.
Es una inconsistencia que requiere revisión; no decide compensación automáticamente.
El caso digital con costo no cero conserva `DESPACHO_DIGITAL_INVALIDO`.

Mantiene entrega obligatoria, precios/subtotales del snapshot y `Math.addExact`
para total productos + despacho. Si supera `limiteTotalClp`, conserva
`TOTAL_NO_ACEPTADO`. Transacción, claims, recuperación y decisiones de compensación
mantienen su flujo existente.

## Revisión de escenarios e idempotencia

Revisión estática:
- Digital puro: costo 0; total final igual al total Catálogo; entrega del pedido NULL.
- Físico puro: costo 3990 una vez; exige entrega; cantidades no alteran la tarifa.
- Mixto: costo 3990 una vez; indicadores físico/digital verdaderos y entrega requerida.
- Total superior al límite: rechazo antes del INSERT de pedido, con suma sin overflow.
- Costo congelado incompatible: rechazo, nunca sustitución silenciosa.
- Cambio futuro de política: registro idéntico recupera contexto/UUID originales;
  hashes y clave no cambian. Una futura V2 debe conservar el evaluador V1 para
  intenciones existentes, no reemplazar sus constantes.
- Pedido ya resuelto: recuperación original sin recalcular ni actualizar su tarifa.
- Contextos antiguos con identificadores distintos: se recuperan en registro, pero
  no se reinterpretan como V1 al materializar; requieren revisión explícita.

No se ejecutaron descuentos, pedidos, SQL, servicios ni HTTP. Compilación e inspección
no sustituyen validación real de MySQL, concurrencia ni del futuro lector de metadatos.
