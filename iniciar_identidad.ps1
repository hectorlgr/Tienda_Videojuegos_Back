# Invocado por iniciar_servicios.bat identidad. Compatible con Windows PowerShell 5.1.
param([switch] $IncluirCatalogo)

$ErrorActionPreference = 'Stop'
$repo = $PSScriptRoot

function Test-Port([int] $Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $pending = $client.ConnectAsync('127.0.0.1', $Port)
        return ($pending.Wait(1500) -and $client.Connected)
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Wait-Ready([string] $Label, [scriptblock] $Check) {
    $script:stage = $Label
    Write-Host "Esperando $Label (hasta 180 segundos)..."
    $deadline = [DateTime]::UtcNow.AddSeconds(180)
    do {
        try {
            if (& $Check) { return }
        } catch {
            # No imprimir respuestas ni excepciones que puedan contener configuracion.
        }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Tiempo agotado esperando $Label. Revisa su ventana de logs."
}

function Test-Http([string] $Url) {
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
    return ($response.StatusCode -eq 200)
}

function Test-Registration([string] $Name) {
    $registration = Invoke-RestMethod -Uri "http://127.0.0.1:8761/eureka/apps/$Name" `
        -Headers @{ Accept = 'application/json' } -TimeoutSec 3
    return (@($registration.application.instance | Where-Object { $_.status -eq 'UP' }).Count -gt 0)
}

function Start-ServiceWindow([string] $Module, [int] $Port) {
    $script:stage = "$Module ($Port)"
    if ($IncluirCatalogo) {
        $env:SERVER_PORT = [string] $Port
        $env:SERVER_ADDRESS = '127.0.0.1'
        $env:SPRING_APPLICATION_NAME = $Module
        # Configuracion por proceso: no sobrescribir archivos privados.
        # Las rutas son relativas al WorkingDirectory del modulo.
        $env:SPRING_CONFIG_LOCATION = switch ($Module) {
            'eureka-server' { 'file:./src/main/resources/application.properties' }
            'api-gateway' { 'file:./src/main/resources/application.yml.example[.yaml]' }
            default { 'file:./src/main/resources/application.properties.example[.properties]' }
        }
    }
    # Solo argumentos publicos. cmd hereda el entorno del proceso PowerShell.
    # WorkingDirectory evita interpolar rutas con espacios en el comando Maven.
    $command = "title $Module - $Port & mvnw.cmd `"-Dmaven.test.skip=true`" spring-boot:run"
    Start-Process -FilePath $env:ComSpec -WorkingDirectory (Join-Path $repo $Module) `
        -ArgumentList @('/d', '/k', $command) -WindowStyle Normal | Out-Null
}

$names = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET', 'STOCK_SERVICE_JWT_SECRET',
    'BOOTSTRAP_ADMIN_ENABLED', 'EUREKA_URL')
if ($IncluirCatalogo) {
    $names += @('CATALOGO_DB_USERNAME', 'CATALOGO_DB_PASSWORD',
        'VENTAS_DB_USERNAME', 'VENTAS_DB_PASSWORD', 'CHECKOUT_VERSION_CONDICIONES',
        'RAWG_API_KEY',
        'CATALOGO_IMAGENES_DIR', 'CATALOGO_PUBLIC_BASE_URL',
        'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_CONFIG_IMPORT',
        'SPRING_PROFILES_ACTIVE', 'SPRING_PROFILES_INCLUDE', 'SPRING_APPLICATION_JSON',
        'SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD',
        'SPRING_JPA_HIBERNATE_DDL_AUTO', 'SPRING_SQL_INIT_MODE',
        'SPRING_LIQUIBASE_ENABLED', 'SPRING_FLYWAY_ENABLED',
        'EUREKA_CLIENT_ENABLED', 'EUREKA_CLIENT_REGISTERWITHEUREKA', 'EUREKA_CLIENT_FETCHREGISTRY',
        'EUREKA_CLIENT_SERVICEURL_DEFAULTZONE',
        'SERVER_PORT', 'SERVER_ADDRESS', 'SPRING_APPLICATION_NAME',
        'SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_SECRETKEY')
}
$previous = @{}
foreach ($name in $names) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$password = $null
$ventasPassword = $null
$stockSecret = $null
$stage = 'comprobaciones iniciales'
$rawgSecret = $null
$rng = $null
$exitCode = 0
try {
    if ($IncluirCatalogo) {
        Write-Host 'CheckPoint Store: Eureka + Identidad + Catalogo + Ventas + Gateway'
        Write-Host 'Detener: Ctrl+C en Gateway, Ventas, Catalogo, Identidad y Eureka, en ese orden.'
        # Neutralizar overrides de otra sesion; restaurarlos al terminar.
        foreach ($name in $names) {
            [Environment]::SetEnvironmentVariable($name, $null, 'Process')
        }
        $env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
        $env:SPRING_SQL_INIT_MODE = 'never'
        $env:SPRING_LIQUIBASE_ENABLED = 'false'
        $env:SPRING_FLYWAY_ENABLED = 'false'
        $env:EUREKA_CLIENT_ENABLED = 'true'
        $env:EUREKA_CLIENT_REGISTERWITHEUREKA = 'true'
        $env:EUREKA_CLIENT_FETCHREGISTRY = 'true'
        $env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = 'http://127.0.0.1:8761/eureka/'
    } else {
        Write-Host 'CheckPoint Store: Eureka + Identidad + Gateway'
        Write-Host 'Detener: Ctrl+C en Gateway, luego Identidad, luego Eureka.'
    }
    # Ni siquiera el modo Identidad debe propagar una clave de stock heredada.
    [Environment]::SetEnvironmentVariable('STOCK_SERVICE_JWT_SECRET', $null, 'Process')
    Write-Host 'Si cmd pide terminar el trabajo por lotes, confirmar; despues cerrar las ventanas.'
    Write-Host 'Cerrar este lanzador NO detiene los servicios. Los logs quedan en cada terminal.'

    $modules = @('eureka-server', 'identidad-service', 'api-gateway')
    if ($IncluirCatalogo) { $modules += @('catalogo-service', 'venta-service') }
    foreach ($module in $modules) {
        if (!(Test-Path (Join-Path $repo "$module\mvnw.cmd"))) {
            throw "No se encuentra el wrapper Maven de $module."
        }
    }
    $configs = @('identidad-service\src\main\resources\application.properties',
        'api-gateway\src\main\resources\application.yml')
    if ($IncluirCatalogo) {
        $configs = @('eureka-server\src\main\resources\application.properties',
            'identidad-service\src\main\resources\application.properties.example',
            'catalogo-service\src\main\resources\application.properties.example',
            'venta-service\src\main\resources\application.properties.example',
            'api-gateway\src\main\resources\application.yml.example')
    }
    foreach ($config in $configs) {
        if (!(Test-Path (Join-Path $repo $config))) {
            throw "Falta $config. Preparar la configuracion local a partir de su .example."
        }
    }
    $ports = @(8761, 9096, 9090)
    if ($IncluirCatalogo) { $ports += @(9091, 9094) }
    foreach ($port in $ports) {
        $listening = $false
        if ($IncluirCatalogo) {
            # Incluye listeners IPv6 o ligados a otra interfaz, no solo loopback IPv4.
            $listening = @([System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().
                GetActiveTcpListeners() | Where-Object { $_.Port -eq $port }).Count -gt 0
        }
        if ($listening -or (Test-Port $port)) {
            throw "El puerto $port ya esta ocupado. Deten el entorno anterior antes de reintentar."
        }
    }
    if (!(Test-Port 3306)) {
        throw 'MySQL no acepta conexiones TCP en 127.0.0.1:3306. Inicia MySQL y reintenta.'
    }
    Write-Host 'MySQL accesible por TCP; los servicios validaran credenciales y esquema durante el arranque.'
    $password = Read-Host 'Contrasena MySQL de checkpoint_app (entrada oculta)' -AsSecureString
    if ($password.Length -eq 0) {
        throw 'La contrasena no puede estar vacia.'
    }

    if ($IncluirCatalogo) {
        $ventasPassword = Read-Host 'Contrasena MySQL de checkpoint_ventas_app (entrada oculta; independiente)' -AsSecureString
        if ($ventasPassword.Length -eq 0) {
            throw 'La contrasena de checkpoint_ventas_app no puede estar vacia.'
        }
        if ($null -ne $previous['STOCK_SERVICE_JWT_SECRET']) {
            if ([string]::IsNullOrWhiteSpace($previous['STOCK_SERVICE_JWT_SECRET']) -or
                [Text.Encoding]::UTF8.GetByteCount($previous['STOCK_SERVICE_JWT_SECRET']) -lt 32) {
                throw 'STOCK_SERVICE_JWT_SECRET heredado no es valido: requiere al menos 32 bytes UTF-8 y no solo espacios.'
            }
            $stockSecret = $previous['STOCK_SERVICE_JWT_SECRET']
            Write-Host 'Stock: se reutilizara el secreto valido del entorno, solo para Catalogo y Ventas.'
        }
        if ([string]::IsNullOrWhiteSpace($previous['RAWG_API_KEY'])) {
            $rawgSecret = Read-Host 'RAWG API key (entrada oculta; Enter para omitir)' -AsSecureString
        } else {
            Write-Host 'RAWG: se reutilizara la clave del entorno.'
        }
    }

    # Eureka no necesita credenciales de la base ni la clave JWT.
    foreach ($name in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET')) {
        [Environment]::SetEnvironmentVariable($name, $null, 'Process')
    }
    $env:BOOTSTRAP_ADMIN_ENABLED = 'false'
    $env:EUREKA_URL = 'http://127.0.0.1:8761/eureka/'
    if ($IncluirCatalogo) {
        # Eureka servidor no debe registrarse ni consultar su propio registro.
        $env:EUREKA_CLIENT_REGISTERWITHEUREKA = 'false'
        $env:EUREKA_CLIENT_FETCHREGISTRY = 'false'
    }
    Start-ServiceWindow 'eureka-server' 8761
    Wait-Ready 'Eureka' { Test-Http 'http://127.0.0.1:8761/' }
    if ($IncluirCatalogo) {
        $env:EUREKA_CLIENT_REGISTERWITHEUREKA = 'true'
        $env:EUREKA_CLIENT_FETCHREGISTRY = 'true'
    }

    $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/checkpoint_store_identidad'
    $env:DB_USERNAME = 'checkpoint_app'
    # Una sola clave por ejecucion: todos los servicios JWT heredan el mismo texto.
    $bytes = New-Object byte[] 64
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    do {
        $rng.GetBytes($bytes)
        $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
    } while ($env:JWT_SECRET -ceq $stockSecret)
    if ($IncluirCatalogo -and $null -eq $stockSecret) {
        do {
            $rng.GetBytes($bytes)
            $stockSecret = [Convert]::ToBase64String($bytes)
        } while ($stockSecret -ceq $env:JWT_SECRET)
        Write-Host 'Stock: secreto independiente generado para Catalogo y Ventas.'
    }
    [Array]::Clear($bytes, 0, $bytes.Length)
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
    try {
        $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
    Start-ServiceWindow 'identidad-service' 9096
    if ($IncluirCatalogo) {
        $env:CATALOGO_DB_USERNAME = $env:DB_USERNAME
        $env:CATALOGO_DB_PASSWORD = $env:DB_PASSWORD
    }
    # Identidad ya heredo su copia; Gateway no necesita credenciales MySQL.
    foreach ($name in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
        [Environment]::SetEnvironmentVariable($name, $null, 'Process')
    }
    Wait-Ready 'Identidad HTTP' { Test-Http 'http://127.0.0.1:9096/auth/v3/api-docs' }
    Wait-Ready 'registro de Identidad en Eureka' {
        Test-Registration 'IDENTIDAD-SERVICE'
    }
    if ($IncluirCatalogo) {
        $env:STOCK_SERVICE_JWT_SECRET = $stockSecret
        # Ruta persistente fuera del repo. Respetar configuración externa si existe.
        $env:CATALOGO_IMAGENES_DIR = $previous['CATALOGO_IMAGENES_DIR']
        if ([string]::IsNullOrWhiteSpace($env:CATALOGO_IMAGENES_DIR)) {
            $env:CATALOGO_IMAGENES_DIR = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'CheckPointStore\imagenes'
        }
        $env:CATALOGO_PUBLIC_BASE_URL = $previous['CATALOGO_PUBLIC_BASE_URL']
        if ([string]::IsNullOrWhiteSpace($env:CATALOGO_PUBLIC_BASE_URL)) {
            $env:CATALOGO_PUBLIC_BASE_URL = 'http://localhost:9090'
        }
        # Solo Catalogo hereda RAWG. No incluir la clave en argumentos ni archivos.
        try {
            if ($null -ne $rawgSecret) {
                $rawgPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($rawgSecret)
                try {
                    $env:RAWG_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($rawgPointer)
                } finally {
                    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($rawgPointer)
                    $rawgSecret.Dispose()
                    $rawgSecret = $null
                }
            } else {
                $env:RAWG_API_KEY = $previous['RAWG_API_KEY']
            }
            if ([string]::IsNullOrWhiteSpace($env:RAWG_API_KEY)) {
                [Environment]::SetEnvironmentVariable('RAWG_API_KEY', $null, 'Process')
                Write-Host 'RAWG omitido: el CRUD local seguira disponible.'
            }
            Start-ServiceWindow 'catalogo-service' 9091
        } finally {
            # Start-Process ya entrego una copia del entorno al hijo.
            [Environment]::SetEnvironmentVariable('RAWG_API_KEY', $null, 'Process')
        }
        foreach ($name in @('CATALOGO_DB_USERNAME', 'CATALOGO_DB_PASSWORD',
                'CATALOGO_IMAGENES_DIR', 'CATALOGO_PUBLIC_BASE_URL')) {
            [Environment]::SetEnvironmentVariable($name, $null, 'Process')
        }
        Wait-Ready 'Catalogo HTTP' { Test-Http 'http://127.0.0.1:9091/api/productos/v3/api-docs' }
        Wait-Ready 'registro de Catalogo en Eureka' { Test-Registration 'CATALOGO-SERVICE' }
        # Catalogo ya heredo su copia. Ventas usa otra cuenta y otra contrasena.
        $env:VENTAS_DB_USERNAME = 'checkpoint_ventas_app'
        $ventasPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($ventasPassword)
        try {
            $env:VENTAS_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ventasPointer)
        } finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ventasPointer)
        }
        try {
            # Condiciones aprobadas, no secretas; solo Ventas hereda este valor.
            $env:CHECKOUT_VERSION_CONDICIONES = 'CHECKPOINT_COMPRA_V1'
            Start-ServiceWindow 'venta-service' 9094
        } finally {
            foreach ($name in @('VENTAS_DB_USERNAME', 'VENTAS_DB_PASSWORD', 'STOCK_SERVICE_JWT_SECRET',
                    'CHECKOUT_VERSION_CONDICIONES')) {
                [Environment]::SetEnvironmentVariable($name, $null, 'Process')
            }
            $stockSecret = $null
        }
        Wait-Ready 'Ventas HTTP' { Test-Http 'http://127.0.0.1:9094/api/ventas/v3/api-docs' }
        Wait-Ready 'registro de Ventas en Eureka' { Test-Registration 'VENTA-SERVICE' }
    }
    Start-ServiceWindow 'api-gateway' 9090
    Wait-Ready 'Gateway y su ruta hacia Identidad' { Test-Http 'http://127.0.0.1:9090/auth/v3/api-docs' }
    if ($IncluirCatalogo) {
        Wait-Ready 'registro de Gateway en Eureka' { Test-Registration 'API-GATEWAY' }
        Wait-Ready 'Gateway y su ruta hacia Catalogo' { Test-Http 'http://127.0.0.1:9090/api/productos/v3/api-docs' }
        Wait-Ready 'Gateway y su ruta hacia Ventas' { Test-Http 'http://127.0.0.1:9090/api/ventas/v3/api-docs' }
    }
    Write-Host 'Entorno disponible. Gateway: http://127.0.0.1:9090'
    Write-Host 'Clave JWT nueva: vuelve a iniciar sesion en Postman; los tokens anteriores ya no sirven.'
} catch {
    # Solo nuestros mensajes controlados; no volcar excepciones de librerias.
    if ($_.Exception -is [System.Management.Automation.RuntimeException] -and
        $_.CategoryInfo.Category -eq 'OperationStopped') {
        Write-Host $_.Exception.Message -ForegroundColor Red
    } else {
        Write-Host "No se pudo completar el arranque en: $stage. Revisa su ventana y las rutas locales." -ForegroundColor Red
    }
    Write-Host 'Los servicios ya abiertos permanecen visibles. Detenlos con Ctrl+C antes de reintentar.'
    $exitCode = 1
} finally {
    foreach ($name in $names) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
    if ($null -ne $password) { $password.Dispose() }
    if ($null -ne $ventasPassword) { $ventasPassword.Dispose() }
    $stockSecret = $null
    if ($null -ne $rawgSecret) { $rawgSecret.Dispose() }
    if ($null -ne $rng) { $rng.Dispose() }
}
exit $exitCode
