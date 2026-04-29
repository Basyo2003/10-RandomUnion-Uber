package com.team10.uber.ride.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;


@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    @SuppressWarnings("deprecation")
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("ride-service::S3-F1", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("ride-service::S3-F3", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("ride-service::S3-F5", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("ride-service::S3-F6", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("ride-service::S3-F9", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("ride-service::S3-F10", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}