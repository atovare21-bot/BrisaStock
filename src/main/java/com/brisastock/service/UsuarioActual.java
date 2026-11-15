package com.brisastock.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Usuario autenticado de la petición actual (para registrar quién hizo cada cambio). */
final class UsuarioActual {

    /** Nombre que se registra cuando no hay usuario autenticado (p. ej. tareas internas). */
    static final String SISTEMA = "sistema";

    private UsuarioActual() {
    }

    static String nombre() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                || auth.getName() == null || auth.getName().isBlank()) {
            return SISTEMA;
        }
        return auth.getName();
    }
}
