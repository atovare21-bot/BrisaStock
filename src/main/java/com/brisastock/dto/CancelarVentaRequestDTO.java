package com.brisastock.dto;

import jakarta.validation.constraints.Size;

/** Cuerpo (opcional) al cancelar una venta. */
public record CancelarVentaRequestDTO(

        @Size(max = 500, message = "El motivo no puede superar 500 caracteres")
        String motivo) {
}
