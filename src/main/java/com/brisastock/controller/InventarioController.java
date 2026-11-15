package com.brisastock.controller;

import com.brisastock.dto.AjusteStockRequestDTO;
import com.brisastock.dto.MovimientoInventarioDTO;
import com.brisastock.dto.MovimientoStockRequestDTO;
import com.brisastock.service.InventarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Operaciones de inventario con trazabilidad. Permisos (ver SecurityConfig):
 *   POST entradas   ADMIN, USER
 *   POST salidas    ADMIN
 *   POST ajustes    ADMIN
 *   GET  movimientos ADMIN
 */
@RestController
@RequestMapping("/api/inventario")
public class InventarioController {

    @Autowired
    private InventarioService inventarioService;

    @PostMapping("/entradas")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoInventarioDTO entrada(@Valid @RequestBody MovimientoStockRequestDTO dto) {
        return inventarioService.registrarEntrada(dto);
    }

    @PostMapping("/salidas")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoInventarioDTO salida(@Valid @RequestBody MovimientoStockRequestDTO dto) {
        return inventarioService.registrarSalida(dto);
    }

    @PostMapping("/ajustes")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoInventarioDTO ajuste(@Valid @RequestBody AjusteStockRequestDTO dto) {
        return inventarioService.registrarAjuste(dto);
    }

    /** Historial general, del más reciente al más antiguo. "limite" por defecto 200, máximo 1000. */
    @GetMapping("/movimientos")
    public List<MovimientoInventarioDTO> movimientos(@RequestParam(required = false) Integer limite) {
        return inventarioService.listarMovimientos(limite);
    }

    @GetMapping("/productos/{id}/movimientos")
    public List<MovimientoInventarioDTO> movimientosDeProducto(
            @PathVariable Long id, @RequestParam(required = false) Integer limite) {
        return inventarioService.listarMovimientosDeProducto(id, limite);
    }
}
