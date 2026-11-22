# Contrato de integración frontend — CheckPoint Store / Catálogo

Revisión del código: 21 de noviembre de 2026, rama `feature/catalogo-inventario`.
Destinatario: formulario dinámico React/Vite.

Este documento describe la implementación existente, no endpoints propuestos.
Los ejemplos de respuesta son ilustrativos; IDs, enlaces y datos reales varían.
El equipo confirmó validación funcional con Postman/MySQL. En esta revisión
documental no se iniciaron servicios ni se ejecutaron HTTP, SQL o pruebas de navegador.

**Antes de conectar React:** revisar la sección 8. No hay CORS configurado para
Vite en el código/configuración inspeccionados. Además, no existe una consulta
administrativa para listar/recuperar productos deshabilitados.

## 1. Configuración, autenticación y errores

### Base

- API a consumir: **http://localhost:9090** (Gateway).
- Catálogo directo: http://localhost:9091, útil para diagnóstico; el frontend
  debe usar Gateway salvo decisión explícita.
- Una variable Vite como `VITE_API_BASE_URL=http://localhost:9090` puede contener
  la URL pública, nunca RAWG_API_KEY, JWT_SECRET ni contraseñas de servicios.
- El lanzador de doble clic es `iniciar_checkpoint_local.bat`. Solicita MySQL
  y opcionalmente RAWG; al generar un JWT_SECRET nuevo invalida tokens anteriores.

### Login administrativo

Usar una cuenta ya existente con rol ADMINISTRADOR. No enviar un rol en el login
ni intentar obtenerlo con el registro público de clientes.

```http
POST http://localhost:9090/auth/login
Content-Type: application/json
```

```json
{
  "email": "administrador@example.com",
  "password": "CONTRASENA_DE_LA_CUENTA"
}
```

Valores ilustrativos, no credenciales configuradas. Email válido y password
no vacío son obligatorios. Respuesta 200:

```json
{"token": "<JWT>"}
```

El campo se llama **token**, no accessToken. En cada operación protegida:

```http
Authorization: Bearer <JWT>
```

El backend obtiene el rol del JWT firmado. No sirven X-Role ni roles enviados
en JSON. No es necesario enviar cookies con `credentials: "include"`.
No registrar tokens en consola. Para rutas públicas, omitir Authorization:
un Bearer inválido puede provocar 401 aunque la ruta sea pública.

### Matriz de permisos

Todas las rutas de la tabla parten de la base Gateway:

| Método y ruta | Acceso | Éxito |
| --- | --- | --- |
| POST /auth/login | Público | 200 |
| GET /api/productos | Público | 200 |
| GET /api/productos?categoria=CONSOLA | Público | 200 |
| GET /api/productos/{id} | Público | 200 |
| POST /api/productos | ADMINISTRADOR | 201 |
| PUT /api/productos/{id} | ADMINISTRADOR | 200 |
| DELETE /api/productos/{id} | ADMINISTRADOR | 204 sin cuerpo |
| POST /api/productos/{id}/stock/entradas | ADMINISTRADOR | 200 |
| POST /api/productos/{id}/stock/salidas | ADMINISTRADOR | 200 |
| POST /api/productos/imagenes/subir | ADMINISTRADOR | 201 |
| GET /api/productos/imagenes/{nombre} | Público | 200, imagen |
| GET /api/productos/rawg/buscar | ADMINISTRADOR | 200 |
| GET /api/productos/rawg/{rawgId} | ADMINISTRADOR | 200 |
| POST /api/productos/rawg/{rawgId}/importar | ADMINISTRADOR | 201 |

Estas reglas están presentes en Catálogo y Gateway. No hay PATCH de productos
implementado, aunque una regla de Catálogo contemple ese método.

### Estados y formato de errores

| HTTP | Interpretación para la UI |
| --- | --- |
| 400 | Entrada inválida, enum/formato incorrecto, imagen inválida |
| 401 | Falta JWT válido/expiró, o credenciales de login incorrectas |
| 403 | Usuario autenticado sin el rol necesario |
| 404 | Producto inexistente u oculto; imagen/juego RAWG inexistente |
| 409 | SKU/variante duplicados, stockEsperado desactualizado, stock insuficiente, límite de stock o conflicto de bloqueo |
| 413 | Subida demasiado grande |
| 500 | Error interno o almacenamiento local no accesible |
| 502/503/504 | Error controlado de RAWG; ver sección 6 |

Ejemplo de conflicto emitido por Catálogo:

```json
{
  "timestamp": "2026-11-21T20:00:00-03:00",
  "status": 409,
  "error": "Conflict",
  "message": "El stock cambió desde la consulta. Recargue el producto antes de editar.",
  "path": "/api/productos/12"
}
```

Los errores de validación tienen la misma estructura, normalmente con mensaje
general; **no existe un mapa `fieldErrors` por campo**. Los errores RAWG añaden
`code`. Los 401/403 de filtros de seguridad y errores del Gateway no garantizan
ese JSON y pueden tener cuerpo vacío. No ejecutar `response.json()` a ciegas
para todo error ni para DELETE 204.

