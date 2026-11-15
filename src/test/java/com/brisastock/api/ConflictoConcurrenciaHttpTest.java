package com.brisastock.api;

import com.brisastock.model.Producto;
import com.brisastock.service.InventarioService;
import com.brisastock.support.PruebaApiBase;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un conflicto de versión debe llegar al cliente como 409 con el formato ApiError, no como 500. */
class ConflictoConcurrenciaHttpTest extends PruebaApiBase {

    @MockitoBean
    private InventarioService inventarioService;

    @Test
    void conflictoOptimista_seTraduceA409() throws Exception {
        when(inventarioService.registrarEntrada(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Producto.class, 1L));

        mvc.perform(post("/api/inventario/entradas").with(httpBasic("admin", "Admin12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"cantidad\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }
}
