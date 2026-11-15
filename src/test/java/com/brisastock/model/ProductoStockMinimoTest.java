package com.brisastock.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regla única de stock bajo: stock <= stockMinimo (stockMinimo nulo = 0). */
class ProductoStockMinimoTest {

    private static Producto con(Integer stock, Integer stockMinimo) {
        Producto p = new Producto();
        p.setStock(stock);
        p.setStockMinimo(stockMinimo);
        return p;
    }

    @Test
    void stockIgualAlMinimo_esBajo() {
        assertTrue(con(10, 10).esStockBajo());
    }

    @Test
    void stockPorDebajoDelMinimo_esBajo() {
        assertTrue(con(9, 10).esStockBajo());
    }

    @Test
    void stockPorEncimaDelMinimo_noEsBajo() {
        assertFalse(con(11, 10).esStockBajo());
    }

    @Test
    void usaElMinimoDelProducto_noUnValorFijo() {
        // con el antiguo valor fijo (5) este producto contaba como "bajo"
        assertFalse(con(5, 2).esStockBajo());
        assertTrue(con(50, 100).esStockBajo());
    }

    @Test
    void minimoNulo_seTrataComoCero() {
        assertTrue(con(0, null).esStockBajo());
        assertFalse(con(1, null).esStockBajo());
    }
}
