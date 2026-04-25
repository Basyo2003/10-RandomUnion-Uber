package com.team10.uber.driver.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import com.team10.uber.driver.singleton.JwtConfigurationManager;

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
