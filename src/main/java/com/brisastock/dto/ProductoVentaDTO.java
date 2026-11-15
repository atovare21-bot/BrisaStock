package com.brisastock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ProductoVentaDTO {

    // ID del producto
    @NotNull(message = "El productoId es obligatorio")
    @Positive(message = "El productoId debe ser un número positivo")
    private Long productoId;

    // Cantidad a vender (debe ser al menos 1: no se admiten 0 ni negativos)
    @NotNull(message = "La cantidad es obligatoria")
    @Positive(message = "La cantidad debe ser mayor que cero")
    private Integer cantidad;
}
