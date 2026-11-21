package com.brisastock.controller;

import com.brisastock.dto.CambiarPasswordRequestDTO;
import com.brisastock.dto.UsuarioAdminDTO;
import com.brisastock.dto.UsuarioRegistroDTO;
import com.brisastock.dto.UsuarioResponseDTO;
import com.brisastock.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin("*")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    /**
     * Solo ADMIN (ver SecurityConfig): crea un usuario SIEMPRE con rol USER y
     * activo=true; el JSON no puede elegir rol (el DTO ni siquiera tiene ese
     * campo). Ya no es un registro público.
     * La respuesta nunca incluye la contraseña ni su hash.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponseDTO registrar(
            @Valid @RequestBody UsuarioRegistroDTO dto) {

        return usuarioService.crearComoUser(dto);
    }

    /** Solo ADMIN: crea un usuario eligiendo rol (ADMIN/USER) y estado. */
    @PostMapping("/admin")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponseDTO crearPorAdmin(
            @Valid @RequestBody UsuarioAdminDTO dto) {

        return usuarioService.crearPorAdmin(dto);
    }

    /** Solo ADMIN: lista los usuarios (sin contraseñas). */
    @GetMapping
    public List<UsuarioResponseDTO> listar() {
        return usuarioService.listarUsuarios();
    }

    /** Solo ADMIN: desactiva la cuenta sin eliminarla ni perder su historial. */
    @PatchMapping("/{id}/desactivar")
    public UsuarioResponseDTO desactivar(@PathVariable Long id, Authentication auth) {
        return usuarioService.cambiarActivo(id, false, auth.getName());
    }

    /** Solo ADMIN: reactiva una cuenta existente. */
    @PatchMapping("/{id}/activar")
    public UsuarioResponseDTO activar(@PathVariable Long id, Authentication auth) {
        return usuarioService.cambiarActivo(id, true, auth.getName());
    }

    /** Solo ADMIN: establece una nueva contraseña, que el servicio almacena con BCrypt. */
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarPassword(@PathVariable Long id, @Valid @RequestBody CambiarPasswordRequestDTO dto) {
        usuarioService.cambiarPassword(id, dto.password());
    }
}
