package com.brisastock.service;

import com.brisastock.model.Usuario;
import com.brisastock.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Usuario (entidad) de la petición actual. Las ventas y los movimientos de inventario
 * guardan una llave foránea al usuario responsable, así que necesitan la fila real de
 * la tabla usuarios y no solo el nombre.
 */
@Component
public class UsuarioSesion {

    @Autowired
    private UsuarioRepository usuarioRepository;

    /**
     * Usuario autenticado. Toda operación que cambia stock o registra una venta exige
     * sesión (la API responde 401 antes de llegar aquí), por lo que no encontrarlo es
     * un error de programación y se detiene la operación en lugar de guardar un
     * registro sin responsable.
     */
    public Usuario requerido() {
        String username = UsuarioActual.nombre();
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "No hay un usuario autenticado para registrar la operación"));
    }
}
