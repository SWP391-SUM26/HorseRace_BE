package com.SWP391.horserace.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security configuration.
 *
 * <p><b>Protected by default:</b> only the auth endpoints, the OpenAPI/Swagger docs and
 * {@code /error} are public. <b>Every other endpoint requires a valid
 * {@code Authorization: Bearer <accessToken>}</b> — calls without a token get a JSON 401
 * (from {@link JwtAuthEntryPoint}); authenticated callers lacking the required role get a
 * JSON 403 (from {@link RestAccessDeniedHandler}). The JWT filter runs first, parsing the
 * token and populating the SecurityContext (so {@code @AuthenticationPrincipal} and
 * {@code @PreAuthorize("hasRole('ADMIN')")} work).
 *
 * <p>No HTTP session, no CSRF (token-based API). {@code @EnableMethodSecurity} is on so
 * {@code @PreAuthorize} works on controllers/services.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/**",
            "/api/v1/files/**",   // public file serving (avatars/horse photos); <img> sends no JWT
            // VNPay callbacks carry no JWT — the vnp_SecureHash checksum is the only auth.
            // The IPN is the authoritative server-to-server credit; the return URL is the
            // browser redirect (status only unless the guarded fallback is enabled). The rest
            // of /api/v1/wallet/** stays authenticated.
            "/api/v1/wallet/vnpay-ipn",
            "/api/v1/wallet/vnpay-return",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/error"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    // See application.properties (app.cors.allowed-origins / APP_CORS_ALLOWED_ORIGINS).
    // The FE never sends cookies (Bearer-token auth), so no allowCredentials is needed.
    @Value("${app.cors.allowed-origins}")
    private List<String> corsAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Password encoder.
     *
     * <ul>
     *   <li>{@code {bcrypt}$2y$...} → verified with BCrypt.</li>
     *   <li>{@code {noop}...} → plain text.</li>
     *   <li>NO prefix (e.g. a raw {@code 123456}) → treated as plain text via the NoOp default,
     *       so simple seed passwords work in dev without a prefix.</li>
     *   <li>New passwords created by the app are encoded with BCrypt (the default for encoding).</li>
     * </ul>
     */
    @Bean
    @SuppressWarnings("deprecation") // NoOpPasswordEncoder is intentional for dev plaintext
    public PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder encoder =
                (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        // Stored hashes without a {id} prefix are matched as plain text instead of throwing.
        encoder.setDefaultPasswordEncoderForMatches(NoOpPasswordEncoder.getInstance());
        return encoder;
    }
}
