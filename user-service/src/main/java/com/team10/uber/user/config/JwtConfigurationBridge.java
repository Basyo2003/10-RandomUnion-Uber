package com.team10.uber.user.config;

import com.team10.uber.user.singleton.JwtConfigurationManager;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfigurationBridge {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expirationMs;

    // Pushes yml values into the singleton on startup
    @PostConstruct
    public void init() {
        JwtConfigurationManager.initConfig(secret, expirationMs);
    }
}
