# Importación administrativa RAWG y variantes

Implementación compilable y revisión estática: no se ejecutó SQL, no se hizo
ninguna petición real a RAWG en esta tarea ni se importaron productos.
Antes de probar, aplicar las restricciones manuales descritas abajo.

## Contrato HTTP definitivo

POST `{{baseUrl}}/api/productos/rawg/{{rawgId}}/importar`

Authorization: Bearer {{adminToken}}; Content-Type: application/json.
rawgId es un entero positivo y proviene de la búsqueda/previsualización.

```json
{
  "codigo": " jue-001 ",
  "plataforma": "  pc  ",
  "formato": "DIGITAL",
  "precio": 29990,
  "precioOferta": null,
  "enOferta": false,
  "stock": 5,
  "stockCritico": 2,
  "habilitado": true
}
```

Campos obligatorios: codigo (1–64 después de normalizar), plataforma (1–255
después de normalizar), formato FISICO/DIGITAL, precio Integer >0 y stock
Integer >=0. stockCritico opcional >=0. precioOferta null o >0; con oferta activa
debe ser menor que precio. enOferta=false y habilitado=true si se omiten;
null explícito se rechaza. Los precios son CLP finales, sin añadir IVA.

El DTO admite SOLO estos campos comerciales: rawgId en body, id, nombre,
categoria, descripcion, imagenUrl, genero, imagenes y cualquier campo
desconocido producen 400. La política estricta se limita a este DTO; no cambia
el JSON de los endpoints manuales.

201 devuelve ProductoResponseDto completo (sin envoltorio HATEOAS), incluyendo:
id local generado, codigo=JUE-001, rawgId de la URL, categoria=VIDEOJUEGO,
metadatos RAWG, plataforma=PC, formato=DIGITAL, valores comerciales enviados,
precioEfectivo, disponible e imagenes=[].
descripcionCorta, marca, ecosistema, compatibilidad y franquicia empiezan null.

Nombre, descripción sanitizada, imagen principal y géneros proceden exclusivamente
de RawgService.detalle(). Los géneros se unen con coma; si los metadatos exceden
los límites del DTO existente, se devuelve 502 RAWG_METADATOS_INVALIDOS en vez
de truncarlos o persistir datos inválidos. La URL principal se conserva sin
descargar ni duplicar la imagen. No se modifica RawgService ni sus errores.

## Transacciones y reutilización

RawgImportacionService usa NOT_SUPPORTED: suspende una transacción previa,
valida el DTO comercial y consulta RawgService fuera de una transacción de escritura.
RawgService puede consultar yaImportado en el repositorio como antes; esa
consulta informativa no es una transacción de escritura ni condiciona el alta.

Después, arma ProductoCrearRequestDto con mapeo explícito y llama a otro bean,
ProductoService.crearProductoImportado(), pasando por el proxy @Transactional.
Ese método reutiliza el alta local, comprobarCodigo, el mapeo editable,
guardarProducto, validaciones Default/ProductoCompleto y saveAndFlush.
No hay llamada externa dentro de esa transacción. No se delega una
@Transactional a una llamada interna que eluda el proxy.

La entidad nueva se crea con ID null, rawgId asignado solo desde este flujo,
categoría fija y colección de imágenes vacía. Precios y stock jamás se leen
de RAWG. El POST manual sigue pasando rawgId=null.

## SKU y variantes

- codigo: strip y mayúsculas Locale.ROOT, igual al CRUD.
- plataforma importada: convertir cualquier secuencia de espacios en uno,
  quitar bordes y pasar a mayúsculas Locale.ROOT.
- La misma normalización se aplica a plataforma en PUT de un producto con
  rawgId no nulo. El PUT manual sin rawgId conserva su comportamiento.
- PC/DIGITAL y PC/FISICO son variantes distintas; PS5/FISICO también.
- PC y " pc " son la misma plataforma. "PlayStation   5" y "PLAYSTATION 5"
  son la misma. La collation también ignora acentos.
- NO hay resolución de alias: PS5 y PLAYSTATION 5 son etiquetas distintas.
  El formulario debe mantener etiquetas consistentes para la plataforma concreta;
  no enviar la lista completa separada por comas que devuelve RAWG.
- La plataforma es una selección comercial del administrador; no se exige
  coincidencia literal con nombres/plataformas de la API externa.

Las búsquedas de duplicados no filtran habilitado. Un producto oculto sigue
ocupando SKU y combinación rawgId/plataforma/formato. PUT puede conservar su
propia combinación y SKU, pero no colisionar con otro producto.
StockEsperado se compara bajo bloqueo ANTES de modificar campos o galería.
Un cambio de categoría en un producto con rawgId no elimina ese identificador:
debe conservar plataforma/formato para cumplir la clave de variante.

**yaImportado=true significa que existe al menos una variante local**, incluso
oculta. NO significa que todas las versiones estén importadas ni impide crear
otra plataforma/formato con SKU diferente. No se crea UNIQUE(rawg_id).
Los productos manuales con rawgId=NULL pueden compartir plataforma/formato.

