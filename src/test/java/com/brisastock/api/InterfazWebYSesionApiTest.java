package com.brisastock.api;

import com.brisastock.model.Producto;
import com.brisastock.support.PruebaApiBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fase 3: /api/auth/me, archivos estáticos públicos y campo stockBajo. */
class InterfazWebYSesionApiTest extends PruebaApiBase {

    @Test
    void me_admin_devuelveRolAdmin_sinDatosSensibles() throws Exception {
        mvc.perform(get("/api/auth/me").with(httpBasic("admin", "Admin12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void me_userYEmpleadoLegado_devuelvenRolUser() throws Exception {
        mvc.perform(get("/api/auth/me").with(httpBasic("cajero", "Cajero12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("USER"));
        mvc.perform(get("/api/auth/me").with(httpBasic("empleado1", "Empleado123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("USER"));
    }

    @Test
    void me_sinCredencialesOConCredencialesMalas_devuelve401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").with(httpBasic("admin", "incorrecta")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        mvc.perform(get("/api/auth/me").with(httpBasic("inactivo", "Inactivo123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void archivosDeLaInterfaz_sonPublicos_peroLaApiSigueCerrada() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BrisaStock")));
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mvc.perform(get("/js/app.js")).andExpect(status().isOk());

        mvc.perform(get("/api/productos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ventas")).andExpect(status().isUnauthorized());
    }

    /** Accesibilidad y búsqueda rápida: sus archivos se sirven sin sesión (no contienen datos). */
    @Test
    void accesibilidadYBusquedaRapida_estanEnLaInterfaz_ySonPublicas() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"btn-accesibilidad\"")))
                .andExpect(content().string(containsString("id=\"btn-accesibilidad-login\"")))
                .andExpect(content().string(containsString("id=\"btn-buscar\"")));
        mvc.perform(get("/js/accesibilidad.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("localStorage")));
        mvc.perform(get("/js/busqueda.js")).andExpect(status().isOk());
        mvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-contraste")));
    }

    @Test
    void producto_publicaStockBajoCalculadoPorElServidor() throws Exception {
        Producto igual = crearProducto("Igual", 5, 1000.0, false);
        igual.setStockMinimo(5);
        productoRepository.save(igual);
        Producto encima = crearProducto("Encima", 6, 1000.0, false);
        encima.setStockMinimo(5);
        productoRepository.save(encima);

        mvc.perform(get("/api/productos/" + igual.getId()).with(httpBasic("cajero", "Cajero12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockBajo").value(true));
        mvc.perform(get("/api/productos/" + encima.getId()).with(httpBasic("cajero", "Cajero12345")))
                .andExpect(jsonPath("$.stockBajo").value(false));
    }
}
