package com.brisastock.dto;

/** Quién es el usuario autenticado (sin contraseña ni hash). "rol" es ADMIN o USER. */
public record SesionDTO(String username, String nombre, String rol) {
}
