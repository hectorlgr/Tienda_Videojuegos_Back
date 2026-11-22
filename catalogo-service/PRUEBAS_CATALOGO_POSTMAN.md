# Creación y consulta de Catálogo

Ejemplos preparados para ejecutar manualmente después de revisar los cambios.
No se ejecutaron peticiones ni inserciones durante la implementación.

## Contratos

- POST `/api/productos`: 201, objeto ProductoResponseDto sin envoltorio HATEOAS.
- GET `/api/productos`: 200, colección HATEOAS con `_embedded.productos` y `_links`.
- GET `/api/productos?categoria=VIDEOJUEGO`: misma estructura, filtrada.
- GET `/api/productos/{id}`: 200, ProductoResponseDto con `_links`; 404 si
  no existe o está deshabilitado.

Contrato completo y reglas de imágenes: `FORMULARIO_PRODUCTOS_HTTP.md`.
Entrada: codigo, nombre, categoria, descripcionCorta, descripcion, marca,
plataforma, genero, formato, ecosistema, compatibilidad, franquicia, imagenUrl,
imagenes, precio, precioOferta, enOferta, stock, stockCritico y habilitado.
codigo/nombre/categoria/precio son obligatorios. Si se omiten stock/enOferta/habilitado,
se usan 0/false/true; enviarlos como null se rechaza. Marca y metadatos son
opcionales salvo: VIDEOJUEGO exige formato/plataforma; CONSOLA ecosistema;
ACCESORIO compatibilidad; FIGURA franquicia. precioOferta es opcional pero, si existe,
debe ser positivo; con enOferta=true debe ser inferior a precio.
No hay id ni rawgId en el DTO de entrada: el servidor no los mapea desde el JSON.
MySQL genera id y rawgId queda null en las altas manuales.

Salida: id, nombre, categoria, descripcion, marca, plataforma, genero, imagenUrl,
precio, precioOferta, enOferta, stock, habilitado, rawgId, precioEfectivo y
disponible; incluye además codigo, descripcionCorta, formato, ecosistema,
compatibilidad, franquicia, stockCritico e imagenes ordenadas.
precioEfectivo usa el cálculo de Producto y no sustituye los precios
almacenados. disponible = habilitado && stock > 0.

La consulta pública siempre filtra habilitado=true, incluso con filtro de
categoría. Stock cero no oculta el producto. La lista vacía usa la relación
HATEOAS `productos`. Categoria inválida, ID no numérico, JSON inválido, enum
numérico, precio/stock fraccionarios y errores de validación devuelven 400.
La deserialización rechaza decimales para los Integer, sin truncarlos.
Los errores usan ApiErrorResponse (timestamp, status, error, message, path);
no se devuelve la excepción interna, SQL ni traza.

## Configuración Postman y seguridad JWT

Variables: `baseUrl=http://localhost:9090`, `adminToken` con JWT ADMINISTRADOR.
POST: Content-Type application/json y Authorization: Bearer {{adminToken}}.
GET: sin Authorization.

El Gateway conserva GET público y POST/PUT/DELETE restringidos a ADMINISTRADOR.
Se añadieron únicamente los dos POST administrativos de movimientos de stock.
Catálogo valida JWT HS256 también en 9091 y
exige ADMINISTRADOR para POST/PUT/PATCH/DELETE bajo /api/productos.
El resto de rutas se deniega salvo las excepciones públicas documentadas en
`SEGURIDAD_CATALOGO.md`. Las cabeceras de rol del cliente no conceden permisos.
La autorización HTTP de esta nueva configuración está pendiente de prueba.

Para una prueba local aislada se puede usar baseUrl=http://localhost:9091;
también requiere Bearer ADMINISTRADOR para escribir. Eso no valida los permisos
del Gateway: repetir después con 9090. El POST y los GET no llaman
a inventario-service. El PUT administrativo tampoco llama a Inventario;
sus ejemplos están en `PRUEBAS_ACTUALIZACION_CATALOGO.md`.
DELETE realiza baja lógica local; su comprobación se detalla al final.

## Cuatro POST de ejemplo

Para cada cuerpo, POST `{{baseUrl}}/api/productos`. Esperado: 201 con id generado
y categoria. Guardar los IDs devueltos; no asumir una secuencia fija.

### Videojuego con oferta

