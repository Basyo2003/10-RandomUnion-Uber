package com.team10.uber.payment.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RedisConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) // Default fallback
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> specificConfigs = new HashMap<>();
        
        // 5 Min TTL: Search results
        specificConfigs.put("payment-service::S5-F1", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // 10 Min TTL: Dashboards, Reports, and DTOs
        specificConfigs.put("payment-service::S5-F3", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        specificConfigs.put("payment-service::S5-F6", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        specificConfigs.put("payment-service::S5-F9", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        specificConfigs.put("payment-service::S5-F10", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        specificConfigs.put("payment-service::S5-F11", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        
        // 15 Min TTL: Entity Details & Relationship DTOs
        specificConfigs.put("payment-service::payment", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        specificConfigs.put("payment-service::coupon", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        specificConfigs.put("payment-service::payment_coupon", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        specificConfigs.put("payment-service::S5-F8", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(specificConfigs)
                .build();
    }
}