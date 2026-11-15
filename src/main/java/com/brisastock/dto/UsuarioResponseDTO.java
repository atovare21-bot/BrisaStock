package com.brisastock.dto;

import com.brisastock.model.Rol;
import com.brisastock.model.Usuario;

/**
 * Representación pública de un usuario. NUNCA incluye password ni hash.
 * "rol" muestra el rol efectivo (ADMIN/USER); los usuarios antiguos con
 * rol "EMPLEADO" se muestran como USER.
 */
public record UsuarioResponseDTO(
        Long id,
        String nombre,
        String username,
        String rol,
        Boolean activo) {

    public static UsuarioResponseDTO desde(Usuario usuario) {
        String rolEfectivo = Rol.desdeAlmacenado(usuario.getRol())
                .map(Rol::name)
                .orElse(usuario.getRol());
        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getUsername(),
                rolEfectivo,
                usuario.getActivo());
    }
}
