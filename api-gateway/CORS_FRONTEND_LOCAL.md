# CORS local para React/Vite

## Política implementada

Única configuración CORS en `config/SecurityConfig.java`, integrada mediante
ServerHttpSecurity.cors(). No hay un CorsWebFilter independiente, @CrossOrigin
ni globalcors duplicado en YAML.

- Orígenes exactos: `http://localhost:5173`, `http://127.0.0.1:5173`.
- Métodos: GET, POST, PUT, DELETE, OPTIONS.
- Encabezados solicitados permitidos: Authorization, Content-Type.
  Accept es un encabezado simple habitual; no hacen falta headers de roles,
  X-Requested-With ni nombres personalizados para las peticiones existentes.
- No cookies: allowCredentials=false. El frontend debe omitir
  `credentials: "include"`; puede usar `credentials: "omit"`.
- Caché de preflight: 600 segundos.
- Sin headers de respuesta adicionales expuestos.
- Se aplica a todas las rutas del Gateway, sin cambiar destinos ni permisos.
  El patrón de rutas `/**` NO es un comodín de orígenes.

El frontend consume **solo http://localhost:9090**. No se habilita CORS directo
en Identidad 9096 ni Catálogo 9091.

Esta configuración resuelve la ausencia de CORS señalada por el contrato
frontend anterior. No se modificó ese documento porque está fuera del alcance.

## OPTIONS y autenticación

Spring Security instala su procesamiento CORS antes de autenticación y
autorización. Un preflight válido (Origin + Access-Control-Request-Method)
se responde en Gateway sin pedir JWT ni enviarlo al servicio de destino.
La regla OPTIONS permitAll existente se conserva, pero no es por sí sola
la política CORS.

El preflight no ejecuta la operación real. El POST/PUT/DELETE posterior pasa
por las mismas reglas JWT/ADMINISTRADOR de antes. Para origen permitido:

- POST administrativo sin token: 401.
- Con CLIENTE/VENDEDOR: 403.
- Con ADMINISTRADOR: llega a la validación/operación correspondiente.
- GET público: no requiere token.
- RAWG: sigue requiriendo ADMINISTRADOR, incluidos sus GET.

La respuesta al preflight válido debe incluir:

```http
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS
Access-Control-Allow-Headers: authorization,content-type
Access-Control-Max-Age: 600
```

La capitalización y espacios pueden variar. Allow-Headers refleja los headers
solicitados y autorizados; si solo se solicita Authorization, puede devolver
solo ese header. No debe aparecer Allow-Credentials: true ni dos Allow-Origin.
Spring añade Vary para el tratamiento de CORS.

Un origen distinto (otro puerto, HTTPS, un dominio externo o Origin:null)
no pasa la política: se espera rechazo CORS 403 sin Allow-Origin para ese
origen. Lo mismo ocurre con un método/header no permitido en preflight.
CORS no protege frente a clientes no navegador: una petición sin Origin sigue
dependiendo de JWT y las reglas HTTP existentes.

PATCH no se incluyó por el alcance solicitado. La ruta de perfil PATCH que
ya existe mantiene su autorización de backend, pero no queda habilitada para
fetch cross-origin desde Vite mediante esta política.

## Comprobación de preflight pendiente

No se iniciaron servicios ni se ejecutaron estas solicitudes en esta tarea.
Después de iniciar el entorno local, desde una terminal puede comprobarse:

```powershell
curl.exe -i -X OPTIONS "http://localhost:9090/api/productos" `
  -H "Origin: http://localhost:5173" `
  -H "Access-Control-Request-Method: POST" `
  -H "Access-Control-Request-Headers: authorization,content-type"
```

Esperado: respuesta exitosa sin JWT (normalmente 200), cabeceras anteriores
y cuerpo vacío. Repetir con:

- Origin http://127.0.0.1:5173: permitido, reflejando exactamente ese origen.
- Origin http://localhost:5174: rechazado, sin Allow-Origin.
- Método PUT y DELETE: permitidos en preflight.
- Header X-Role: rechazado.
- URL /auth/login, método POST y header content-type: permitido.
- URL /api/productos/imagenes/subir, POST y header authorization: permitido.
- URL /api/productos/rawg/buscar, GET y header authorization: permitido.

## Verificación desde navegador/Vite

Abrir la aplicación desde uno de los dos orígenes permitidos, abrir DevTools
Network y ejecutar los siguientes ejemplos en su contexto. No usar file://
ni otra página. Sustituir el token con el obtenido en POST /auth/login.
No publicarlo en capturas ni registros.

### GET público

```javascript
const respuesta = await fetch("http://localhost:9090/api/productos", {
  credentials: "omit"
});
if (!respuesta.ok) throw new Error(`HTTP ${respuesta.status}`);
const productos = (await respuesta.json())._embedded?.productos ?? [];
```

Un GET simple puede no mostrar OPTIONS. Sí debe poder leerse su respuesta
por CORS y tener Allow-Origin con el origen exacto.

### POST JSON con JWT

Para verificar CORS/autorización sin crear un producto, enviar un body inválido:

```javascript
const respuesta = await fetch("http://localhost:9090/api/productos", {
  method: "POST",
  credentials: "omit",
  headers: {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json"
  },
  body: JSON.stringify({})
});
const error = await respuesta.json().catch(() => null);
```

Con ADMINISTRADOR se espera **400 de validación legible por JavaScript**,
no un bloqueo CORS. Con un cuerpo válido del contrato se espera 201 y se crea
un producto: ejecutar esa variante solo con datos de prueba acordados.
Sin token esperar 401 y con CLIENTE/VENDEDOR 403; el cuerpo puede estar vacío.
En Network comprobar OPTIONS exitoso y después el POST real.

### Subida multipart

Con un input file existente en React:

```javascript
async function subirImagen(archivo, token) {
  const form = new FormData();
  form.append("archivo", archivo);
  const respuesta = await fetch("http://localhost:9090/api/productos/imagenes/subir", {
    method: "POST",
    credentials: "omit",
    headers: { Authorization: `Bearer ${token}` },
    body: form
  });
  const data = await respuesta.json().catch(() => null);
  if (!respuesta.ok) throw new Error(data?.message ?? `HTTP ${respuesta.status}`);
  return data.imagenUrl;
}
```

No fijar Content-Type: el navegador añade multipart/form-data con boundary.
Authorization provoca preflight. Con ADMINISTRADOR y archivo admitido se
espera 201 y una imagenUrl legible. Esta prueba sí guarda un archivo local;
no se realizó durante la implementación. Mantiene límites JPEG/PNG/WebP y
5 MiB de la API existente.

Si no se ve OPTIONS, puede estar cacheado. Usar un perfil de navegador nuevo
o esperar el maxAge; no confundir caché de preflight con ausencia de validación.
No usar mode:"no-cors": impediría leer las respuestas y no resolvería JWT.

## Estado de verificación

Revisión estática de orden CORS/seguridad y permisos anteriores. Compilar Gateway
y ejecutar git diff --check no sustituye las comprobaciones de red anteriores.
La integración desde React/navegador queda pendiente de prueba real.
