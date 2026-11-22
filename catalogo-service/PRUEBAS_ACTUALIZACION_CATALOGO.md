# Edición administrativa: PUT /api/productos/{id}

Ejemplos preparados, no ejecutados durante la implementación.

## Contrato

**CAMBIO OBLIGATORIO DEL PUT:** ahora requiere `stockEsperado` (entero no
negativo), además de `stock`. Es el valor leído antes de editar, no el nuevo
stock deseado. Si falta/null/negativo: 400. Si no coincide con el stock actual
bajo bloqueo: 409, sin modificar ningún campo ni galería.
POST y PUT ahora también exigen codigo y campos específicos por categoría.
Véase `FORMULARIO_PRODUCTOS_HTTP.md` para el contrato actualizado.

Authorization: Bearer {{adminToken}} y Content-Type: application/json.
Usar baseUrl=http://localhost:9091 y repetir después con http://localhost:9090.
Gateway ya enruta /api/productos/** y permite PUT de un producto solo para
ADMINISTRADOR; no requiere cambios. Catálogo aplica también esa autorización.

Entrada: ProductoRequestDto, que hereda campos y validaciones de
ProductoCrearRequestDto. Son obligatorios codigo, nombre, categoria, precio, stock,
enOferta, habilitado y stockEsperado. En PUT deben enviarse explícitamente
(omitidos o null: 400); POST conserva sus defaults 0/false/true.

PUT representa el estado completo editable, no una edición parcial:
descripcion, marca, plataforma, genero, imagenUrl y precioOferta omitidos
quedan null. Para conservar un valor hay que reenviarlo. Las cuatro categorías
son válidas: VIDEOJUEGO exige formato/plataforma; CONSOLA ecosistema;
ACCESORIO compatibilidad; FIGURA franquicia.
Los otros escalares opcionales omitidos quedan null. La galería tiene una
semántica explícita: imagenes omitido/null la conserva, [] la elimina y una
lista de 1–4 URLs la reemplaza por completo en el orden enviado.
Precio positivo entero CLP, stock entero no negativo y oferta con precioOferta
positivo inferior a precio cuando está activa. Se reutilizan también límites
de longitud y rechazo de números fraccionarios configurados para POST.

El ID de la URL selecciona un registro existente mediante findByIdForUpdate
con bloqueo PESSIMISTIC_WRITE dentro de una transacción, también si
está deshabilitado. No se crea un producto si no existe. Ni id ni rawgId forman
parte del DTO ni se asignan en el mapeo; se conservan los valores persistidos.
La validación precede a save y la edición usa una transacción local. No hay
llamadas a Inventario. Habilitado no modifica stock: al alternarlo, reenviar
el stock actual para mantenerlo. Tampoco stock cero deshabilita el producto.

Respuesta 200: ProductoResponseDto (sin envoltorio HATEOAS), con todos los
campos actuales, precioEfectivo y disponible. 404 para ID inexistente con
entrada válida, 400 para datos inválidos, 401 sin JWT válido y 403 para
CLIENTE/VENDEDOR. El GET público de un producto oculto sigue devolviendo 404.
409 indica SKU duplicado, stock desactualizado o conflicto de bloqueo. Ante stock desactualizado
recargar y revisar la edición, no reemplazar automáticamente stockEsperado
para forzar un formulario viejo. No se modifica el esquema MySQL.

## 1. Editar un producto

Seleccionar un ID existente para {{productoId}} y registrar su stock,
habilitado, id y rawgId antes de la edición.

PUT `{{baseUrl}}/api/productos/{{productoId}}`

```json
{
  "codigo": "ACC-001",
  "nombre": "Mando inalámbrico actualizado",
  "categoria": "ACCESORIO",
  "descripcion": "Versión revisada del mando",
  "marca": "Marca de accesorios",
  "plataforma": null,
  "genero": null,
  "compatibilidad": "PC y PS5",
  "stockCritico": 2,
  "imagenUrl": "https://example.com/mando.jpg",
  "imagenes": ["https://example.com/mando-lateral.jpg", "http://example.com/mando-dorso.jpg"],
  "precio": 59990,
  "precioOferta": null,
  "enOferta": false,
  "stock": 7,
  "stockEsperado": 5,
  "habilitado": true
}
```

Este ejemplo supone stock actual=5: sustituir stockEsperado por el valor
real observado antes de editar. Esperado: 200, mismo id y rawgId, stock=7,
precioEfectivo=59990 y disponible=true.
Repetir sobre productos de VIDEOJUEGO, CONSOLA y FIGURA utilizando la categoría
correspondiente y sus campos obligatorios. VIDEOJUEGO necesita plataforma
y formato. Usar el SKU actual si se quiere conservarlo; ACC-001 es ilustrativo.

## 2. Modificar una oferta

Reenviar el cuerpo completo anterior con stockEsperado=7 (si el stock sigue
siendo 7) y cambiando los campos de oferta:

```json
{
  "precio": 59990,
  "precioOferta": 44990,
  "enOferta": true
}
```

Este fragmento NO es un body completo. Esperado: 200, precioEfectivo=44990.
Después reenviar el body completo con enOferta=false y precioOferta=null:
precioEfectivo vuelve a precio.

## 3. Deshabilitar y volver a habilitar

1. Reenviar el body completo con stock=7, stockEsperado=7 y habilitado=false.
2. Esperar 200 con stock=7 y disponible=false.
3. GET por ID: 404; el producto no aparece en el listado ni filtro público.
4. Sobre el MISMO ID, reenviar el mismo body con habilitado=true,
   stock=7 y stockEsperado=7.
5. Esperar 200 y volver a comprobar GET: 200; stock sigue siendo 7.
6. Reenviar body completo con stock=0, stockEsperado=7 y habilitado=true: sigue en los GET
   públicos, con disponible=false.

## 4. Errores y conservación de datos

- Sin Bearer: 401; con CLIENTE o VENDEDOR: 403, tanto en 9091 como 9090.
- ID confirmado inexistente + body válido: 404 y ninguna nueva fila.
- Categoría inválida/omitida, precio <= 0, stock < 0, valores fraccionarios,
  oferta activa sin precioOferta o precioOferta >= precio: 400.
- Omitir stock, stockEsperado, enOferta o habilitado: 400.
- Después de una entrada/salida, enviar el stockEsperado anterior: 409 sin
  cambiar nombre, precio, stock ni ningún otro campo.
- Tras un 400, consultar el producto para verificar que conserva sus valores.
- Comprobar que cambiar solamente habilitado en un body completo no altera
  el stock reenviado ni rawgId. Para un rawgId preexistente no nulo, su
  conservación se revisará cuando haya datos importados: no insertar ni
  modificar metadatos RAWG solo para esta tarea.

Las pruebas manuales de actualización sí modifican datos cuando el usuario las
ejecute. No se ejecutó SQL ni HTTP, ni se crearon pruebas unitarias nuevas.