```json
{
  "codigo": "JUE-001",
  "nombre": "Videojuego de aventura",
  "categoria": "VIDEOJUEGO",
  "descripcion": "Edición física",
  "marca": "Editorial de juegos",
  "plataforma": "PS5",
  "genero": "Aventura",
  "formato": "FISICO",
  "descripcionCorta": "Aventura para PS5",
  "stockCritico": 2,
  "imagenUrl": "https://example.com/videojuego.jpg",
  "imagenes": ["https://example.com/juego-frente.jpg", "https://example.com/juego-dorso.jpg"],
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
  "codigo": "CON-002",
  "nombre": "Consola de sobremesa",
  "categoria": "CONSOLA",
  "descripcion": "Consola con mando",
  "ecosistema": "PlayStation",
  "imagenes": ["https://example.com/consola-lateral.jpg"],
  "precio": 499990,
  "stock": 3
}
```

Esperado: enOferta=false, habilitado=true, precioEfectivo=499990.

### Accesorio agotado

```json
{
  "codigo": "ACC-001",
  "nombre": "Mando inalámbrico",
  "categoria": "ACCESORIO",
  "compatibilidad": "PC y PS5",
  "ecosistema": "PlayStation",
  "imagenUrl": "http://example.com/mando.jpg",
  "imagenes": [],
  "precio": 49990
}
```

Esperado: stock=0, habilitado=true, disponible=false; sigue visible en GET.

### Figura oculta

```json
{
  "codigo": "FIG-001",
  "nombre": "Figura de colección",
  "categoria": "FIGURA",
  "franquicia": "The Legend of Zelda",
  "imagenes": ["https://example.com/figura-lateral.jpg"],
  "precio": 29990,
  "stock": 2,
  "habilitado": false
}
```

Esperado: 201, disponible=false. Su ID devuelve 404 en GET y no aparece
en listados públicos, tampoco con categoria=FIGURA.

Los SKU son ilustrativos: elegir valores libres antes de ejecutar.
CON-001 ya pertenece a la consola existente; no usarlo para otro POST.
Los dominios example.com son ejemplos de formato; no se verifica ni descarga
la imagen remota. El POST puede guardar esas URLs sin garantizar que existan.
SKU duplicado (incluido producto oculto): 409. La protección concurrente
requiere activar antes el índice de 04_exigir_producto_completo.sql.

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

## DELETE: baja lógica local (pruebas pendientes)

No se ejecutaron estas peticiones durante la implementación. Elegir un producto
de prueba existente y habilitado; no hace falta crear nuevos registros.
Primero baseUrl=http://localhost:9091, después repetir con 9090.

1. GET `{{baseUrl}}/api/productos/{{productoId}}`: guardar los datos originales,
   incluidos id, categoria, precios, oferta, stock, rawgId y metadatos.
2. DELETE de esa misma URL, sin body, con Bearer ADMINISTRADOR:
   esperar **204 y cuerpo vacío**.
3. Repetir exactamente el DELETE: esperar **204 y cuerpo vacío**. El producto
   ya deshabilitado no se vuelve a modificar.
4. GET por ID: **404**. Listado y filtro de categoría no deben incluirlo.
5. Para comprobar conservación antes de reactivarlo, revisar la fila mediante
   una consulta de solo lectura en Workbench:

   ```sql
   SELECT * FROM checkpoint_store_catalogo.productos WHERE id = <ID_DE_PRUEBA>;
   ```

   Sustituir el marcador por el ID real. Debe existir la misma fila y únicamente
   habilitado debe haber cambiado a 0; stock, oferta y demás valores permanecen.
   Esta comprobación antes del PUT evita que la reactivación oculte un cambio
   accidental al reenviar valores.
6. PUT con el estado completo original, stockEsperado igual al stock observado
   y habilitado=true: **200**. El GET vuelve
   a devolver **200**. No enviar id ni rawgId en el DTO de edición.
7. DELETE de un ID confirmado inexistente, con administrador: **404**.
8. DELETE de un producto habilitado sin token o con JWT inválido: **401**.
   Con CLIENTE o VENDEDOR: **403**. Verificar mediante GET que sigue habilitado.

El servicio busca con findByIdForUpdate y trabaja en una transacción local. Solo asigna
habilitado=false cuando estaba activo; no borra filas ni cambia stock, y no
necesita que inventario-service esté iniciado. La protección de Gateway y
Catálogo permanece ADMINISTRADOR. Las pruebas manuales de DELETE/PUT sí
cambiarán la visibilidad cuando se ejecuten; no se ejecutaron en esta tarea.
