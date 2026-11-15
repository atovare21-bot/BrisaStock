package com.brisastock.config;

import com.brisastock.security.RestAccessDeniedHandler;
import com.brisastock.security.RestAuthenticationEntryPoint;
import com.brisastock.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad: HTTP Basic + autorización por roles.
 *
 * ======================== MATRIZ DE PERMISOS ========================
 * (Spring evalúa las reglas en orden: gana la primera que coincide.)
 *
 *  Recurso                                     Anónimo   USER   ADMIN
 *  ------------------------------------------  --------  -----  -----
 *  Swagger UI y /v3/api-docs                      no      no     si
 *  GET  /, /index.html, /css/**, /js/**           si      si     si
 *       (archivos de la interfaz web, sin datos)
 *  GET  /api/auth/me  (quién soy / rol)           no      si     si
 *  GET  /api/productos/**  (listar, por id,       no      si     si
 *       buscar, categoría, estado, stock-bajo)
 *  POST/PUT/DELETE /api/productos/**              no      no     si
 *  POST /api/ventas  (registrar venta)            no      si     si
 *  GET  /api/ventas/**  (historial de ventas)     no      no     si
 *  POST /api/ventas/{id}/cancelar                 no      no     si
 *  POST /api/inventario/entradas                  no      si     si
 *  POST /api/inventario/salidas, /ajustes         no      no     si
 *  GET  /api/inventario/**  (movimientos)         no      no     si
 *  /api/reportes/**                               no      no     si
 *  /api/usuarios/** y POST /api/usuarios          no      no     si
 *       (listar y crear usuarios; NO hay registro público)
 *  Cualquier otra ruta                            no      no     si
 *
 * "Cualquier otra ruta" es solo ADMIN a propósito: si se agrega un endpoint
 * nuevo y se olvida declararlo aquí, queda restringido (falla cerrado).
 *
 * Roles: ROLE_ADMIN y ROLE_USER (ver enum Rol). Los usuarios antiguos con
 * rol "EMPLEADO" en la BD se tratan como ROLE_USER.
 * ====================================================================
 */
@Configuration
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager
    authenticationManager(
            AuthenticationConfiguration config)
            throws Exception {

        return config
                .getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth

                        // --- Documentación de la API: solo ADMIN autenticado ---
                        // (el navegador pedirá usuario y contraseña; no es pública)
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).hasRole("ADMIN")

                        // Interfaz web: solo archivos estáticos (HTML/CSS/JS), sin datos.
                        // Todos los datos siguen detrás de /api/**, que exige autenticación.
                        .requestMatchers(HttpMethod.GET,
                                "/", "/index.html", "/css/**", "/js/**")
                        .permitAll()

                        // Comprobar credenciales y rol del usuario conectado
                        .requestMatchers(HttpMethod.GET, "/api/auth/me")
                        .hasAnyRole("ADMIN", "USER")

                        // --- Usuarios: solo ADMIN ---
                        // Ya no existe registro público: crear usuarios (con o sin elegir rol)
                        // y listarlos requiere ROLE_ADMIN. La ruta base se declara de forma
                        // explícita para que nadie la reabra por accidente.
                        .requestMatchers("/api/usuarios", "/api/usuarios/**")
                        .hasRole("ADMIN")

                        // --- Productos ---
                        .requestMatchers(HttpMethod.GET, "/api/productos/**")
                        .hasAnyRole("ADMIN", "USER")
                        .requestMatchers("/api/productos/**")
                        .hasRole("ADMIN")

                        // --- Ventas ---
                        .requestMatchers(HttpMethod.POST, "/api/ventas")
                        .hasAnyRole("ADMIN", "USER")
                        .requestMatchers("/api/ventas/**")
                        .hasRole("ADMIN")

                        // --- Inventario ---
                        // Recibir mercancía (entrada) la puede registrar un USER;
                        // salidas, ajustes y consulta del historial: solo ADMIN.
                        .requestMatchers(HttpMethod.POST, "/api/inventario/entradas")
                        .hasAnyRole("ADMIN", "USER")
                        .requestMatchers("/api/inventario/**")
                        .hasRole("ADMIN")

                        // --- Reportes ---
                        .requestMatchers("/api/reportes/**")
                        .hasRole("ADMIN")

                        // --- Todo lo demás: falla cerrado ---
                        .anyRequest()
                        .hasRole("ADMIN")
                )

                .userDetailsService(
                        userDetailsService)

                // 401 / 403 con el mismo formato JSON que el resto de la API
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .httpBasic(basic -> basic
                        .authenticationEntryPoint(authenticationEntryPoint));

        return http.build();
    }
}
