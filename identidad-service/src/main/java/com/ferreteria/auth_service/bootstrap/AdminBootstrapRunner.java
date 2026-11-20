package com.ferreteria.auth_service.bootstrap;

import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("bootstrap-admin")
@ConditionalOnProperty(name = "bootstrap.admin.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private final AdminBootstrapService bootstrapService;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        // Solo la sesión local explícita; no combinar con perfiles de despliegue.
        if (!Arrays.equals(environment.getActiveProfiles(), new String[] { "bootstrap-admin" })
                || !"127.0.0.1".equals(environment.getProperty("server.address"))
                || !environment.getProperty("spring.datasource.url", "").matches(
                        "^jdbc:mysql://(?:127\\.0\\.0\\.1|localhost)(?::[0-9]+)?/checkpoint_store_identidad(?:\\?.*)?$")
                || !"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))) {
            throw new IllegalStateException("Inicialización administrativa permitida solo en el entorno local documentado");
        }

        try {
            boolean created = bootstrapService.crearPrimero(
                    System.getenv("BOOTSTRAP_ADMIN_EMAIL"),
                    System.getenv("BOOTSTRAP_ADMIN_NOMBRE"),
                    System.getenv("BOOTSTRAP_ADMIN_PASSWORD"));
            logger.info(created
                    ? "Inicialización administrativa completada. Detener Auth y retirar la habilitación temporal."
                    : "Inicialización omitida: cuenta existente o administrador ya presente. No se modificaron cuentas.");
        } catch (RuntimeException e) {
            // No incluir causas SQL, DTO, credenciales ni valores rechazados en el error de arranque.
            throw new IllegalStateException(
                    "No se pudo inicializar el administrador. Revisar datos, límite de 72 bytes y configuración local; no se sobrescriben cuentas.");
        }
    }
}
