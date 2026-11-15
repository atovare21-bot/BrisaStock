package com.brisastock.controller;

import com.brisastock.dto.SesionDTO;
import com.brisastock.model.Rol;
import com.brisastock.model.Usuario;
import com.brisastock.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Permite a la interfaz web comprobar las credenciales y saber el rol del
 * usuario conectado. Requiere autenticación (ADMIN o USER); no cambia el
 * mecanismo de seguridad (sigue siendo HTTP Basic).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @GetMapping("/me")
    public SesionDTO yo(Authentication auth) {
        String rol = auth.getAuthorities().stream()
                .anyMatch(a -> Rol.ADMIN.authority().equals(a.getAuthority()))
                ? Rol.ADMIN.name()
                : Rol.USER.name();

        String nombre = usuarioRepository.findByUsername(auth.getName())
                .map(Usuario::getNombre)
                .orElse(auth.getName());

        return new SesionDTO(auth.getName(), nombre, rol);
    }
}
