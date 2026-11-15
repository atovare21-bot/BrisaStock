package com.brisastock.controller;

import com.brisastock.dto.CancelarVentaRequestDTO;
import com.brisastock.dto.VentaRequestDTO;
import com.brisastock.model.Venta;
import com.brisastock.service.VentaService;
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