## 2. Contrato JSON común de POST y PUT

Enviar `Content-Type: application/json`. No envolver en `{producto: ...}`.
Usar números JSON y booleanos reales; no cadenas de un formulario HTML.
Los Integer admiten hasta 2.147.483.647. Los decimales se rechazan, no se
truncan. Enums son strings exactos en mayúsculas; números como categoría
o formato se rechazan.

### Campos

| Nombre exacto | Tipo JSON | Regla |
| --- | --- | --- |
| codigo | string | Obligatorio, no vacío, máximo 64 después de normalizar |
| nombre | string | Obligatorio, no vacío, máximo 255 |
| categoria | string enum | VIDEOJUEGO, CONSOLA, ACCESORIO o FIGURA; obligatorio |
| descripcionCorta | string/null | Opcional, máximo 500 |
| descripcion | string/null | Descripción larga opcional; persiste en TEXT |
| marca | string/null | Opcional, máximo 100 |
| plataforma | string/null | Máximo 255; obligatoria para VIDEOJUEGO |
| genero | string/null | Opcional, máximo 255; es texto, no array |
| formato | string/null enum | FISICO o DIGITAL; obligatorio para VIDEOJUEGO |
| ecosistema | string/null | Máximo 100; obligatorio para CONSOLA |
| compatibilidad | string/null | Máximo 255; obligatoria para ACCESORIO |
| franquicia | string/null | Máximo 255; obligatoria para FIGURA |
| precio | number entero | Obligatorio, >0, CLP final |
| precioOferta | number entero/null | Opcional; si existe, >0 |
| enOferta | boolean | POST: false si se omite; PUT: obligatorio |
| stock | number entero | >=0; POST: 0 si se omite; PUT: obligatorio |
| stockCritico | number entero/null | Opcional, >=0 |
| habilitado | boolean | POST: true si se omite; PUT: obligatorio |
| imagenUrl | string/null | Portada opcional; URL absoluta http/https, máximo 2048 |
| imagenes | array de strings/null | Entre 0 y 4 URLs adicionales, máximo 2048 cada una |
| stockEsperado | number entero | Solo PUT, obligatorio >=0; stock leído al abrir/recargar |

`descripcion` no tiene @Size en el DTO, pero TEXT tiene límite físico de MySQL
(65.535 bytes); no tratarla como contenido ilimitado. Los campos de descripción
manual no se sanitizan como RAWG: mostrarlos como texto React, no como HTML.

No enviar `id`, `rawgId`, `precioEfectivo`, `disponible`, `_links` o `_embedded`
en POST/PUT: son salida, no campos editables. Construir el body con lista
explícita de campos; no reenviar toda la respuesta GET sin filtrarla.
El alta manual siempre tiene rawgId=null; PUT conserva el rawgId existente.

### Obligatoriedad por categoría

| Categoría | Además de los campos comunes |
| --- | --- |
| VIDEOJUEGO | formato y plataforma no vacía |
| CONSOLA | ecosistema no vacío |
| ACCESORIO | compatibilidad no vacía |
| FIGURA | franquicia no vacía |

Los otros atributos pueden ser null. VIDEOJUEGO permite genero, ecosistema
y franquicia opcionales; ACCESORIO permite ecosistema/franquicia opcionales.
El backend no elimina automáticamente atributos enviados por no corresponder
a la categoría: el formulario decide qué conservar o limpiar.

### Valores, defaults, null y omisión

- codigo se recorta con strip y se convierte a mayúsculas Locale.ROOT:
  `" con-001 "` → `"CON-001"`. No se genera SKU ni se deduce del ID/categoría.
- La unicidad incluye productos ocultos. PUT permite mantener el SKU propio.
  Los índices MySQL preparados usan collation insensible a mayúsculas/acentos.
  Rechazo: 409; el frontend no puede garantizar disponibilidad solo consultando.
- Nombre y demás textos manuales no reciben esa normalización de SKU.
- POST: omitir stock/enOferta/habilitado da 0/false/true; enviar null da 400.
- PUT: los tres campos anteriores deben enviarse junto con stockEsperado.
- Escalares opcionales omitidos o null quedan null en POST y PUT. PUT no es
  PATCH: puede borrar campos opcionales que no se reenvíen.
- Excepción: `imagenes` omitido/null en PUT conserva la galería; `[]` la quita.
  En POST, omitido/null crea galería vacía.
- URL opcional vacía `""` es inválida; usar null. En `imagenes` no se admiten
  elementos null/vacíos, Base64, rutas relativas, credenciales ni espacios.
  Se exige esquema `http`/`https` en minúsculas y host válido.
- Lista de adicionales independiente de imagenUrl: máximo **1 principal + 4
  adicionales**. No duplicar la principal en el array por convención de UI.
