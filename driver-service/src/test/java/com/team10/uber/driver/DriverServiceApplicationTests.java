package com.team10.uber.driver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.contracts.feign.UserServiceClient;
import com.team10.uber.driver.repository.elastic.DriverSearchRepository;
import com.team10.uber.driver.repository.mongo.DriverEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class DriverServiceApplicationTests {

    // Infrastructure mocks — all external services are unavailable in unit test env
    @MockitoBean StringRedisTemplate stringRedisTemplate;
    @MockitoBean ElasticsearchOperations elasticsearchOperations;
    @MockitoBean DriverSearchRepository driverSearchRepository;
    @MockitoBean DriverEventRepository driverEventRepository;
    @MockitoBean MongoClient mongoClient;
    @MockitoBean MongoDatabaseFactory mongoDatabaseFactory;
    @MockitoBean MongoTemplate mongoTemplate;
    @MockitoBean RideServiceClient rideServiceClient;
    @MockitoBean UserServiceClient userServiceClient;
    @MockitoBean ConnectionFactory connectionFactory;
    @MockitoBean ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }
}
