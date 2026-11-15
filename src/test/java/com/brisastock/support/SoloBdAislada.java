package com.brisastock.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Guardia de seguridad: aborta ANTES de abrir cualquier conexión si la
 * aplicación de pruebas no apunta a una BD H2 en memoria. Así es imposible
 * que un test ejecute operaciones sobre la base de datos real.
 */
public class SoloBdAislada
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String url = context.getEnvironment().getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:h2:mem:")) {
            throw new IllegalStateException(
                    "Las pruebas solo pueden usar H2 en memoria, pero spring.datasource.url = '"
                            + url + "'. Se aborta para proteger la base de datos real.");
        }
    }
}