## Procedimiento MySQL Workbench antes de probar

### Base existente

1. Respaldar y detener Catálogo y otros escritores.
2. Confirmar que 01 y 04 ya están aplicados. Si falta 04, completar datos y
   ejecutar 03 sin incidencias antes de aplicar 04. Se requiere uq_producto_codigo.
3. Ejecutar **sql/05_validar_variantes_rawg.sql**: solo lecturas.
   Revisar duplicados normalizados, plataformas con espacios no canónicos,
   campos nulos y cualquier UNIQUE heredado sobre rawg_id solo.
4. Si hay incidencias, detenerse y corregirlas manualmente con revisión previa;
   no borrar ni fusionar productos automáticamente. No se incluyó UPDATE.
   Volver a ejecutar 05 hasta resolverlas.
5. Ejecutar UNA vez **sql/06_unicidad_variantes_rawg.sql**.
   Añade UNIQUE(rawg_id, plataforma, formato), CHECK de campos de variante
   y cambia explícitamente la collation de plataforma a utf8mb4_0900_ai_ci.
   Los espacios deben estar normalizados para las filas con rawgId.
6. Revisar SHOW CREATE TABLE productos y SHOW INDEX: comprobar las dos
   restricciones uq_producto_codigo y uq_producto_variante_rawg y el CHECK.
7. Reiniciar Catálogo/Gateway y probar. El DDL hace commit implícito;
   no confiar en ROLLBACK para deshacerlo.

El CHECK y el índice hacen que MySQL garantice la combinación incluso con
importaciones concurrentes. Las comprobaciones Java solo mejoran el error:
sin los índices NO hay garantía concurrente, y el endpoint no crea índices
ni comprueba el esquema en cada petición.

### Instalación vacía

El script crear_productos_checkpoint_store_catalogo.sql ya incorpora la
unicidad de variante y su CHECK. NO ejecutar 06 después de ese script.
El SKU sigue el procedimiento 03/04 existente: activar su UNIQUE/NOT NULL
antes de habilitar importación. No ejecutar CREATE TABLE contra una base existente.

## Seguridad y respuestas

Solo se añadió la regla POST /api/productos/rawg/{rawgId}/importar
ADMINISTRADOR en Catálogo y Gateway. El destino /api/productos/** ya existe.
Las reglas GET públicas, RAWG y resto de operaciones se conservan.

| Caso | HTTP |
| --- | --- |
| Importación válida | 201 |
| JWT ausente/inválido | 401 |
| CLIENTE/VENDEDOR | 403 |
| Datos inválidos/campos extra/ID no positivo | 400 |
| Juego RAWG inexistente | 404 |
| SKU o combinación de variante duplicados | 409 |
| Fallo externo RAWG | Mismos códigos controlados existentes |

La colisión simultánea detectada por MySQL de cualquiera de los dos índices
se traduce a 409 en GlobalExceptionHandler, sin SQL ni datos internos.
Si RAWG falla, no se inicia el guardado. Si el guardado falla, la transacción
revierte la fila local. No se registra la clave, JWT ni la respuesta externa.
No hay reintentos automáticos ni sincronización posterior.
Como el detalle se consulta antes de persistir, una indisponibilidad de RAWG
puede preceder a la comprobación local de duplicados.

## Pruebas Postman pendientes

Repetir por 9091 y 9090, después del SQL. Elegir un rawgId de resultados reales
y SKU libres; los ejemplos no se ejecutaron y una prueba positiva crea datos.

1. Buscar y previsualizar el juego con ADMINISTRADOR.
2. Importar PC/DIGITAL con un SKU libre y body válido: 201. Verificar nombre,
   descripción, género y URL de RAWG; comparar precios/stock con lo enviado.
3. GET público del ID local: confirmar rawgId, SKU, formato/plataforma e imagenes=[].
4. Mismo rawgId, PC/FISICO y otro SKU: 201 aunque yaImportado=true.
5. Mismo rawgId, " pc ", DIGITAL y otro SKU: 409.
6. Repetir SKU con otro juego/variante: 409. Deshabilitar una variante de
   prueba y repetir su combinación: 409; reactivar usando PUT y stockEsperado.
7. PUT conserva combinación propia: 200. Intentar combinación de otra variante:
   409 sin cambiar campos ni galería. StockEsperado antiguo: 409.
8. Dos importaciones simultáneas con mismo SKU: una 201, otra 409.
   Con SKU distintos y misma variante: una 201, otra 409. Verificar una fila.
9. Omitir stock/codigo/plataforma/formato, valores negativos/fraccionarios,
   oferta inválida, rawgId en body, nombre/categoria falsificados: 400.
10. JWT ausente/expirado: 401; CLIENTE/VENDEDOR: 403.
11. Juego inexistente: 404. Clave ausente/rechazada, cuota o caída RAWG:
    error controlado; comprobar que no hay un producto parcial.
12. PUT, stock, galería y baja lógica de importado; creación manual de las
    cuatro categorías conserva contrato y rawgId=null. Ninguna descarga
    de imagen RAWG debe aparecer en la carpeta local.
