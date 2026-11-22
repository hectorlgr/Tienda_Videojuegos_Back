# Subida local de imágenes — contrato y pruebas pendientes

Implementación compilada y revisada estáticamente. No se iniciaron servicios,
no se ejecutó SQL ni se realizaron subidas/consultas HTTP en esta tarea.
No se crearon productos ni archivos de imagen de prueba.

## Contrato

POST `/api/productos/imagenes/subir`

- JWT ADMINISTRADOR, tanto directamente en 9091 como por Gateway 9090.
- Content-Type multipart/form-data (boundary generado por Postman/navegador).
- Un solo archivo por solicitud, campo `archivo`.
- JPEG, PNG o WebP; máximo de entrada 5 MiB (5.242.880 bytes).
- Solicitud multipart máxima 6 MiB, incluido boundary/cabeceras.
- Máximo 12 millones de píxeles; salida PNG máxima 20 MiB.

Respuesta 201 ilustrativa:

```json
{
  "imagenUrl": "http://localhost:9090/api/productos/imagenes/6d839659-64ab-4f65-880d-8a163a5d108e.png"
}
```

El UUID se genera en backend; el nombre original nunca se usa para nombrar
archivos ni se devuelve/registra. La subida no vincula la imagen a ningún
producto ni accede a MySQL.

GET público `/api/productos/imagenes/{nombre}`

- Devuelve bytes con Content-Type image/png, Content-Disposition inline,
  X-Content-Type-Options nosniff y caché pública de un día.
- Solo admite nombre UUID en minúsculas seguido de .png; no permite separadores,
  rutas absolutas, .. ni nombres arbitrarios.
- No sirve enlaces simbólicos de archivo ni listados de la carpeta.
- No expone la ruta del sistema ni ejecuta contenido.

