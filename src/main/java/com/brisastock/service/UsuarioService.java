package com.brisastock.service;

import com.brisastock.dto.UsuarioAdminDTO;
import com.brisastock.dto.UsuarioRegistroDTO;
import com.brisastock.dto.UsuarioResponseDTO;
import com.brisastock.exception.ConflictoNegocioException;
import com.brisastock.exception.SolicitudInvalidaException;
import com.brisastock.model.Rol;
import com.brisastock.model.Usuario;
import com.brisastock.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class UsuarioService {

    /** BCrypt solo usa los primeros 72 bytes de la contraseña. */
    private static final int MAX_BYTES_PASSWORD = 72;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Crea un usuario SIEMPRE con rol USER y activo=true. Quien llama (el
     * controller, restringido a ADMIN) no puede influir en esos dos valores.
     */
    @Transactional
    public UsuarioResponseDTO crearComoUser(UsuarioRegistroDTO dto) {
        return crear(dto.nombre(), dto.username(), dto.password(), Rol.USER, true);
    }

    /** Creación por un administrador: permite elegir rol y estado. */
    @Transactional
    public UsuarioResponseDTO crearPorAdmin(UsuarioAdminDTO dto) {
        boolean activo = dto.activo() == null || dto.activo();
        return crear(dto.nombre(), dto.username(), dto.password(), dto.rol(), activo);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listarUsuarios() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioResponseDTO::desde)
                .toList();
    }

    private UsuarioResponseDTO crear(String nombre, String username, String password,
                                     Rol rol, boolean activo) {

        // Defensa adicional por si el servicio se invoca sin pasar por el controller
        if (password == null || password.isBlank()) {
            throw new SolicitudInvalidaException("La contraseña es obligatoria");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_PASSWORD) {
            throw new SolicitudInvalidaException(
                    "La contraseña no puede superar " + MAX_BYTES_PASSWORD + " bytes");
        }

        String usernameLimpio = username.trim();
        if (usuarioRepository.existsByUsername(usernameLimpio)) {
            throw new ConflictoNegocioException("El nombre de usuario ya está en uso");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre.trim());
        usuario.setUsername(usernameLimpio);
        // Siempre BCrypt; nunca texto plano
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(rol.name());
        usuario.setActivo(activo);

        return UsuarioResponseDTO.desde(usuarioRepository.save(usuario));
    }
}
