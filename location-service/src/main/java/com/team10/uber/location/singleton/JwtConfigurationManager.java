package com.team10.uber.location.singleton;

import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import javax.crypto.SecretKey;

public class JwtConfigurationManager {

    private static volatile JwtConfigurationManager instance;

    private SecretKey secretKey;
    private long expirationMs;
    private final String algorithm = "HS256";

    // §3.6 — Private constructor
    private JwtConfigurationManager() {}

    // §3.6 — Thread-safe getInstance()
    public static JwtConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (JwtConfigurationManager.class) {
                if (instance == null) {
                    instance = new JwtConfigurationManager();
                }
            }
        }
        return instance;
    }

    // §3.6 — Bridge method: called by Spring config bean during @PostConstruct
    public static void initConfig(String secret, long expirationMs) {
        JwtConfigurationManager mgr = getInstance();
        mgr.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        mgr.expirationMs = expirationMs;
    }

    public SecretKey getSecretKey() { return secretKey; }
    public long getExpirationMs()   { return expirationMs; }
    public String getAlgorithm()    { return algorithm; }
}