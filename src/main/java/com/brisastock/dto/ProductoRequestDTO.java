package com.brisastock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Datos de entrada para crear (POST) y actualizar (PUT) productos.
 *
 * No incluye "id" ni "estado": el id lo asigna la BD y el estado se calcula
 * en el servicio (Disponible / Agotado / Vencido).
 */
public record ProductoRequestDTO(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
        String nombre,

        @Size(max = 100, message = "La marca no puede superar 100 caracteres")
        String marca,

        @Size(max = 80, message = "La categoría no puede superar 80 caracteres")
        String categoria,

        @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
        String descripcion,

        @NotNull(message = "El precio de compra es obligatorio")
        @PositiveOrZero(message = "El precio de compra no puede ser negativo")
        Double precioCompra,

        @NotNull(message = "El precio de venta es obligatorio")
        @Positive(message = "El precio de venta debe ser mayor que cero")
        Double precioVenta,

        @NotNull(message = "El stock es obligatorio")
        @PositiveOrZero(message = "El stock no puede ser negativo")
        Integer stock,

        @PositiveOrZero(message = "El stock mínimo no puede ser negativo")
        Integer stockMinimo,

        Boolean vencido,

        @Size(max = 255, message = "La fecha de vencimiento no puede superar 255 caracteres")
        String fechaVencimiento) {
}
