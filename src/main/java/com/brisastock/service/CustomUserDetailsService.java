package com.brisastock.service;

import com.brisastock.model.Rol;
import com.brisastock.model.Usuario;
import com.brisastock.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService
        implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(
            String username)
            throws UsernameNotFoundException {

        Usuario usuario = usuarioRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Usuario no encontrado"
                        ));

        if (usuario.getPassword() == null || usuario.getPassword().isBlank()) {
            throw new UsernameNotFoundException("Usuario sin credenciales válidas");
        }

        // "ADMIN" -> ROLE_ADMIN; "USER" y el valor histórico "EMPLEADO" -> ROLE_USER.
        // Un rol nulo o desconocido NO recibe ningún permiso (falla cerrado).
        List<GrantedAuthority> autoridades = Rol.desdeAlmacenado(usuario.getRol())
                .map(rol -> List.<GrantedAuthority>of(new SimpleGrantedAuthority(rol.authority())))
                .orElseGet(() -> {
                    log.warn("El usuario '{}' tiene un rol no reconocido; no recibe permisos",
                            usuario.getUsername());
                    return List.of();
                });

        return User.builder()
                .username(usuario.getUsername())
                .password(usuario.getPassword())
                .authorities(autoridades)
                // activo nulo se trata como desactivado (antes provocaba NullPointerException)
                .disabled(!Boolean.TRUE.equals(usuario.getActivo()))
                .build();
    }
}
