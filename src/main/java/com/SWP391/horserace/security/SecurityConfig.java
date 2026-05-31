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
 * <p><b>DEV mode:</b> every endpoint is currently <b>permitAll()</b> — no authorization is
 * required to call any API. The JWT filter still runs, so a valid {@code Authorization: Bearer}
 * token is parsed and the user is populated into the SecurityContext (useful for
 * {@code @AuthenticationPrincipal}), but it is not enforced.
 *
 * <p>To re-enable protection later, replace {@code anyRequest().permitAll()} with the
 * permit-/auth/** + authenticated() rules.
 *
 * <p>No HTTP session, no CSRF (token-based API). {@code @EnableMethodSecurity} is on so
 * {@code @PreAuthorize} still works where you add it explicitly.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // DEV: permit everything — no endpoint requires authorization.
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
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
