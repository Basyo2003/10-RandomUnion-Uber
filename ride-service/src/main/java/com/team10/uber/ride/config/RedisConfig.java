package com.team10.uber.ride.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;


@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    @SuppressWarnings("deprecation")
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(
                        LaissezFaireSubTypeValidator.instance,
                        ObjectMapper.DefaultTyping.NON_FINAL);

        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        // Entity detail caches
        cacheConfigs.put("ride-service::ride",     defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigs.put("ride-service::rideStop", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        // Feature caches
        cacheConfigs.put("ride-service::S3-F1",  defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("ride-service::S3-F3",  defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("ride-service::S3-F5",  defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("ride-service::S3-F6",  defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("ride-service::S3-F9",  defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("ride-service::S3-F10", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("ride-service::S3-F12", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}