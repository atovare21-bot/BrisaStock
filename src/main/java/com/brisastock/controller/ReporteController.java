package com.brisastock.controller;

import com.brisastock.model.Producto;
import com.brisastock.service.ReporteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}