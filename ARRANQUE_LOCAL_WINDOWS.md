# Arranque local de CheckPoint Store en Windows

## Doble clic

1. Tener MySQL iniciado y la configuración/esquema local ya preparados como antes.
2. Hacer doble clic en `iniciar_checkpoint_local.bat`.
3. Introducir las dos contraseñas MySQL (entrada oculta, una vez por cuenta):
   - `checkpoint_app`: compartida por Identidad y Catálogo.
   - `checkpoint_ventas_app`: exclusiva de Ventas; puede ser diferente.
4. Si el proceso ya heredó una `RAWG_API_KEY` no vacía, se reutiliza sin pedirla
   ni mostrar su valor. En otro caso aparece:
   `RAWG API key (entrada oculta; Enter para omitir)`.
5. Introducir la clave o pulsar Enter para continuar sin RAWG.
   Una entrada que solo contiene espacios también se considera omitida.
6. Esperar el mensaje de entorno disponible. Se abren las ventanas de Eureka,
    Identidad, Catálogo, Ventas y Gateway, en ese orden. Acceder mediante
    http://localhost:9090.
7. Iniciar sesión otra vez en React/Postman: se genera un `JWT_SECRET` nuevo,
   compartido por Identidad, Catálogo, Ventas y Gateway, en cada arranque.

No es necesario abrir PowerShell ni configurar variables antes del doble clic.
También se genera `STOCK_SERVICE_JWT_SECRET`, diferente del humano, compartido
solo por Catálogo y Ventas. Si el proceso ya heredó uno válido (al menos 32 bytes
UTF-8 y no solo espacios), se reutiliza. Uno heredado inválido detiene el arranque
antes de abrir servicios. No se solicita ni muestra ninguno de los dos secretos.
Ventas solo recibe la variable para una integración futura: no emite tokens de servicio.
Si se omite RAWG, el CRUD local funciona normalmente; los endpoints RAWG
devolverán su error controlado de clave no configurada.
El lanzador no valida la clave contra RAWG ni realiza importaciones.
Una clave rechazada se detectará al usar los endpoints RAWG.
Para proporcionar otra clave, detener y volver a iniciar los servicios.

## Alcance de la clave

- Se reutiliza el entorno **heredado por el proceso** que abre el BAT.
  Una variable establecida en otra terminal independiente no llega al
  Explorador de Windows ni a un doble clic iniciado desde él.
- Solo `-IncluirCatalogo` obtiene y gestiona la clave. Se guarda el valor
  previo en memoria y se retira del entorno antes de iniciar los otros servicios.
- La entrada manual usa Read-Host -AsSecureString. Solo se convierte a texto
  al preparar el entorno de Catálogo; el BSTR se libera con ZeroFreeBSTR.
- Start-Process entrega una copia del entorno a cmd/Maven/Java de Catálogo.
  No incluye la clave en argumentos, títulos ni mensajes.
- Después de crear ese proceso se limpia RAWG_API_KEY del entorno del
  lanzador, incluso si falla el inicio; Gateway no la hereda.
- El finally exterior libera la entrada oculta si se interrumpió antes y
  restaura el entorno previo del proceso. Una clave introducida manualmente
  no queda definida para el siguiente arranque.
- No se escriben secretos en archivos ni se usan setx ni variables User/Machine.
  La clave permanece en el entorno del proceso de Catálogo mientras este viva.

Se conservan el almacenamiento local de imágenes y las URLs por Gateway.
Las bases y esquemas deben estar preparados: `ddl-auto=validate` no crea tablas.
La cuenta de Ventas conserva sus permisos SELECT; el lanzador no cambia permisos.
`iniciar_identidad.bat` mantiene su ruta anterior sin `-IncluirCatalogo`:
no solicita RAWG ni cambia su entorno RAWG.

## Detener

En las ventanas de servicio, pulsar Ctrl+C en este orden:

1. Gateway.
2. Ventas.
3. Catálogo.
4. Identidad.
5. Eureka.

Si cmd pregunta si desea terminar el trabajo por lotes, confirmar y cerrar
la ventana. Cerrar solo el lanzador no detiene los servicios.
Para el lanzador anterior de Identidad: Gateway, Identidad y Eureka.

## Verificación

Revisión estática de herencia, ramas y limpieza; parser PowerShell y
git diff --check. No se ejecutaron servicios ni se probó un arranque real.
Pendiente comprobar mediante doble clic:

- Sin variable previa: entrada oculta con clave y consulta RAWG posterior.
- Sin variable previa: Enter vacío y CRUD disponible.
- Variable previamente heredada: reutilización sin pregunta.
- Lanzador de Identidad: ninguna pregunta RAWG.
- Detención y nuevo arranque: no se conserva una clave introducida manualmente.
- Eureka en http://127.0.0.1:8761 muestra `VENTA-SERVICE` en UP, puerto 9094,
  además de Identidad, Catálogo y Gateway.
- Las dos contraseñas distintas permiten arrancar los servicios correspondientes.
- Ventas expone su documentación en `/api/ventas/v3/api-docs`, por 9094 y 9090.
- Login nuevo funciona para las consultas permitidas de Ventas; token humano
  del arranque anterior ya no autentica.
- Un fallo de conexión/esquema informa la etapa y deja las ventanas para revisión;
  detener todas antes de reintentar.

No inspeccionar/imprimir el entorno completo ni volcar claves para verificarlo.
Detalles de variables, orden y configuración: [ARRANQUE_CHECKPOINT_LOCAL.md](ARRANQUE_CHECKPOINT_LOCAL.md).
