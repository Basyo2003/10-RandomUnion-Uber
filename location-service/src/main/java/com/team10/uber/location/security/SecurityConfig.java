package com.team10.uber.location.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    // 5.4 Security Configuration - stateless JWT security for Location Service
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 5.4 Security Configuration - CSRF disabled for token-based API
                .csrf(AbstractHttpConfigurer::disable)

                // 5.4 Security Configuration - no server-side sessions
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 5.4 Security Configuration - health is public, all other endpoints require JWT
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )

                // 5.4 Security Configuration - register custom JWT filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}