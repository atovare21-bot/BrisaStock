package com.brisastock.api;

import com.brisastock.model.Producto;
import com.brisastock.support.PruebaApiBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductoApiTest extends PruebaApiBase {

    private static final String VALIDO = """
            {"nombre":"Producto Nuevo","marca":"X","categoria":"Snacks","descripcion":"d",
             "precioCompra":1000,"precioVenta":1500,"stock":10,"stockMinimo":3,"vencido":false,
             "fechaVencimiento":"2027-01-01"}""";

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return httpBasic("admin", "Admin12345");
    }

    @Test
    void obtenerPorId_existente_devuelve200() throws Exception {
        Producto p = crearProducto("Cola", 5, 1000.0, false);
        mvc.perform(get("/api/productos/" + p.getId()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Cola"));
    }

    @Test
    void obtenerPorId_inexistente_devuelve404() throws Exception {
        mvc.perform(get("/api/productos/999999").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void obtenerPorId_conTextoEnVezDeNumero_devuelve400() throws Exception {
        mvc.perform(get("/api/productos/abc").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearProductoValido_calculaEstadoEIgnoraIdYEstadoDelCliente() throws Exception {
        String cuerpo = "{\"id\":5,\"estado\":\"Hackeado\"," + VALIDO.substring(1);
        mvc.perform(post("/api/productos").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Disponible"));
    }

    @Test
    void crearConStockCero_quedaAgotado() throws Exception {
        mvc.perform(post("/api/productos").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALIDO.replace("\"stock\":10", "\"stock\":0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Agotado"));
    }

    @Test
    void crearConStockAusente_devuelve400_noNullPointer() throws Exception {
        mvc.perform(post("/api/productos").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALIDO.replace("\"stock\":10,", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores").isArray());
    }

    @Test
    void crearConDatosInvalidos_devuelve400YNoGuarda() throws Exception {
        String[] invalidos = {
                VALIDO.replace("\"stock\":10", "\"stock\":-1"),
                VALIDO.replace("\"precioVenta\":1500", "\"precioVenta\":0"),
                VALIDO.replace("\"precioCompra\":1000", "\"precioCompra\":-5"),
                VALIDO.replace("Producto Nuevo", "   "),
                "{}"
        };
        for (String cuerpo : invalidos) {
            mvc.perform(post("/api/productos").with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, productoRepository.count());
    }

    @Test
    void actualizar_inexistente_devuelve404() throws Exception {
        mvc.perform(put("/api/productos/999999").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isNotFound());
    }

    // Fase 2: este test antes exigía que PUT pudiera cambiar el stock (comportamiento
    // de Fase 1). Ahora el stock solo cambia por /api/inventario, así que PUT con un
    // stock distinto se rechaza y los datos administrativos siguen actualizándose.
    @Test
    void actualizar_valido_modificaDatosAdministrativos_yNoPermiteCambiarStock() throws Exception {
        Producto p = crearProducto("Cola", 5, 1000.0, false);

        // stock distinto del actual -> 400 y no se toca nada
        mvc.perform(put("/api/productos/" + p.getId()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALIDO.replace("\"stock\":10", "\"stock\":77")))
                .andExpect(status().isBadRequest());
        assertEquals(5, stockActual(p.getId()));
        assertEquals("Cola", productoRepository.findById(p.getId()).orElseThrow().getNombre());

        // mismo stock que el actual -> se aceptan los demás cambios
        mvc.perform(put("/api/productos/" + p.getId()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALIDO.replace("\"stock\":10", "\"stock\":5")))
                .andExpect(status().isOk());
        assertEquals(5, stockActual(p.getId()));
        assertEquals("Producto Nuevo", productoRepository.findById(p.getId()).orElseThrow().getNombre());
    }

    @Test
    void eliminar_inexistente_devuelve404() throws Exception {
        mvc.perform(delete("/api/productos/999999").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminar_productoConVentas_devuelve409YNoLoBorra() throws Exception {
        Producto p = crearProducto("Cola", 5, 1000.0, false);
        String venta = "{\"productos\":[{\"productoId\":" + p.getId() + ",\"cantidad\":1}]}";
        mvc.perform(post("/api/ventas").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(venta))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/productos/" + p.getId()).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
        assertEquals(1, productoRepository.count());
    }

    @Test
    void eliminar_productoSinVentas_devuelve200() throws Exception {
        Producto p = crearProducto("Cola", 5, 1000.0, false);
        mvc.perform(delete("/api/productos/" + p.getId()).with(admin()))
                .andExpect(status().isOk());
        assertEquals(0, productoRepository.count());
    }

    @Test
    void busquedasExistentesSiguenFuncionando() throws Exception {
        crearProducto("Cola Zero", 5, 1000.0, false);
        mvc.perform(get("/api/productos/buscar").param("nombre", "cola").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Cola Zero"));
        mvc.perform(get("/api/productos/categoria/Bebidas").with(admin())).andExpect(status().isOk());
        mvc.perform(get("/api/productos/estado/Disponible").with(admin())).andExpect(status().isOk());
        mvc.perform(get("/api/productos/stock-bajo/10").with(admin())).andExpect(status().isOk());
        mvc.perform(get("/api/productos/buscar").with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void listarPagina_respetaPaginaBusquedaYFiltro() throws Exception {
        crearProducto("Arroz", 8, 1000.0, false);
        crearProducto("Atún", 0, 1500.0, false);
        crearProducto("Azúcar", 6, 900.0, false);

        mvc.perform(get("/api/productos/pagina").param("pagina", "1").param("tamano", "2").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nombre").value("Azúcar"));

        mvc.perform(get("/api/productos/pagina").param("nombre", "Atún").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nombre").value("Atún"));

        mvc.perform(get("/api/productos/pagina").param("filtro", "agotado").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nombre").value("Atún"));
    }
}