- Con enOferta=true, precioOferta debe existir, ser positivo y menor que precio.
  Con enOferta=false puede conservarse precioOferta positivo, pero no se aplica.
- precioEfectivo = enOferta ? precioOferta : precio. **No añadir 19 % de IVA**.
- stockCritico es un umbral informativo; no cambia stock, habilitado ni disponible.
- Stock cero significa agotado; no equivale a oculto.

### Cuatro ejemplos completos de creación

Todos se envían a POST `/api/productos` con JWT ADMINISTRADOR.
Elegir SKU libres; los ejemplos no son instrucciones para duplicar datos
de prueba existentes. Las URLs example.com ilustran sintaxis, no archivos
garantizados; pueden sustituirse por URLs reales o devueltas por subida.

#### VIDEOJUEGO

```json
{
  "codigo": "JUE-101",
  "nombre": "Videojuego de aventura",
  "categoria": "VIDEOJUEGO",
  "descripcionCorta": "Aventura para PS5",
  "descripcion": "Edición física del videojuego.",
  "marca": "Editorial de videojuegos",
  "plataforma": "PS5",
  "genero": "Aventura",
  "formato": "FISICO",
  "ecosistema": "PlayStation",
  "compatibilidad": null,
  "franquicia": null,
  "precio": 59990,
  "precioOferta": 44990,
  "enOferta": true,
  "stock": 5,
  "stockCritico": 2,
  "habilitado": true,
  "imagenUrl": "https://example.com/juego.jpg",
  "imagenes": ["https://example.com/juego-dorso.jpg"]
}
```

#### CONSOLA

```json
{
  "codigo": "CON-101",
  "nombre": "Consola de sobremesa",
  "categoria": "CONSOLA",
  "descripcionCorta": "Consola con mando",
  "descripcion": "Incluye consola, mando y cables.",
  "marca": "Marca de consolas",
  "plataforma": null,
  "genero": null,
  "formato": null,
  "ecosistema": "PlayStation",
  "compatibilidad": null,
  "franquicia": null,
  "precio": 499990,
  "precioOferta": null,
  "enOferta": false,
  "stock": 3,
  "stockCritico": 1,
  "habilitado": true,
  "imagenUrl": "https://example.com/consola.jpg",
  "imagenes": ["https://example.com/consola-lateral.jpg"]
}
```

#### ACCESORIO

```json
{
  "codigo": "ACC-101",
  "nombre": "Mando inalámbrico",
  "categoria": "ACCESORIO",
  "descripcionCorta": "Mando compatible con PC y PS5",
  "descripcion": "Conexión inalámbrica y por cable.",
  "marca": "Marca de accesorios",
  "plataforma": null,
  "genero": null,
  "formato": null,
  "ecosistema": "PlayStation",
  "compatibilidad": "PC y PS5",
  "franquicia": null,
  "precio": 49990,
  "precioOferta": null,
  "enOferta": false,
  "stock": 0,
  "stockCritico": 2,
  "habilitado": true,
  "imagenUrl": "http://example.com/mando.jpg",
  "imagenes": []
}
```

#### FIGURA

```json
{
  "codigo": "FIG-101",
  "nombre": "Figura de colección",
  "categoria": "FIGURA",
  "descripcionCorta": "Figura decorativa",
  "descripcion": "Figura con base de exposición.",
  "marca": "Fabricante de figuras",
  "plataforma": null,
  "genero": null,
  "formato": null,
  "ecosistema": null,
  "compatibilidad": null,
  "franquicia": "The Legend of Zelda",
  "precio": 29990,
  "precioOferta": null,
  "enOferta": false,
  "stock": 2,
  "stockCritico": null,
  "habilitado": true,
  "imagenUrl": "https://example.com/figura.jpg",
  "imagenes": [
    "https://example.com/figura-frente.jpg",
    "https://example.com/figura-dorso.jpg"
  ]
}
```

## 3. Respuestas, consulta, edición y baja

### ProductoResponseDto

POST manual, PUT, movimientos de stock e importación devuelven el objeto plano
con todos los campos de salida siguientes, sin stockEsperado:

```json
{
  "id": 12,
  "nombre": "Videojuego de aventura",
  "categoria": "VIDEOJUEGO",
  "descripcion": "Edición física del videojuego.",
  "marca": "Editorial de videojuegos",
  "plataforma": "PS5",
  "genero": "Aventura",
  "imagenUrl": "https://example.com/juego.jpg",
  "precio": 59990,
  "precioOferta": 44990,
  "enOferta": true,
  "stock": 5,
  "habilitado": true,
  "rawgId": null,
  "precioEfectivo": 44990,
  "disponible": true,
  "codigo": "JUE-101",
  "descripcionCorta": "Aventura para PS5",
  "formato": "FISICO",
  "ecosistema": "PlayStation",
  "compatibilidad": null,
  "franquicia": null,
  "stockCritico": 2,
  "imagenes": ["https://example.com/juego-dorso.jpg"]
}
```

