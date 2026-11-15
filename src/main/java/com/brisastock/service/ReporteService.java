package com.brisastock.service;

import com.brisastock.model.Producto;
import com.brisastock.repository.ProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReporteService {

    @Autowired
    private ProductoRepository productoRepository;

    // Stock bajo: stock <= stockMinimo de cada producto (sin valores fijos)
    public List<Producto> obtenerStockBajo() {

        return productoRepository.findConStockBajo();
    }

    // Agotados
    public List<Producto> obtenerAgotados() {

        // Por stock real (<= 0), no por el texto de "estado"
        return productoRepository.findAgotados();
    }

    // Vencidos: marcados manualmente, con estado heredado "Vencido" o con fecha
    // válida anterior a hoy. Las fechas con formato antiguo no se consideran vencidas
    // (se filtra en memoria para no depender del formato guardado en la BD).
    public List<Producto> obtenerVencidos() {

        return productoRepository.findAll().stream()
                .filter(Producto::estaActivo)
                .filter(Producto::estaVencidoEfectivo)
                .toList();
    }
}
