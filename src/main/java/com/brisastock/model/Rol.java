package com.brisastock.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Roles de la aplicación.
 *
 * En la base de datos el rol se guarda como texto (columna usuarios.rol).
 * Los datos existentes usan "ADMIN" y "EMPLEADO"; por compatibilidad,
 * "EMPLEADO" se interpreta como {@link #USER} SIN modificar la base de datos.
 * Los usuarios nuevos se guardan con "ADMIN" o "USER".
 */
public enum Rol {

    ADMIN,
    USER;

    /** Autoridad de Spring Security (ROLE_ADMIN, ROLE_USER). */
    public String authority() {
        return "ROLE_" + name();
    }

    /**
     * Interpreta el valor guardado en la BD. Devuelve vacío si el valor es
     * nulo o no se reconoce (en ese caso el usuario no recibe ningún permiso).
     */
    public static Optional<Rol> desdeAlmacenado(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        String normalizado = valor.trim().toUpperCase(Locale.ROOT);
        if (normalizado.startsWith("ROLE_")) {
            normalizado = normalizado.substring("ROLE_".length());
        }
        return switch (normalizado) {
            case "ADMIN" -> Optional.of(ADMIN);
            case "USER", "EMPLEADO" -> Optional.of(USER);
            default -> Optional.empty();
        };
    }
}
