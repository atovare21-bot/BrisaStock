package com.brisastock.controller;

import com.brisastock.model.Producto;
import com.brisastock.dto.ReporteVentaDiarioDTO;
import com.brisastock.service.ReporteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reportes")
@CrossOrigin("*")
public class ReporteController {

    @Autowired
    private ReporteService reporteService;

    // Productos con stock bajo
    @GetMapping("/stock-bajo")
    public List<Producto> obtenerStockBajo() {

        return reporteService.obtenerStockBajo();
    }

    // Productos agotados
    @GetMapping("/agotados")
    public List<Producto> obtenerAgotados() {

        return reporteService.obtenerAgotados();
    }

    // Productos vencidos
    @GetMapping("/vencidos")
    public List<Producto> obtenerVencidos() {

        return reporteService.obtenerVencidos();
    }

    /** Ventas activas agrupadas por día y username del vendedor. */
    @GetMapping("/ventas-por-dia")
    public List<ReporteVentaDiarioDTO> obtenerVentasPorDiaYVendedor(
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {

        return reporteService.obtenerVentasPorDiaYVendedor(desde, hasta);
    }
}
