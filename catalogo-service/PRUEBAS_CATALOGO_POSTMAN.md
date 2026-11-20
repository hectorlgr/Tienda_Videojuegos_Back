# Creación y consulta de Catálogo

Ejemplos preparados para ejecutar manualmente después de revisar los cambios.
No se ejecutaron peticiones ni inserciones durante la implementación.

## Contratos

- POST `/api/productos`: 201, objeto ProductoResponseDto sin envoltorio HATEOAS.
- GET `/api/productos`: 200, colección HATEOAS con `_embedded.productos` y `_links`.
- GET `/api/productos?categoria=VIDEOJUEGO`: misma estructura, filtrada.
- GET `/api/productos/{id}`: 200, ProductoResponseDto con `_links`; 404 si
  no existe o está deshabilitado.

Entrada: nombre, categoria, descripcion, marca, plataforma, genero, imagenUrl,
precio, precioOferta, enOferta, stock y habilitado.
nombre/categoria/precio son obligatorios. Si se omiten stock/enOferta/habilitado,
se usan 0/false/true; enviarlos como null se rechaza. Marca y metadatos son
opcionales en las cuatro categorías. precioOferta es opcional pero, si existe,
debe ser positivo; con enOferta=true debe ser inferior a precio.
No hay id ni rawgId en el DTO de entrada: el servidor no los mapea desde el JSON.
MySQL genera id y rawgId queda null en las altas manuales.

Salida: id, nombre, categoria, descripcion, marca, plataforma, genero, imagenUrl,
precio, precioOferta, enOferta, stock, habilitado, rawgId, precioEfectivo y
disponible. precioEfectivo usa el cálculo de Producto y no sustituye los precios
almacenados. disponible = habilitado && stock > 0.

La consulta pública siempre filtra habilitado=true, incluso con filtro de
categoría. Stock cero no oculta el producto. La lista vacía usa la relación
HATEOAS `productos`. Categoria inválida, ID no numérico, JSON inválido, enum
numérico, precio/stock fraccionarios y errores de validación devuelven 400.
La deserialización rechaza decimales para los Integer, sin truncarlos.
Los errores usan ApiErrorResponse (timestamp, status, error, message, path);
no se devuelve la excepción interna, SQL ni traza.

## Configuración Postman y seguridad pendiente

Variables: `baseUrl=http://localhost:9090`, `adminToken` con JWT ADMINISTRADOR.
POST: Content-Type application/json y Authorization: Bearer {{adminToken}}.
GET: sin Authorization.

El Gateway conserva GET público y POST/PUT/DELETE restringidos a ADMINISTRADOR.
No se cambiaron sus reglas. Catálogo no incorpora Spring Security ni validación
JWT propia: si 9091 es accesible, un POST directo puede evitar el Gateway y
crearse sin JWT. Esto se deduce del código; no se probó mediante peticiones.
Resolver la protección del acceso directo antes del despliegue (seguridad del
servicio y/o aislamiento de red según la arquitectura acordada).

Para una prueba local aislada se puede usar baseUrl=http://localhost:9091,
pero eso NO valida los permisos del Gateway. El POST y los GET no llaman
a inventario-service. El PUT y DELETE heredados siguen pendientes de adaptación;
no forman parte de esta prueba ni se amplió su funcionalidad.

## Cuatro POST de ejemplo

Para cada cuerpo, POST `{{baseUrl}}/api/productos`. Esperado: 201 con id generado
y categoria. Guardar los IDs devueltos; no asumir una secuencia fija.

### Videojuego con oferta

```json
{
  "nombre": "Videojuego de aventura",
  "categoria": "VIDEOJUEGO",
  "descripcion": "Edición física",
  "marca": "Editorial de juegos",
  "plataforma": "PS5",
  "genero": "Aventura",
  "imagenUrl": "https://example.com/videojuego.jpg",
  "precio": 59990,
  "precioOferta": 44990,
  "enOferta": true,
  "stock": 5,
  "habilitado": true
}
```

Esperado: precioEfectivo=44990 y disponible=true.

### Consola

```json
{
  "nombre": "Consola de sobremesa",
  "categoria": "CONSOLA",
  "descripcion": "Consola con mando",
  "precio": 499990,
  "stock": 3
}
```

Esperado: enOferta=false, habilitado=true, precioEfectivo=499990.

### Accesorio agotado

```json
{
  "nombre": "Mando inalámbrico",
  "categoria": "ACCESORIO",
  "precio": 49990
}
```

Esperado: stock=0, habilitado=true, disponible=false; sigue visible en GET.

### Figura oculta

```json
{
  "nombre": "Figura de colección",
  "categoria": "FIGURA",
  "precio": 29990,
  "stock": 2,
  "habilitado": false
}
```

Esperado: 201, disponible=false. Su ID devuelve 404 en GET y no aparece
en listados públicos, tampoco con categoria=FIGURA.

## GET y casos negativos

1. GET `{{baseUrl}}/api/productos`: solo habilitados, incluyendo el accesorio agotado.
2. GET `{{baseUrl}}/api/productos?categoria=VIDEOJUEGO`.
3. Repetir el filtro con CONSOLA, ACCESORIO y FIGURA.
4. GET `{{baseUrl}}/api/productos/{{productoId}}`: campos y valores derivados.
5. GET de un ID confirmado inexistente: 404.
6. GET `{{baseUrl}}/api/productos?categoria=OTRA`: 400.
7. POST con nombre vacío, categoria omitida/OTRA, precio cero/negativo,
   stock negativo, flags null: 400.
8. POST con precio=10.5 o stock=1.5: 400, sin truncamiento.
9. POST con oferta activa sin precioOferta, o con precioOferta >= precio: 400.
10. POST con enOferta=false y precioOferta positivo: precioEfectivo=precio.
11. POST con stock=0 y habilitado=true: permanece habilitado.
12. Por Gateway, POST sin token: 401; con JWT CLIENTE o VENDEDOR válido: 403.

La revisión estática y compilación no sustituyen estas verificaciones
funcionales. No se crearon pruebas unitarias nuevas ni se ejecutó la suite
heredada; su adaptación a los DTO y firmas nuevos queda pendiente.