La URL generada usa la base configurada, no Host ni X-Forwarded-Host enviados
por el cliente. La misma ruta GET funciona directamente en 9091 y por 9090.
Gateway ya tiene destino /api/productos/** hacia Catálogo: solo se añadieron
dos reglas de seguridad, POST subir ADMINISTRADOR y GET imagen público.
En Catálogo se explicitan esos mismos permisos. No se abrieron PUT/DELETE
de archivos ni se cambiaron permisos de productos/stock/RAWG.

## Validación real del formato

ImageIO identifica un lector a partir de bytes y decodifica la imagen:
no se confía en extensión ni Content-Type declarado. Solo se admiten lectores
JPEG/PNG/WebP. Se rechazan errores y advertencias de decodificación.

Java no tiene lector WebP integrado: se añadió únicamente
com.twelvemonkeys.imageio:imageio-webp:3.12.0 y sus dependencias transitivas.
Se usa para leer, no se requiere codificador WebP.

Todas las entradas válidas se copian a un raster nuevo y se reescriben como PNG.
Por tanto, una entrada JPEG/WebP también produce URL .png y MIME image/png.
Esto descarta metadatos EXIF/texto y contenido anexo; no se almacena el archivo
original. Puede cambiar el tamaño, y no se conserva orientación EXIF.
Para formatos con múltiples cuadros se procesa solo el primer cuadro que
el lector soporte: no se garantiza conservar animaciones.
SVG, GIF, PDF, texto y un archivo renombrado que no decodifique se rechazan.

## Almacenamiento y lanzador de doble clic

Variables de entorno configurables:

| Variable | Uso |
| --- | --- |
| CATALOGO_IMAGENES_DIR | Ruta absoluta de la carpeta persistente |
| CATALOGO_PUBLIC_BASE_URL | Origen HTTP/HTTPS público; puede incluir prefijo de despliegue |

`iniciar_checkpoint_local.bat` sigue invocando
`iniciar_identidad.ps1 -IncluirCatalogo`. Solo se ajustó el auxiliar:

- Por defecto establece CATALOGO_IMAGENES_DIR en
  `%LOCALAPPDATA%\CheckPointStore\imagenes`.
- Por defecto establece CATALOGO_PUBLIC_BASE_URL=http://localhost:9090.
- Respeta valores externos si ya estaban configurados.
- Entrega esas variables a Catálogo, las retira antes de iniciar Gateway
  y restaura el entorno del lanzador al finalizar.

No hace falta editar archivos ni definir rutas en cada sesión.
La carpeta se crea cuando se realiza la primera subida válida; si no hay
permisos para crear/escribir, responde con error controlado.

Fuera del lanzador, el servicio tiene como fallback
`${user.home}/CheckPointStore/imagenes` y base pública http://localhost:9090.
Configurar explícitamente ambas variables al desplegar; elegir una carpeta
dedicada fuera del repositorio y recursos empaquetados, accesible solo para
la cuenta del servicio. No colocar allí archivos ajenos.

La carpeta no se limpia en startup/shutdown ni durante Maven clean:
es externa a target/src y usa una ruta estable, por lo que está diseñada para
persistir entre reinicios. La recuperación efectiva tras reiniciar aún debe
verificarse con las pruebas HTTP siguientes.
No depende de recursos estáticos empaquetados ni contiene secretos.

Se escribe primero un temporal propio en esa misma carpeta y luego se mueve
al nombre definitivo sin REPLACE_EXISTING. Si falla, se intenta limpiar solo
ese temporal. Nunca se sobrescribe un archivo existente.

## Integración con JSON y URLs externas

React/Postman sube el archivo, recibe imagenUrl y la utiliza sin cambios en
el contrato existente. Fragmento para incorporar al JSON completo:

```json
{
  "imagenUrl": "{{imagenSubida}}",
  "imagenes": [
    "{{imagenAdicionalSubida}}",
    "https://example.com/imagen-externa.jpg"
  ]
}
```

La subida sirve para portada y para cada adicional. El máximo de cuatro
adicionales lo siguen controlando DTO y tabla producto_imagenes al guardar
el producto; el endpoint de subida no conoce ese límite por producto.

- URL local subida: bytes almacenados en la carpeta configurada, servidos
  por Catálogo/Gateway; la base de datos solo recibe su URL al guardar JSON.
- URL externa (incluida RAWG): se conserva como referencia; no se descarga
  ni copia automáticamente en la carpeta.
- Base64/data: sigue rechazado por los DTO existentes; no se modificaron.

Si la base pública cambia, las URL absolutas ya guardadas no se reescriben.
Mantener un origen estable o planificar ese cambio por separado.

## Errores

| Caso | Estado |
| --- | --- |
| Sin JWT válido | 401 |
| CLIENTE/VENDEDOR | 403 |
| Campo archivo ausente/vacío, multipart mal formado, más de un archivo | 400 |
| Formato no admitido, datos inválidos o límite de píxeles/salida excedido | 400 |
| Archivo >5 MiB o request multipart >6 MiB | 413 |
| Imagen no encontrada/nombre inválido | 404 |
| Fallo de almacenamiento | 500 controlado, sin rutas internas |

Usar multipart/form-data: otro Content-Type no forma parte del contrato.
Los errores usan ApiErrorResponse, sin trazas internas ni contenido del archivo.
Los rechazos de rutas malformadas/traversal pueden ocurrir antes del controlador
por seguridad HTTP (400/403/404); nunca deben entregar archivos.

## Prueba Postman completa pendiente

1. Arrancar con el BAT habitual; obtener JWT ADMINISTRADOR.
2. POST `http://localhost:9090/api/productos/imagenes/subir`.
   Authorization: Bearer {{adminToken}}. Body > form-data:
   clave `archivo`, tipo **File**, elegir un JPEG/PNG/WebP válido menor de 5 MiB.
   No fijar manualmente Content-Type: Postman genera el boundary.
3. Esperar 201. Guardar la URL:

   ```javascript
   pm.expect(pm.response.code).to.eql(201);
   pm.environment.set("imagenSubida", pm.response.json().imagenUrl);
   ```

4. GET `{{imagenSubida}}` sin Authorization: 200 y Content-Type image/png;
   comprobar imagen visible y nosniff. Repetir la misma ruta con puerto 9091.
5. Repetir la subida para una imagen adicional y guardar otra variable.
6. Solo al autorizar creación de datos de prueba, utilizar las URLs en uno
   de los JSON completos de FORMULARIO_PRODUCTOS_HTTP.md, con SKU libre
   y campos obligatorios de categoría. POST de producto: 201; GET devuelve
   las mismas URL y conserva su orden. No usar CON-001 para un alta nueva.
7. Reiniciar los servicios, mantener la carpeta y repetir GET de la URL:
   debe seguir mostrando la imagen. Maven clean tampoco debe borrarla.
8. Repetir la subida directamente en 9091 y verificar permisos en ambos puertos:
   sin token/expirado 401; CLIENTE y VENDEDOR 403.
9. Probar campo ausente, vacío, dos archivos, texto renombrado .png,
   PDF/SVG/GIF, imagen truncada y Content-Type falso con datos inválidos: 400.
   Una imagen real con extensión equivocada puede aceptarse por su contenido.
10. Probar JPEG, PNG y WebP reales; >5 MiB: 413; dimensiones excesivas: 400.
11. UUID inexistente, ../, rutas codificadas y nombres externos: ningún acceso
    al sistema de archivos fuera de las imágenes generadas.
12. Probar fallo de escritura en un entorno controlado con una carpeta sin
    permisos: error genérico, sin ruta, sin archivo definitivo parcial.

## Limitación deliberada

No hay eliminación física ni limpieza automática de huérfanos. Subir y luego
cancelar el formulario, reemplazar una URL o borrar lógicamente un producto
deja el archivo almacenado. Tampoco hay cuotas totales de disco ni sincronización
entre varias instancias. Son tareas posteriores; AWS/S3 no se implementa aquí.
