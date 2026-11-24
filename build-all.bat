@echo off
setlocal

for %%S in (eureka-server api-gateway identidad-service catalogo-service venta-service) do (
    echo Construyendo %%S...
    pushd "%~dp0%%S" || exit /b 1
    call .\mvnw.cmd clean package -DskipTests
    if errorlevel 1 (
        popd
        exit /b 1
    )
    popd
)

echo Compilacion completada.
pause
exit /b 0
