package com.brisastock.api;

import com.brisastock.model.Producto;
import com.brisastock.model.Venta;
import com.brisastock.support.PruebaApiBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Modelo relacional: los usuarios quedan relacionados con las ventas y los movimientos,
 * las categorías son una tabla propia y el detalle de venta no duplica datos.
 */
class ModeloRelacionalApiTest extends PruebaApiBase {

    private static final String PRODUCTO = """
            {"nombre":"%s","categoria":"%s","precioCompra":1000,"precioVenta":2500,"stock":10,"stockMinimo":2}
            """;

    private String venta(Long id, int cantidad) {
        return "{\"productos\":[{\"productoId\":" + id + ",\"cantidad\":" + cantidad + "}]}";
    }

    @Test
    void venta_quedaRelacionadaConElUsuarioQueLaRegistra_yConElQueLaCancela() throws Exception {
        Producto p = crearProducto("Gaseosa", 10, 2500.0, false);

        mvc.perform(post("/api/ventas").with(httpBasic("cajero", "Cajero12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(venta(p.getId(), 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("cajero"))        // el JSON publica solo el username
                .andExpect(jsonPath("$.usuario.password").doesNotExist());

        Venta guardada = ventaRepository.findAll().get(0);
        Long idCajero = usuarioRepository.findByUsername("cajero").orElseThrow().getId();
        assertEquals(idCajero, guardada.getUsuario().getId());           // llave foránea real
        assertNull(guardada.getUsuarioCancelacion());

        mvc.perform(post("/api/ventas/" + guardada.getId() + "/cancelar").with(httpBasic("admin", "Admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"prueba\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("cajero"))
                .andExpect(jsonPath("$.usuarioCancelacion").value("admin"));

        Long idAdmin = usuarioRepository.findByUsername("admin").orElseThrow().getId();
        assertEquals(idAdmin, ventaRepository.findById(guardada.getId()).orElseThrow()
                .getUsuarioCancelacion().getId());
    }

    @Test
    void detalleDeVenta_guardaSoloCantidadYPrecio_elNombreYElSubtotalSeCalculan() throws Exception {
        Producto p = crearProducto("Gaseosa", 10, 2500.0, false);

        mvc.perform(post("/api/ventas").with(httpBasic("cajero", "Cajero12345"))
                        .contentType(MediaType.APPLICATION_JSON).content(venta(p.getId(), 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(7500.0))
                .andExpect(jsonPath("$.detalles[0].cantidad").value(3))
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(2500.0))
                .andExpect(jsonPath("$.detalles[0].subtotal").value(7500.0))
                .andExpect(jsonPath("$.detalles[0].nombreProducto").value("Gaseosa"));

        // El precio de la línea es el del momento de la venta: no cambia si después cambia el producto
        p = productoRepository.findById(p.getId()).orElseThrow();
        p.setPrecioVenta(9999.0);
        productoRepository.save(p);
        assertEquals(2500.0, detalleVentaRepository.findAll().get(0).getPrecioUnitario());
    }

    @Test
    void categorias_sonUnaTablaPropia_yNoSeDuplicanPorMayusculas() throws Exception {
        for (String[] dato : new String[][]{{"Leche", "Lácteos"}, {"Yogur", "lácteos"}, {"Jabón", "Aseo"}}) {
            mvc.perform(post("/api/productos").with(httpBasic("admin", "Admin12345"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PRODUCTO.formatted(dato[0], dato[1])))
                    .andExpect(status().isOk());
        }

        assertEquals(2, categoriaRepository.count());                    // Lácteos y Aseo
        mvc.perform(get("/api/productos/categoria/LÁCTEOS").with(httpBasic("cajero", "Cajero12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].categoria").value("Lácteos")); // en el JSON sigue siendo un texto
    }
}
