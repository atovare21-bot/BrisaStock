package com.brisastock.model;

/**
 * Tipos de movimiento de inventario. Cada cambio de stock se registra con
 * exactamente uno de estos tipos.
 *
 * ENTRADA     suma stock (compra, reposición, stock inicial)
 * SALIDA      resta stock manualmente (merma, consumo interno)
 * VENTA       resta stock por una venta
 * AJUSTE      corrección manual: fija el stock a un valor (sube o baja)
 * DEVOLUCION  suma stock por la cancelación/devolución de una venta
 */
public enum TipoMovimiento {
    ENTRADA,
    SALIDA,
    VENTA,
    AJUSTE,
    DEVOLUCION
}
