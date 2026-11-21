package com.brisastock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contraseña nueva para restablecer desde la gestión de usuarios (solo ADMIN). */
public record CambiarPasswordRequestDTO(
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password) {
}
