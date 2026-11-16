package com.brisastock.dto;

import com.brisastock.model.MovimientoInventario;
import com.brisastock.model.TipoMovimiento;

import java.time.LocalDateTime;

/** Representación pública de un movimiento de inventario. */
public record MovimientoInventarioDTO(
        Long id,
        Long productoId,
        String productoNombre,
        TipoMovimiento tipo,
        Integer cantidad,
        Integer stockAnterior,
        Integer stockPosterior,
        LocalDateTime fecha,
        String usuario,
        String referencia,
        String observacion) {

    public static MovimientoInventarioDTO desde(MovimientoInventario m) {
        return new MovimientoInventarioDTO(
                m.getId(),
                m.getProducto().getId(),
                m.getProducto().getNombre(),
                m.getTipo(),
                m.getCantidad(),
                m.getStockAnterior(),
                m.getStockPosterior(),
                m.getFecha(),
                m.getUsuario().getUsername(),
                m.getReferencia(),
                m.getObservacion());
    }
}
