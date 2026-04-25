package com.team10.uber.payment.singleton;

public class JwtConfigurationManager {

    private static volatile JwtConfigurationManager instance;

    private String secretKey;
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

    // §3.6 — Bridge method: called by Spring @Configuration bean during @PostConstruct
    public static void initConfig(String secret, long expirationMs) {
        JwtConfigurationManager mgr = getInstance();
        mgr.secretKey = secret;
        mgr.expirationMs = expirationMs;
    }

    public String getSecretKey() { return secretKey; }
    public long getExpirationMs() { return expirationMs; }
    public String getAlgorithm() { return algorithm; }
}
