package com.team10.uber.payment.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
    private static final String PREFIX = "payment-service::";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisCacheService(StringRedisTemplate redis) {
        this.redis = redis;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public <T> void put(String key, T value, long ttlSeconds) {
        try {
            redis.opsForValue().set(PREFIX + key,
                    mapper.writeValueAsString(value), ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis put failed for key {}: {}", key, e.getMessage());
        }
    }

    public <T> T get(String key, Class<T> type) {
        try {
            String json = redis.opsForValue().get(PREFIX + key);
            if (json == null) return null;
            return mapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Redis get failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    public <T> T get(String key, TypeReference<T> typeRef) {
        try {
            String json = redis.opsForValue().get(PREFIX + key);
            if (json == null) return null;
            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("Redis get failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    public void evict(String key) {
        try {
            redis.delete(PREFIX + key);
        } catch (Exception e) {
            log.warn("Redis evict failed for key {}: {}", key, e.getMessage());
        }
    }

    public void evictByPattern(String pattern) {
        try {
            Set<String> keys = redis.keys(PREFIX + pattern);
            if (keys != null && !keys.isEmpty()) {
                Collection<String> keyList = new ArrayList<>(keys);
                redis.delete(keyList);
            }
        } catch (Exception e) {
            log.warn("Redis evictByPattern failed for {}: {}", pattern, e.getMessage());
        }
    }
}