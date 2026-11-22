# Arranque local de CheckPoint Store en Windows

## Doble clic

1. Tener MySQL iniciado y la configuración/esquema local ya preparados como antes.
2. Hacer doble clic en `iniciar_checkpoint_local.bat`.
3. Introducir la contraseña MySQL cuando se solicite (entrada oculta).
4. Si el proceso ya heredó una `RAWG_API_KEY` no vacía, se reutiliza sin pedirla
   ni mostrar su valor. En otro caso aparece:
   `RAWG API key (entrada oculta; Enter para omitir)`.
5. Introducir la clave o pulsar Enter para continuar sin RAWG.
   Una entrada que solo contiene espacios también se considera omitida.
6. Esperar el mensaje de entorno disponible. Se abren las ventanas de Eureka,
   Identidad, Catálogo y Gateway. Acceder mediante http://localhost:9090.
7. Iniciar sesión otra vez en Postman: se mantiene la generación existente de
   un JWT_SECRET nuevo y compartido en cada arranque.

No es necesario abrir PowerShell ni configurar variables antes del doble clic.
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

La configuración de MySQL, JWT compartido y almacenamiento local de imágenes
se conserva. No se vuelve a solicitar ningún dato existente.
`iniciar_identidad.bat` mantiene su ruta anterior sin `-IncluirCatalogo`:
no solicita RAWG ni cambia su entorno RAWG.

## Detener

En las ventanas de servicio, pulsar Ctrl+C en este orden:

1. Gateway.
2. Catálogo.
3. Identidad.
4. Eureka.

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

No inspeccionar/imprimir el entorno completo ni volcar claves para verificarlo.