`id` es el identificador MySQL local; `rawgId` es externo y nullable.
`imagenes` es un array de URL ordenadas, nunca entidades con producto_id/orden.
`disponible = habilitado && stock > 0`. No hay campo de alerta de stock crítico
calculado por el backend.

### GET y HATEOAS

- GET `/api/productos`: todos los habilitados; no tiene paginación implementada.
- GET `/api/productos?categoria=CONSOLA`: mismo contrato filtrado.
- GET `/api/productos/{id}`: producto habilitado o 404.
- Categoría inválida: 400. No usar `categoria=juego` ni `categoria=todos`;
  para todas las categorías, omitir el parámetro.

GET por ID devuelve **los mismos campos del objeto completo anterior en el
nivel raíz**, añadiendo:

```json
{
  "_links": {
    "self": {"href": "http://localhost:9090/api/productos/12"},
    "todos-los-productos": {"href": "http://localhost:9090/api/productos"}
  }
}
```

El bloque anterior muestra únicamente la parte añadida, no la respuesta completa.
No existe un envoltorio `data` o `content`.

GET de lista usa esta estructura (objeto de producto abreviado solo en este
ejemplo; en ejecución cada elemento incluye todos los campos):

```json
{
  "_embedded": {
    "productos": [
      {
        "id": 12,
        "codigo": "JUE-101",
        "nombre": "Videojuego de aventura",
        "_links": {
          "self": {"href": "http://localhost:9090/api/productos/12"},
          "todos-los-productos": {"href": "http://localhost:9090/api/productos"}
        }
      }
    ]
  },
  "_links": {
    "self": {"href": "http://localhost:9090/api/productos"}
  }
}
```

En una colección vacía, consumir defensivamente:

```javascript
const productos = respuesta._embedded?.productos ?? [];
```

El controlador proporciona el tipo de colección para conservar la relación
`productos`. No asumir que la respuesta de lista es un array directo.
Los href los genera Spring HATEOAS según la petición y cabeceras reenviadas:
el origen mostrado es ilustrativo. Para evitar depender de cambios de proxy,
el frontend puede construir las rutas conocidas con su base y `id`.

### Productos ocultos y agotados

Los GET excluyen habilitado=false **incluso con JWT ADMINISTRADOR**.
No existe `?incluirDeshabilitados=true` ni endpoint administrativo alternativo
de lectura. Esta es una limitación concreta para una pantalla de gestión:
después de ocultar y recargar, no se puede recuperar la ficha por esos GET.
PUT sí admite el ID de un producto oculto, pero requiere el estado completo
y stockEsperado; no inventar esos datos. Resolver una consulta administrativa
en otra tarea si la UI necesita listar/editar/reactivar ocultos.

Con stock=0 y habilitado=true el producto sigue en GET, disponible=false.
La UI puede mostrar «Agotado» sin tratarlo como eliminado.

### PUT y stockEsperado

PUT `/api/productos/{id}` devuelve 200 con ProductoResponseDto plano.
Reenviar todos los campos que se quieran conservar. Ejemplo completo sobre
la consola de prueba, suponiendo stock actual=3:

```json
{
  "codigo": "CON-001",
  "nombre": "Consola de prueba",
  "categoria": "CONSOLA",
  "descripcionCorta": "Consola con mando",
  "descripcion": "Descripción larga actualizada.",
  "marca": "Marca de consolas",
  "plataforma": null,
  "genero": null,
  "formato": null,
  "ecosistema": "PlayStation",
  "compatibilidad": null,
  "franquicia": null,
  "precio": 499990,
  "precioOferta": null,
  "enOferta": false,
  "stock": 4,
  "stockEsperado": 3,
  "stockCritico": 1,
  "habilitado": true,
  "imagenUrl": "https://example.com/consola.jpg",
  "imagenes": ["https://example.com/consola-lateral.jpg"]
}
```

Guardar el stock leído en una variable independiente del campo stock editable:

```javascript
const stockLeido = producto.stock; // al cargar el formulario
// body contiene solo campos del DTO, elegidos explícitamente:
const bodyPut = { ...camposEditables, stockEsperado: stockLeido };
```

Si stock actual != stockEsperado, devuelve 409 antes de cambiar atributos o
galería. Recargar y pedir al usuario revisar; no sustituir automáticamente
stockEsperado para forzar un formulario antiguo. Después de un PUT exitoso,
actualizar formulario y stockLeido desde la respuesta.
Este mecanismo protege el saldo, no es una versión general: dos ediciones
de texto con stock idéntico no producen necesariamente conflicto.

| `imagenes` en PUT | Efecto |
| --- | --- |
| Omitido o null | Conserva adicionales |
| [] | Quita todas las adicionales |
| 1–4 URLs | Reemplaza todas, en el orden del array |

