# Contrato HTTP del formulario dinámico

Implementación revisada estáticamente y pendiente de HTTP/MySQL. No se ejecutó
SQL ni se modificaron registros reales. Solo se modifica catalogo-service.

## Requisitos previos y compatibilidad

01_ampliar_producto_formulario.sql ya debe estar aplicado: columnas nuevas y
tabla producto_imagenes. NO volver a ejecutarlo.

**Antes de habilitar creación definitiva con el formulario:** completar los
datos antiguos (incluidos ocultos), ejecutar las validaciones de 03 y aplicar
manualmente 04_exigir_producto_completo.sql, revisándolo previamente.
No se cambió ni ejecutó ningún script SQL en esta tarea.

Sin uq_producto_codigo la comprobación de existencia rechaza duplicados
secuenciales, pero NO garantiza unicidad ante dos POST/PUT simultáneos.
Con el índice, la colisión de uq_producto_codigo se traduce a 409 sin exponer SQL.
El índice usa la collation utf8mb4_0900_ai_ci: ignora mayúsculas y acentos.
Las consultas de existencia usan esa misma columna y collation.
Códigos antiguos con espacios exteriores requieren normalización revisada;
esta implementación no modifica registros antiguos automáticamente.

El CHECK de categoría de 04 exige formato para VIDEOJUEGO, pero NO exige
plataforma: la nueva regla formato + plataforma se valida en DTO/servicio.
No se modificó ese SQL. No hay UNIQUE rawg_id ni se propone añadirlo.

POST/PUT antiguos sin codigo y campos por categoría ahora devuelven 400.
Los GET siguen devolviendo registros antiguos incompletos; un PUT sobre ellos
debe completarlos. Stock/DELETE no activan el grupo ProductoCompleto y no exigen
rellenar metadatos nuevos para poder mover stock/deshabilitar.

## Campos

| Campo | Regla |
| --- | --- |
| codigo | Obligatorio, strip + mayúsculas Locale.ROOT, máximo 64; sin generación automática |
| nombre | Obligatorio, no vacío, máximo 255 |
| categoria | VIDEOJUEGO, CONSOLA, ACCESORIO, FIGURA |
| descripcionCorta | Opcional, máximo 500 |
| descripcion | Descripción larga opcional |
| marca | Opcional, máximo 100 |
| precio | Integer positivo, CLP final |
| precioOferta | Null o Integer positivo; si hay oferta, menor que precio |
| enOferta | POST false por defecto; PUT obligatorio |
| stock | Integer >=0; POST 0 por defecto; PUT obligatorio |
| stockCritico | Null o Integer >=0; no cambia disponible ni stock |
| habilitado | POST true por defecto; PUT obligatorio |
| imagenUrl | Null o URL absoluta HTTP/HTTPS, máximo 2048 |
| imagenes | Null o lista de 0–4 URLs absolutas HTTP/HTTPS, máximo 2048 cada una |
| plataforma, genero | Máximo 255; plataforma obligatoria para VIDEOJUEGO |
| formato | Null, FISICO o DIGITAL; obligatorio para VIDEOJUEGO |
| ecosistema | Máximo 100, obligatorio para CONSOLA |
| compatibilidad | Máximo 255, obligatorio para ACCESORIO |
| franquicia | Máximo 255, obligatoria para FIGURA |
| stockEsperado | Solo PUT: obligatorio, Integer >=0 |

Otros atributos de categoría pueden ser null. No se borran automáticamente
campos enviados solo por pertenecer a otra categoría. Las URLs deben tener
host, esquema http/https en minúsculas y no incluir credenciales ni espacios;
no se aceptan data:, Base64, rutas relativas, vacíos o elementos null.
No se hace petición remota para validar que la imagen exista.

id/rawgId son solo salida: no hay setters en los DTO de entrada ni asignación
en el mapeo manual. POST deja rawgId null; PUT conserva el existente.
Los enums y enteros mantienen la configuración estricta de JSON existente.

## POST definitivo (ejemplo completo)

POST /api/productos, Bearer ADMINISTRADOR, Content-Type application/json:

```json
{
  "codigo": " jue-001 ",
  "nombre": "Videojuego de aventura",
  "categoria": "VIDEOJUEGO",
  "descripcionCorta": "Aventura para PS5",
  "descripcion": "Descripción larga del juego",
  "marca": "Editorial",
  "precio": 59990,
  "precioOferta": 44990,
  "enOferta": true,
  "stock": 5,
  "stockCritico": 2,
  "habilitado": true,
  "imagenUrl": "https://example.com/juego.jpg",
  "imagenes": [
    "https://example.com/juego-frente.jpg",
    "http://example.com/juego-dorso.jpg"
  ],
  "plataforma": "PS5",
  "genero": "Aventura",
  "formato": "FISICO",
  "ecosistema": "PlayStation",
  "compatibilidad": null,
  "franquicia": null
}
```

201: campos anteriores (codigo normalizado a JUE-001), id generado, rawgId=null,
precioEfectivo=44990 y disponible=true. No devuelve stockEsperado.
Ejemplos ejecutables para las cuatro categorías:
PRUEBAS_CATALOGO_POSTMAN.md. Elegir SKU libres antes de ejecutarlos.

