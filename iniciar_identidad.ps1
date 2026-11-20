# Invocado por iniciar_servicios.bat identidad. Compatible con Windows PowerShell 5.1.
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

function Start-ServiceWindow([string] $Module, [int] $Port) {
    # Solo argumentos publicos. cmd hereda el entorno del proceso PowerShell.
    # WorkingDirectory evita interpolar rutas con espacios en el comando Maven.
    $command = "title $Module - $Port & mvnw.cmd `"-Dmaven.test.skip=true`" spring-boot:run"
    Start-Process -FilePath $env:ComSpec -WorkingDirectory (Join-Path $repo $Module) `
        -ArgumentList @('/d', '/k', $command) -WindowStyle Normal | Out-Null
}

$names = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET',
    'BOOTSTRAP_ADMIN_ENABLED', 'EUREKA_URL')
$previous = @{}
foreach ($name in $names) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$password = $null
$rng = $null
$exitCode = 0
try {
    Write-Host 'CheckPoint Store: Eureka + Identidad + Gateway'
    Write-Host 'Detener: Ctrl+C en Gateway, luego Identidad, luego Eureka.'
    Write-Host 'Si cmd pide terminar el trabajo por lotes, confirmar; despues cerrar las ventanas.'
    Write-Host 'Cerrar este lanzador NO detiene los servicios. Los logs quedan en cada terminal.'

    foreach ($module in @('eureka-server', 'identidad-service', 'api-gateway')) {
        if (!(Test-Path (Join-Path $repo "$module\mvnw.cmd"))) {
            throw "No se encuentra el wrapper Maven de $module."
        }
    }
    foreach ($config in @('identidad-service\src\main\resources\application.properties',
        'api-gateway\src\main\resources\application.yml')) {
        if (!(Test-Path (Join-Path $repo $config))) {
            throw "Falta $config. Preparar la configuracion local a partir de su .example."
        }
    }
    foreach ($port in @(8761, 9096, 9090)) {
        if (Test-Port $port) {
            throw "El puerto $port ya esta ocupado. Deten el entorno anterior antes de reintentar."
        }
    }
    if (!(Test-Port 3306)) {
        throw 'MySQL no acepta conexiones TCP en 127.0.0.1:3306. Inicia MySQL y reintenta.'
    }
    Write-Host 'MySQL accesible por TCP; Identidad validara credenciales y esquema durante el arranque.'
    $password = Read-Host 'Contrasena MySQL de checkpoint_app (entrada oculta)' -AsSecureString
    if ($password.Length -eq 0) {
        throw 'La contrasena no puede estar vacia.'
    }

    # Eureka no necesita credenciales de la base ni la clave JWT.
    foreach ($name in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET')) {
        [Environment]::SetEnvironmentVariable($name, $null, 'Process')
    }
    $env:BOOTSTRAP_ADMIN_ENABLED = 'false'
    $env:EUREKA_URL = 'http://127.0.0.1:8761/eureka/'
    Start-ServiceWindow 'eureka-server' 8761
    Wait-Ready 'Eureka' { Test-Http 'http://127.0.0.1:8761/' }

    $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/checkpoint_store_identidad'
    $env:DB_USERNAME = 'checkpoint_app'
    # 64 bytes criptograficamente aleatorios. Ambos servicios usan el mismo texto.
    $bytes = New-Object byte[] 64
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
    [Array]::Clear($bytes, 0, $bytes.Length)
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
    try {
        $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
    Start-ServiceWindow 'identidad-service' 9096
    # Identidad ya heredo su copia; Gateway no necesita credenciales MySQL.
    foreach ($name in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
        [Environment]::SetEnvironmentVariable($name, $null, 'Process')
    }
    Wait-Ready 'Identidad HTTP' { Test-Http 'http://127.0.0.1:9096/auth/v3/api-docs' }
    Wait-Ready 'registro de Identidad en Eureka' {
        $registration = Invoke-RestMethod -Uri 'http://127.0.0.1:8761/eureka/apps/IDENTIDAD-SERVICE' `
            -Headers @{ Accept = 'application/json' } -TimeoutSec 3
        return (@($registration.application.instance | Where-Object { $_.status -eq 'UP' }).Count -gt 0)
    }
    Start-ServiceWindow 'api-gateway' 9090
    Wait-Ready 'Gateway y su ruta hacia Identidad' { Test-Http 'http://127.0.0.1:9090/auth/v3/api-docs' }
    Write-Host 'Entorno disponible. Gateway: http://127.0.0.1:9090'
    Write-Host 'Clave JWT nueva: vuelve a iniciar sesion en Postman; los tokens anteriores ya no sirven.'
} catch {
    # Solo nuestros mensajes controlados; no volcar excepciones de librerias.
    if ($_.Exception -is [System.Management.Automation.RuntimeException] -and
        $_.CategoryInfo.Category -eq 'OperationStopped') {
        Write-Host $_.Exception.Message -ForegroundColor Red
    } else {
        Write-Host 'No se pudo completar el arranque. Revisa las ventanas de los servicios y las rutas locales.' -ForegroundColor Red
    }
    Write-Host 'Los servicios ya abiertos permanecen visibles. Detenlos con Ctrl+C antes de reintentar.'
    $exitCode = 1
} finally {
    foreach ($name in $names) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
    if ($null -ne $password) { $password.Dispose() }
    if ($null -ne $rng) { $rng.Dispose() }
}
exit $exitCode
