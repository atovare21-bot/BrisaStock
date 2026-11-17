package com.brisastock.controller;

import com.brisastock.dto.CancelarVentaRequestDTO;
import com.brisastock.dto.VentaRequestDTO;
import com.brisastock.dto.PaginaDTO;
import com.brisastock.model.Venta;
import com.brisastock.service.VentaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/ventas")
@CrossOrigin("*")
public class VentaController {

    @Autowired
    private VentaService ventaService;

    // Registrar venta
    @PostMapping
    public Venta registrarVenta(
            @Valid @RequestBody VentaRequestDTO request) {

        return ventaService.registrarVenta(request);
    }
    
// Obtener historial de ventas
@GetMapping
public List<Venta> listarVentas() {
    return ventaService.listarVentas();
}

// Historial paginado y ordenado del más reciente al más antiguo.
@GetMapping("/pagina")
public PaginaDTO<Venta> listarPagina(
        @RequestParam(defaultValue = "0") int pagina,
        @RequestParam(defaultValue = "20") int tamano) {
    Pageable pageable = PageRequest.of(Math.max(0, pagina),
            Math.max(1, Math.min(tamano, 100)),
            Sort.by(Sort.Direction.DESC, "fechaVenta").and(Sort.by(Sort.Direction.DESC, "id")));
    return PaginaDTO.desde(ventaService.listarPagina(pageable));
}

// Buscar venta por ID
@GetMapping("/{id}")
public Venta obtenerVentaPorId(
        @PathVariable Long id) {

    return ventaService.obtenerVentaPorId(id);
}

// Cancelar/devolver una venta (solo ADMIN): devuelve el stock y deja la venta CANCELADA
@PostMapping("/{id}/cancelar")
public Venta cancelarVenta(
        @PathVariable Long id,
        @Valid @RequestBody(required = false) CancelarVentaRequestDTO request) {

    return ventaService.cancelarVenta(id, request == null ? null : request.motivo());
}
    
}
