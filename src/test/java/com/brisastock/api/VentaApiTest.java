package com.brisastock.api;

import com.brisastock.model.Producto;
import com.brisastock.support.PruebaApiBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VentaApiTest extends PruebaApiBase {

    private ResultActions vender(String json) throws Exception {
        return mvc.perform(post("/api/ventas").with(httpBasic("admin", "Admin12345"))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String linea(Object productoId, Object cantidad) {
        return "{\"productoId\":" + productoId + ",\"cantidad\":" + cantidad + "}";
    }

    private static String venta(String... lineas) {
        return "{\"productos\":[" + String.join(",", lineas) + "]}";
    }

    @Test
    void ventaValida_calculaTotal_yDescuentaStock() throws Exception {
        Producto a = crearProducto("A", 10, 1000.0, false);
        Producto b = crearProducto("B", 10, 2500.0, false);

        vender(venta(linea(a.getId(), 2), linea(b.getId(), 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4500.0))
                .andExpect(jsonPath("$.detalles.length()").value(2));

        assertEquals(8, stockActual(a.getId()));
        assertEquals(9, stockActual(b.getId()));
    }

    @Test
    void cantidadCeroONegativa_devuelve400_yElStockNoCambia() throws Exception {
        Producto a = crearProducto("A", 10, 1000.0, false);

        vender(venta(linea(a.getId(), 0))).andExpect(status().isBadRequest());
        vender(venta(linea(a.getId(), -5))).andExpect(status().isBadRequest());

        assertEquals(10, stockActual(a.getId()));
        assertEquals(0, ventaRepository.count());
    }

    @Test
    void solicitudesIncompletas_devuelven400() throws Exception {
        Producto a = crearProducto("A", 10, 1000.0, false);

        vender(venta("{\"productoId\":" + a.getId() + "}")).andExpect(status().isBadRequest());
        vender(venta("{\"cantidad\":1}")).andExpect(status().isBadRequest());
        vender("{\"productos\":[]}").andExpect(status().isBadRequest());
        vender("{}").andExpect(status().isBadRequest());
        vender("{\"productos\":[null]}").andExpect(status().isBadRequest());
    }

    @Test
    void productoInexistente_devuelve404_yNoDescuentaLasOtrasLineas() throws Exception {
        Producto a = crearProducto("A", 10, 1000.0, false);

        vender(venta(linea(a.getId(), 1), linea(999999, 1)))
                .andExpect(status().isNotFound());

        assertEquals(10, stockActual(a.getId()));
    }

    @Test
    void stockInsuficienteEnLaSegundaLinea_devuelve409_yNoDescuentaLaPrimera() throws Exception {
        Producto a = crearProducto("A", 10, 1000.0, false);
        Producto b = crearProducto("B", 2, 1000.0, false);

        vender(venta(linea(a.getId(), 3), linea(b.getId(), 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        assertEquals(10, stockActual(a.getId()));
        assertEquals(2, stockActual(b.getId()));
        assertEquals(0, ventaRepository.count());
    }

    @Test
    void productoVencido_devuelve409() throws Exception {
        Producto v = crearProducto("Yogurt", 10, 1000.0, true);

        vender(venta(linea(v.getId(), 1))).andExpect(status().isConflict());

        assertEquals(10, stockActual(v.getId()));
    }

    @Test
    void ventaDeTodoElStock_dejaAgotado() throws Exception {
        Producto a = crearProducto("A", 3, 1000.0, false);

        vender(venta(linea(a.getId(), 3))).andExpect(status().isOk());

        Producto actual = productoRepository.findById(a.getId()).orElseThrow();
        assertEquals(0, actual.getStock());
        assertEquals("Agotado", actual.getEstado());
    }

    @Test
    void consultarVentaInexistente_devuelve404() throws Exception {
        mvc.perform(get("/api/ventas/999999").with(httpBasic("admin", "Admin12345")))
                .andExpect(status().isNotFound());
    }
}
