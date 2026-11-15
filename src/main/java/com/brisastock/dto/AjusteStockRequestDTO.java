package com.brisastock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Corrección manual: fija el stock de un producto a un valor. La observación es obligatoria. */
public record AjusteStockRequestDTO(

        @NotNull(message = "El productoId es obligatorio")
        @Positive(message = "El productoId debe ser un número positivo")
        Long productoId,

        @NotNull(message = "El nuevo stock es obligatorio")
        @PositiveOrZero(message = "El stock no puede ser negativo")
        Integer nuevoStock,

        @NotBlank(message = "Todo ajuste debe indicar el motivo en la observación")
        @Size(max = 500, message = "La observación no puede superar 500 caracteres")
        String observacion) {
}