`imagenUrl=null` quita solo la referencia principal. El reemplazo de galería
es transaccional; no elimina archivos físicos. Un error revierte la escritura.
SKU duplicado, variante duplicada, stock desactualizado o bloqueo pueden
producir 409. Con body válido e ID inexistente: 404.

### DELETE y movimientos

DELETE `/api/productos/{id}` deshabilita, no borra filas ni cambia stock,
precios, rawgId o imágenes. 204 sin cuerpo si existe, aunque ya estuviera
oculto; 404 si no existe. No llamar response.json() después del 204.

POST `/api/productos/{id}/stock/entradas` o `/salidas`, con:

```json
{"cantidad": 2}
```

Cantidad Integer positiva obligatoria. 200 con ProductoResponseDto; 400
inválida, 404 inexistente, 409 insuficiente/desbordamiento/bloqueo.
Son ajustes administrativos, permiten productos ocultos y solo cambian stock.
No son una API de compra. No son idempotentes: repetir una petición exitosa
aplica otro movimiento; evitar doble envío.

## 4. Imágenes: archivo local o URL externa

### Flujo del formulario

1. El usuario selecciona un File.
2. Enviar un FormData con un único campo File `archivo`.
3. POST `/api/productos/imagenes/subir` con Bearer ADMINISTRADOR.
4. Recibir 201 con `{ "imagenUrl": "http://localhost:9090/api/productos/imagenes/<uuid>.png" }`.
5. Colocar esa URL en `imagenUrl` (principal) o en `imagenes` (adicionales).
6. Enviar POST/PUT normal en JSON. La subida por sí sola no crea/vincula productos.

No fijar Content-Type al enviar FormData; el navegador debe generar el boundary.

### Límites y representación

- Una imagen por solicitud, JPEG/PNG/WebP reales, verificados mediante decoder.
- Entrada máxima **5 MiB = 5.242.880 bytes**; request multipart máximo 6 MiB.
- Resolución máxima 12.000.000 píxeles; salida procesada máxima 20 MiB.
- Todas se convierten a PNG: la URL termina .png y GET devuelve image/png.
- Se descartan metadatos; no se garantiza conservar animación/orientación EXIF.
- Campo ausente/vacío o formato inválido: 400; límite de entrada/request: 413;
  dimensiones/salida procesada excesivas: 400; fallo de almacenamiento: 500.
- SVG/GIF/documentos no están admitidos por la subida.
- GET público `/api/productos/imagenes/{nombre}` sirve solo el nombre generado,
  con nosniff y caché pública de un día. No necesita token.

Una URL externa HTTP/HTTPS válida puede escribirse directamente en el formulario.
No se descarga ni valida su existencia en el CRUD. No enviar Base64, data:,
blob: ni rutas `C:\...`; blob: sirve únicamente para una vista previa local
que no se persiste.

### Ejemplo React mínimo

Este ejemplo presupone CORS resuelto o un proxy de desarrollo de mismo origen
(sección 8). No se ha probado en navegador en esta revisión.

```jsx
import { useState } from "react";

const API = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:9090";

export function SubirImagen({ token, onSubida }) {
  const [subiendo, setSubiendo] = useState(false);
  const [error, setError] = useState("");

  async function seleccionar(event) {
    const input = event.currentTarget;
    const archivo = input.files?.[0];
    if (!archivo) return;
    setError("");
    if (archivo.size > 5 * 1024 * 1024) {
      setError("El máximo es 5 MiB.");
      input.value = "";
      return;
    }
    setSubiendo(true);
    try {
      const form = new FormData();
      form.append("archivo", archivo);
      const response = await fetch(`${API}/api/productos/imagenes/subir`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
        body: form
      });
      const data = await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(data?.message ?? `Error HTTP ${response.status}`);
      }
      if (!data?.imagenUrl) throw new Error("Respuesta de subida incompleta");
      onSubida(data.imagenUrl);
    } catch (e) {
      setError(e instanceof Error ? e.message : "No se pudo subir la imagen");
    } finally {
      setSubiendo(false);
      input.value = "";
    }
  }

  return (
    <div>
      <input type="file" accept="image/jpeg,image/png,image/webp"
        disabled={subiendo} onChange={seleccionar} />
      {subiendo && <p>Subiendo…</p>}
      {error && <p role="alert">{error}</p>}
    </div>
  );
}
```

Ejemplo de integración: `onSubida={url => setImagenUrl(url)}` para portada;
para adicionales, añadir a la lista solo si tiene menos de cuatro elementos.
El atributo accept es ayuda de UI, no reemplaza las validaciones backend.

### Almacenamiento y despliegue

