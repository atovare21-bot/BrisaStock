package com.brisastock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Entrada o salida manual de inventario: cuánto se suma/resta de un producto. */
public record MovimientoStockRequestDTO(

        @NotNull(message = "El productoId es obligatorio")
        @Positive(message = "El productoId debe ser un número positivo")
        Long productoId,

        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor que cero")
        Integer cantidad,

        @Size(max = 120, message = "La referencia no puede superar 120 caracteres")
        String referencia,

        @Size(max = 500, message = "La observación no puede superar 500 caracteres")
        String observacion) {
}
