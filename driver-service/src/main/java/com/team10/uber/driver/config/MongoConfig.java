package com.team10.uber.driver.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoDatabaseFactorySupport;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.data.mongodb.connect-timeout-ms:3000}")
    private int mongoConnectTimeoutMs;

    @Value("${spring.data.mongodb.server-selection-timeout-ms:3000}")
    private int mongoServerSelectionTimeoutMs;

    @Bean
    @Primary
    public MongoClient mongoClient() {
        if (mongoUri == null || mongoUri.trim().isEmpty()) {
            throw new IllegalStateException("Required property 'spring.data.mongodb.uri' must be configured");
        }
        ConnectionString cs = new ConnectionString(mongoUri);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(cs)
                .applyToSocketSettings(b -> b.connectTimeout(mongoConnectTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS))
                .applyToClusterSettings(b -> b.serverSelectionTimeout(mongoServerSelectionTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS))
                .build();
        System.out.println("[MongoConfig] Connecting to: " + cs.getHosts() + " db=" + cs.getDatabase() + " user="
                + cs.getUsername());
        return MongoClients.create(settings);
    }

    @Bean
    @Primary
    public MongoDatabaseFactory mongoDatabaseFactory(MongoClient mongoClient) {
        ConnectionString cs = new ConnectionString(mongoUri);
        String db = cs.getDatabase() != null ? cs.getDatabase() : "ubermongo";
        return new SimpleMongoClientDatabaseFactory(mongoClient, db);
    }

    @Bean
    @Primary
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory) {
        return new MongoTemplate(factory);
    }
}