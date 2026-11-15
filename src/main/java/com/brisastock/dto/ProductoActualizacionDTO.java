package com.brisastock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Datos ADMINISTRATIVOS de un producto (PUT). El stock ya no se modifica por
 * aquí: se cambia con /api/inventario (entradas, salidas, ajustes).
 *
 * "stock" se acepta solo por compatibilidad con clientes que reenvían el
 * producto completo: si viene y es distinto del actual, la solicitud se
 * rechaza con 400; si no viene o es igual, se ignora.
 */
public record ProductoActualizacionDTO(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
        String nombre,

        @Size(max = 255, message = "La marca no puede superar 255 caracteres")
        String marca,

        @Size(max = 255, message = "La categoría no puede superar 255 caracteres")
        String categoria,

        @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
        String descripcion,

        @NotNull(message = "El precio de compra es obligatorio")
        @PositiveOrZero(message = "El precio de compra no puede ser negativo")
        Double precioCompra,

        @NotNull(message = "El precio de venta es obligatorio")
        @Positive(message = "El precio de venta debe ser mayor que cero")
        Double precioVenta,

        @PositiveOrZero(message = "El stock no puede ser negativo")
        Integer stock,

        @PositiveOrZero(message = "El stock mínimo no puede ser negativo")
        Integer stockMinimo,

        Boolean vencido,

        @Size(max = 255, message = "La fecha de vencimiento no puede superar 255 caracteres")
        String fechaVencimiento) {
}
