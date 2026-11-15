package com.brisastock;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protege la configuración real de la aplicación (src/main/resources). Los tests usan
 * su propio application.properties (H2), así que sin esta prueba nadie notaría si
 * alguien vuelve a escribir credenciales fijas o a activar show-sql por defecto.
 * Solo lee el archivo; no se conecta a ninguna base de datos.
 */
class ConfiguracionSeguridadTest {

    private static String propiedades() throws Exception {
        return Files.readString(Path.of("src/main/resources/application.properties"));
    }

    @Test
    void credencialesYUrlDeLaBd_vienenDeVariablesDeEntorno() throws Exception {
        String p = propiedades();
        assertTrue(p.contains("spring.datasource.url=${DB_URL:"), "la URL debe poder venir de DB_URL");
        assertTrue(p.contains("spring.datasource.username=${DB_USERNAME:"), "el usuario debe poder venir de DB_USERNAME");
        assertTrue(p.contains("spring.datasource.password=${DB_PASSWORD:"), "la contraseña debe poder venir de DB_PASSWORD");
    }

    @Test
    void laBaseDeDatosSigueSiendoBrisastockDb_porDefecto() throws Exception {
        assertTrue(propiedades().contains("jdbc:mysql://localhost:3306/brisastock_db"));
    }

    @Test
    void showSql_estaDesactivadoPorDefecto_yEsActivableExplicitamente() throws Exception {
        String p = propiedades();
        assertTrue(p.contains("spring.jpa.show-sql=${SHOW_SQL:false}"));
        assertFalse(p.contains("show-sql=true"));
    }

    @Test
    void swagger_sePuedeApagarConUnaVariable() throws Exception {
        assertTrue(propiedades().contains("springdoc.swagger-ui.enabled=${SWAGGER_ENABLED:"));
    }
}
