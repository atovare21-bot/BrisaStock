package com.brisastock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class VentaRequestDTO {

    // Lista de productos vendidos
    @NotEmpty(message = "La venta debe incluir al menos un producto")
    @Size(max = 100, message = "Una venta no puede superar 100 líneas de producto")
    private List<@NotNull(message = "No se permiten líneas de venta vacías") @Valid ProductoVentaDTO> productos;
}