Actualmente los bytes se guardan fuera de Git, en carpeta local configurable:
`CATALOGO_IMAGENES_DIR`. El lanzador usa
`%LOCALAPPDATA%\CheckPointStore\imagenes`; sin él, el fallback es
`${user.home}/CheckPointStore/imagenes`. La URL se genera con
`CATALOGO_PUBLIC_BASE_URL` (por defecto http://localhost:9090).

Reiniciar no borra la carpeta. Reemplazar referencias, cancelar un formulario
o deshabilitar productos no elimina archivos: no hay limpieza de huérfanos.
La base almacena URL, no binarios. Una URL externa RAWG no está en esa carpeta.

AWS/S3 es futuro, no existe contrato de presigned URL ni subida directa a S3.
Al desplegar hay que definir almacenamiento persistente y origen público HTTPS;
localhost de una URL guardada apunta al equipo del visitante. Cambiar la variable
de base pública no reescribe URL antiguas. La UI debe conservar la URL devuelta,
sin construir nombres físicos o asumir que siempre será local.

## 5. RAWG: buscar, previsualizar e importar variantes

Todos estos endpoints requieren ADMINISTRADOR. RAWG_API_KEY solo existe en el
backend; nunca incluirla en React, variables VITE_ ni requests al Gateway.

### Búsqueda

GET `/api/productos/rawg/buscar?query=portal&page=1`

- query obligatorio, no vacío tras strip, máximo 100 caracteres.
- page entero positivo, por defecto 1.
- pageSize fijo 20, no configurable por el frontend.
- Construir query mediante URLSearchParams para codificar espacios y símbolos.

Respuesta 200 ilustrativa:

```json
{
  "resultados": [
    {
      "rawgId": 4200,
      "nombre": "Portal 2",
      "imagenUrl": null,
      "plataformas": ["PC"],
      "yaImportado": true
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1,
  "haySiguiente": false
}
```

Sin HATEOAS, next/previous externos ni URLs con claves.

### Previsualización

GET `/api/productos/rawg/{rawgId}`, ID externo entero positivo:

```json
{
  "rawgId": 4200,
  "nombre": "Portal 2",
  "descripcion": "Descripción del videojuego.",
  "imagenUrl": null,
  "generos": ["Puzzle"],
  "plataformas": ["PC"],
  "yaImportado": true
}
```

Metadatos opcionales pueden ser null y listas vacías. `generos` y `plataformas`
son arrays en RAWG; en un Producto local `genero` y `plataforma` son strings.
No hay precios ni stock en la previsualización.
La descripción se extrae como texto y se escapan caracteres HTML residuales;
puede contener entidades como `&amp;`. Renderizar como texto, no usar
dangerouslySetInnerHTML ni desescapar para ejecutarlo como HTML.

### Importación

POST `/api/productos/rawg/{rawgId}/importar`

```json
{
  "codigo": "JUE-RAWG-101",
  "plataforma": "PC",
  "formato": "DIGITAL",
  "precio": 29990,
  "precioOferta": null,
  "enOferta": false,
  "stock": 5,
  "stockCritico": 2,
  "habilitado": true
}
```

Obligatorios: codigo, plataforma concreta no vacía, formato, precio y stock.
**Stock no tiene default en importación**, a diferencia del POST manual.
enOferta/habilitado omitidos: false/true; null explícito: 400.
stockCritico y precioOferta son opcionales; mismas reglas comerciales del CRUD.

Este DTO es estricto: **cualquier campo adicional produce 400**, incluidos
rawgId en body, nombre, categoria, descripción, imágenes y género.
No enviar el objeto completo de previsualización.

| Origen | Datos guardados |
| --- | --- |
| Servidor local | Nuevo id, categoria fija VIDEOJUEGO |
| RAWG | rawgId de la ruta verificado, nombre, descripción sanitizada, imagenUrl, géneros unidos por coma |
| Administrador | SKU, plataforma concreta, formato, precio, precioOferta, enOferta, stock, stockCritico, habilitado |

Galería adicional inicialmente vacía; otros campos no mapeados como marca y
descripcionCorta empiezan null. La imagen RAWG se conserva como URL, sin descarga.
La respuesta 201 es ProductoResponseDto completo y plano. Guardar su **id local**
para editar; no usar rawgId como ID de PUT/DELETE/movimientos.

Una importación no sincroniza el producto periódicamente. Después se edita
con el PUT normal y stockEsperado, y se pueden añadir imágenes o deshabilitarlo.

### Variantes y duplicados

- SKU único para todos los productos, incluidos ocultos.
- Un mismo rawgId puede tener PC/DIGITAL, PC/FISICO y PS5/FISICO con diferentes
  SKU, precios y stock.
- UNIQUE compuesto: rawgId + plataforma + formato; no UNIQUE(rawgId).
- Plataforma importada: espacios Unicode consecutivos → uno, quitar bordes,
  mayúsculas Locale.ROOT. PUT de un importado aplica esa misma normalización.
- La collation MySQL configurada es insensible a mayúsculas/acentos.
- No se resuelven alias: PS5 y PLAYSTATION 5 son etiquetas diferentes.
  Mantener un catálogo de etiquetas coherente en la UI; el backend no comprueba
  pertenencia literal a la lista de plataformas devuelta por RAWG.
- `yaImportado=true` indica que **alguna** variante existe, incluso oculta.
  No deshabilitar el botón de importar todas las variantes por ese booleano.
- Un duplicado devuelve 409; otra variante válida con SKU libre, 201.
- Los productos manuales con rawgId=null no compiten por la clave de variante.

La garantía concurrente depende de los índices MySQL de SKU y variante
instalados según los scripts del proyecto. La UI debe manejar 409 de todas
formas. Si un importado cambia de categoría mediante PUT, rawgId no se elimina:
debe seguir conservando plataforma/formato por su identidad de variante.

### Atribución RAWG

En cada página que use datos o imágenes RAWG, mostrar atribución visible con
enlace activo, incluidas búsqueda/previsualización y fichas/listados locales
que utilicen metadatos importados. Ejemplo:

```jsx
<a href="https://rawg.io/" target="_blank" rel="noopener noreferrer">
  Datos e imágenes: RAWG
</a>
```

`rawgId !== null` permite identificar productos importados. La referencia
oficial es https://rawg.io/apidocs. El acceso a imágenes no equivale a una
licencia universal sobre sus derechos; revisar plan/condiciones antes de un
uso comercial real. No redistribuir la base RAWG como servicio independiente.

## 6. Errores RAWG y tratamiento en UI

Ejemplo de error (la clave nunca aparece):

```json
{
  "timestamp": "2026-11-21T20:00:00-03:00",
  "status": 503,
  "error": "Service Unavailable",
  "code": "RAWG_NO_CONFIGURADO",
  "message": "La integración RAWG no tiene clave configurada.",
  "path": "/api/productos/rawg/buscar"
}
```

| code | HTTP |
| --- | --- |
| RAWG_NO_CONFIGURADO | 503 |
| RAWG_CLAVE_RECHAZADA | 502 (no confundir con JWT de usuario) |
| RAWG_CUOTA_AGOTADA | 503 (cuota o limitación externa 429) |
| RAWG_TIMEOUT | 504 |
| RAWG_CONEXION / RAWG_NO_DISPONIBLE | 502 |
| RAWG_RESPUESTA_INVALIDA / RAWG_METADATOS_INVALIDOS | 502 |
| RAWG_INTERRUPCION | 503 |
| RAWG_NO_ENCONTRADO | 404 |
| RAWG_PAGINA_INEXISTENTE | 400 |

No reintentar importaciones automáticamente con otro SKU tras un timeout:
consultar el estado del catálogo primero. Una repetición puede devolver 409
si ya se guardó la primera solicitud. Los fallos de consulta RAWG antes del
guardado no crean un producto parcial.

## 7. Adaptación desde el antiguo db.json

No reenviar los objetos antiguos sin transformarlos. Tabla de conversión:

| Frontend/db.json antiguo | Backend actual | Transformación |
| --- | --- | --- |
| categoria: juego | categoria: VIDEOJUEGO | Mapeo explícito |
| categoria: consola | categoria: CONSOLA | Mapeo explícito |
| categoria: accesorio | categoria: ACCESORIO | Mapeo explícito |
| categoria: figura | categoria: FIGURA | Mapeo explícito |
| codigo | codigo | String obligatorio; backend recorta y pasa a mayúsculas |
| imagen | imagenUrl | URL absoluta http/https o null |
| imagenes | imagenes | Array de URLs adicionales, no objetos; máximo cuatro |
| descripcionCorta | descripcionCorta | Mismo nombre, opcional, máximo 500 |
| descripcionLarga | descripcion | Renombrar; no existe descripcionLarga en el DTO |
| formato: físico/fisico | formato: FISICO | Mapeo explícito; no solo uppercase de “físico” |
| formato: digital | formato: DIGITAL | Mapeo explícito |
| ecosistema | ecosistema | String, obligatorio para CONSOLA |
| plataforma / genero | plataforma / genero | Strings; no arrays |
| compatibilidad / franquicia | mismos nombres | Completar según categoría |
| precio/stock como texto de input | precio/stock como number | Validar entero; no convertir vacío en 0 accidentalmente |
| id local de db.json | id de respuesta MySQL | No enviarlo como ID de creación ni asumir coincidencia |
| datos sin stockEsperado | stockEsperado en PUT | Capturar stock de GET, no inferir del stock nuevo |

Si el array antiguo incluye también la portada, separar explícitamente la
principal de las adicionales antes de enviar. La conversión no sube archivos:
rutas relativas o rutas del computador necesitan el flujo FormData o una URL
pública válida. No inventar defaults de campos obligatorios específicos.

Ejemplo de mapas, sin modificar db.json:

```javascript
const categorias = {
  juego: "VIDEOJUEGO", consola: "CONSOLA",
  accesorio: "ACCESORIO", figura: "FIGURA"
};
const formatos = { fisico: "FISICO", "físico": "FISICO", digital: "DIGITAL" };
```

## 8. CORS: incompatibilidad actual con Vite directo

Se inspeccionaron SecurityConfig de Gateway y Catálogo, controladores,
configuraciones Java y recursos application.yml/properties y ejemplos.
No se encontró CorsConfigurationSource, CorsWebFilter, configuración MVC/WebFlux
de CORS, @CrossOrigin, globalcors ni allow-origin configurado.

Ambas seguridades permiten OPTIONS, pero eso **no añade**
Access-Control-Allow-Origin/Methods/Headers ni autoriza un origen.
http://localhost:5173 y http://localhost:9090 son orígenes distintos.
También http://127.0.0.1:5173 es distinto de http://localhost:5173.

| Solicitud desde Vite a Gateway | Evaluación estática actual |
| --- | --- |
| GET público mediante fetch | Aunque no necesite preflight, falta autorización CORS para leer la respuesta |
| POST /auth/login con application/json | Requiere preflight; no hay política CORS configurada |
| POST JSON administrativo | Preflight por JSON/Authorization, no autorizado mediante una política CORS |
| PUT / DELETE con Bearer | Preflight, misma incompatibilidad |
| GET RAWG con Bearer | Authorization provoca preflight, misma incompatibilidad |
| POST multipart con Bearer | FormData no evita el preflight de Authorization |
| GET directo a Catálogo 9091 | Tampoco hay CORS configurado allí |

Conclusión: **no declarar compatible ni validada la integración fetch directa
desde localhost:5173 con la configuración inspeccionada**. Un JWT correcto
no resuelve CORS. Postman no aplica estas restricciones del navegador.
En producción podría existir un proxy externo con su propia política; no hay
evidencia de ello en esta revisión y no se puede asumir.

Una etiqueta `<img src="...">` normalmente puede mostrar una imagen pública
cross-origin sin fetch ni Bearer. Eso no autoriza leerla por fetch ni usar sus
píxeles en canvas sin CORS. Servir frontend HTTPS con imágenes HTTP también
puede causar contenido mixto, independiente de JWT.

### Resolución pendiente, fuera de esta tarea

Acordar una política CORS en Gateway para el origen exacto de Vite y producción,
incluyendo GET/POST/PUT/DELETE/OPTIONS y headers Authorization/Content-Type.
La petición real y el preflight deben recibir cabeceras coherentes. Si se
permitirá consumo directo por 9091, deberá resolverse también en Catálogo.
No se modificó esa configuración aquí.

Para desarrollo se puede acordar alternativamente un proxy Vite de `/api`
y `/auth` hacia 9090 y usar fetch relativo (base vacía): el navegador vería
mismo origen. No está implementado ni verificado en esta tarea. `mode:"no-cors"`
no es solución: produce respuestas opacas y limita métodos/headers.

Comprobar después en DevTools: login, GET, POST, PUT, DELETE, subida FormData
y RAWG, con sus OPTIONS y respuestas reales. Un error CORS puede impedir leer
el status/body y presentarse como TypeError de fetch.

## 9. Lista de entrega al compañero y fuentes verificadas

Antes de conectar el formulario:

1. Resolver CORS o acordar proxy Vite; probar navegador, no solo Postman.
2. Usar DTO explícito y mapas de categorías/formato; números/booleanos JSON.
3. Separar stock leído del stock editable y manejar 409 sin sobrescritura ciega.
4. Decidir cómo gestionará la UI productos ocultos: no hay GET administrativo
   para recuperarlos después de recargar.
5. Subir File antes de guardar JSON; conservar URLs y limitar adicionales a 4.
6. No bloquear nuevas variantes por yaImportado; mostrar atribución RAWG.
7. No asumir errores por campo, enlaces con origen fijo ni cuerpo JSON en 204/401/403.

Fuentes de código revisadas (rutas relativas al backend):

- `catalogo-service/src/main/java/com/ferreteria/catalogo_service/Dto/`:
  ProductoCrearRequestDto, ProductoRequestDto, ProductoResponseDto,
  RawgDtos, RawgImportacionRequestDto.
- `catalogo-service/.../controller/`: ProductoController, ImagenController,
  RawgController.
- `catalogo-service/.../service/`: ProductoService, ImagenService, RawgService,
  RawgImportacionService.
- `catalogo-service/.../repository/ProductoRepository.java`,
  `assembler/ProductoModelAssembler.java`, `exception/GlobalExceptionHandler.java`.
- `catalogo-service/.../config/`: SecurityConfig, JsonConfig, ImagenMultipartConfig.
- `api-gateway/src/main/java/com/ferreteria/api_gateway/config/SecurityConfig.java`
  y `api-gateway/src/main/resources/` (configuración y ejemplo).
- Para login: `identidad-service/.../Dto/LoginRequestDto.java` y
  `controller/UserController.java`, solo lectura.

Único artefacto creado en esta tarea: este documento. No se modificaron CORS,
seguridad, DTO, endpoints, frontend, db.json ni esquema.