## PUT definitivo sobre consola existente

PUT /api/productos/{{productoId}}, Bearer ADMINISTRADOR:

```json
{
  "codigo": "CON-001",
  "nombre": "Consola de prueba",
  "categoria": "CONSOLA",
  "descripcionCorta": "Consola con mando",
  "descripcion": "Descripción larga de la consola",
  "marca": "Marca",
  "precio": 499990,
  "precioOferta": null,
  "enOferta": false,
  "stock": 3,
  "stockEsperado": 3,
  "stockCritico": 1,
  "habilitado": true,
  "imagenUrl": "https://example.com/consola.jpg",
  "imagenes": [
    "https://example.com/consola-frente.jpg",
    "https://example.com/consola-lateral.jpg"
  ],
  "plataforma": null,
  "genero": null,
  "formato": null,
  "ecosistema": "PlayStation",
  "compatibilidad": null,
  "franquicia": null
}
```

El ejemplo supone stock real=3; reemplazar TODOS los valores por los observados
si se desea conservar el producto de prueba. No ejecutar a ciegas.

PUT admite el mismo código de su producto, normalizado; rechaza el de otro,
incluso deshabilitado. Los escalares opcionales omitidos quedan null.
Para la galería:

- imagenes omitido o null: conserva las imágenes existentes.
- imagenes=[]: elimina las adicionales.
- imagenes=[...]: reemplaza la galería completa, posiciones 1..N.
- imagenUrl=null: quita la referencia principal, sin tocar adicionales.

El servicio valida DTO, bloquea la fila y comprueba stockEsperado ANTES de
cambiar cualquier atributo o imagen. 409 por stock desactualizado deja todo
intacto. Dentro de la transacción se comprueba SKU y se reemplaza la galería.
Se vacía la colección y hace flush de orphanRemoval ANTES de reutilizar orden,
evitando colisiones temporales de UNIQUE(producto_id, orden).
Flush no es commit: un fallo posterior revierte atributos y galería juntos.
No se borra ningún archivo físico, solo filas de referencias de imagen.

## GET y resto de respuestas

GET /api/productos, /api/productos/{id}, ?categoria=... mantienen HATEOAS y
filtro de habilitado. Cada ProductoResponseDto ahora incluye todos los campos
del formulario más id, rawgId, precioEfectivo y disponible, sin stockEsperado.
imagenes siempre es una lista (vacía cuando no hay adicionales), ordenada.

Los GET usan EntityGraph para cargar la galería; PUT y movimientos la
inicializan dentro de su transacción. El mapeo no depende de Open Session
in View ni accede a una colección LAZY cerrada. No se serializa ProductoImagen
ni se devuelve su ID interno, producto_id o un objeto recursivo.
Las respuestas de movimientos también incluyen los campos nuevos; los
movimientos solo asignan stock. La baja lógica conserva la galería.

## Permisos y estados

Sin cambios de SecurityConfig ni Gateway. GET de productos públicos; POST,
PUT, DELETE y movimientos ADMINISTRADOR; RAWG ADMINISTRADOR.
401 sin JWT válido y 403 con CLIENTE/VENDEDOR en operaciones administrativas.

- POST: 201, 400 por validación, 409 por SKU duplicado.
- PUT: 200, 400, 404 si no existe con body válido, 409 por SKU/stock/bloqueo.
- GET: 200; ID inexistente/oculto: 404.
- DELETE: 204 para existente, incluso ya oculto.

## Pruebas HTTP/MySQL pendientes

Repetir por 9091 y 9090; las pruebas positivas escriben datos cuando el usuario
las ejecute. Esta tarea no las ejecutó.

1. Crear las cuatro categorías con cuerpos válidos; consultar por ID/lista/filtro
   y comparar todos los campos, precios derivados y orden de imágenes.
2. POST con SKU " con-001 " cuando CON-001 ya existe: 409, sin crear filas.
   Repetir con SKU de producto oculto. PUT con el propio SKU: 200.
3. SKU omitido/vacío/>64, campos de categoría ausentes, formato inválido,
   stockCritico negativo/fraccionario: 400.
4. URL principal/adicional Base64, relativa, vacía o null en lista, cinco
   adicionales: 400; lista vacía válida; URLs http y https válidas.
5. PUT con cuatro imágenes, reordenar y reemplazar por otras cuatro:
   200 sin orden duplicado. PUT [] elimina; omisión/null conserva.
6. PUT con stockEsperado antiguo y nueva galería/nombre/precio:
   409, GET debe confirmar que NO cambió ningún campo ni imagen.
7. Simultaneidad PUT/movimiento: mantener los resultados documentados en
   PRUEBAS_STOCK_CATALOGO.md; probar también que la galería se revierte en 409.
8. Con índice UNIQUE ya aplicado, dos altas simultáneas del mismo SKU:
   una 201, otra 409; comprobar rollback de la galería del alta fallida.
   Sin el índice no afirmar ni esperar esta garantía.
9. Permisos JWT y regresión de RAWG; no ejecutar importaciones.
