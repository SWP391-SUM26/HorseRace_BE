package com.SWP391.horserace.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/error"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
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
