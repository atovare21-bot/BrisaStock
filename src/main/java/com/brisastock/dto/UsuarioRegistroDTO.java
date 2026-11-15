package com.brisastock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear un usuario básico (POST /api/usuarios, solo ADMIN).
 *
 * Deliberadamente NO incluye "rol" ni "activo": el servidor siempre crea el
 * usuario con rol USER y activo=true. Si el cliente envía esos campos, se
 * ignoran (no existe forma de asignarlos desde este DTO).
 */
public record UsuarioRegistroDTO(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String nombre,

        @NotBlank(message = "El username es obligatorio")
        @Size(min = 3, max = 50, message = "El username debe tener entre 3 y 50 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9._-]*$",
                message = "El username solo puede contener letras, números, punto, guion y guion bajo")
        String username,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password) {
}
