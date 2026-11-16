package com.brisastock.api;

import com.brisastock.dto.ProductoVentaDTO;
import com.brisastock.dto.VentaRequestDTO;
import com.brisastock.model.Producto;
import com.brisastock.model.Venta;
import com.brisastock.repository.VentaRepository;
import com.brisastock.service.VentaService;
import com.brisastock.support.PruebaApiBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Prueba de la frontera transaccional con una BD real (H2 en memoria).
 *
 * Se reemplaza VentaRepository por un mock que falla al guardar la venta.
 * Eso ocurre DESPUÉS de que VentaService ya descontó el stock de los
 * productos (que sí usan el repositorio real). Si la transacción funciona,
 * el rollback deja el stock exactamente como estaba.
 *
 * (El nombre termina en "Test" para que Maven Surefire la ejecute; las
 * clases terminadas en "IT" no se ejecutan con "mvn test".)
 */
class VentaTransaccionalTest extends PruebaApiBase {

    @Autowired
    private VentaService ventaService;

    // Sustituye el bean real: aquí ventaRepository.save(...) lanzará una excepción
    @MockitoBean
    private VentaRepository ventaRepositoryFallido;

    private static ProductoVentaDTO linea(Long id, int cantidad) {
        ProductoVentaDTO l = new ProductoVentaDTO();
        l.setProductoId(id);
        l.setCantidad(cantidad);
        return l;
    }

    @Test
    void siFallaElGuardadoDeLaVenta_seRevierteElStockDeTodosLosProductos() {
        Producto a = crearProducto("A", 10, 1000.0, false);
        Producto b = crearProducto("B", 10, 2000.0, false);

        autenticarComo("admin");
        doThrow(new RuntimeException("fallo simulado al guardar la venta"))
                .when(ventaRepositoryFallido).save(any(Venta.class));

        VentaRequestDTO request = new VentaRequestDTO();
        request.setProductos(List.of(linea(a.getId(), 3), linea(b.getId(), 4)));

        assertThrows(RuntimeException.class, () -> ventaService.registrarVenta(request));

        // Rollback: ambos productos conservan su stock original
        assertEquals(10, stockActual(a.getId()));
        assertEquals(10, stockActual(b.getId()));
    }
}
