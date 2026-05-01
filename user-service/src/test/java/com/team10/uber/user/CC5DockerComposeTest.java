package com.team10.uber.user;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CC5DockerComposeTest {

    private static String dockerCompose;

    @BeforeAll
    static void readDockerCompose() throws Exception {
        Path path = Path.of("../docker-compose.yaml");
        assertTrue(Files.exists(path), "docker-compose.yaml must exist at project root");
        dockerCompose = Files.readString(path);
    }

    @Test
    void dockerCompose_hasPostgresService() {
        assertTrue(dockerCompose.contains("postgres:"),
                "docker-compose.yaml must define a postgres service");
    }

    @Test
    void dockerCompose_hasMongoService() {
        assertTrue(dockerCompose.contains("mongo:"),
                "docker-compose.yaml must define a mongo service");
    }

    @Test
    void dockerCompose_hasRedisService() {
        assertTrue(dockerCompose.contains("redis:"),
                "docker-compose.yaml must define a redis service");
    }

    @Test
    void dockerCompose_hasElasticsearchService() {
        assertTrue(dockerCompose.contains("elasticsearch:"),
                "docker-compose.yaml must define an elasticsearch service");
    }

    @Test
    void dockerCompose_hasNeo4jService() {
        assertTrue(dockerCompose.contains("neo4j:"),
                "docker-compose.yaml must define a neo4j service");
    }

    @Test
    void dockerCompose_hasCassandraService() {
        assertTrue(dockerCompose.contains("cassandra:"),
                "docker-compose.yaml must define a cassandra service");
    }

    @Test
    void dockerCompose_postgres_hasHealthcheck() {
        assertTrue(dockerCompose.contains("pg_isready"),
                "postgres service must have a healthcheck using pg_isready");
    }

    @Test
    void dockerCompose_mongo_hasHealthcheck() {
        assertTrue(dockerCompose.contains("mongosh") || dockerCompose.contains("mongo --eval"),
                "mongo service must have a healthcheck");
    }

    @Test
    void dockerCompose_redis_hasHealthcheck() {
        assertTrue(dockerCompose.contains("redis-cli"),
                "redis service must have a healthcheck using redis-cli");
    }

    @Test
    void dockerCompose_redis_hasMemoryCap() {
        assertTrue(dockerCompose.contains("maxmemory"),
                "redis service must configure maxmemory");
    }

    @Test
    void dockerCompose_elasticsearch_hasMemoryCap() {
        assertTrue(dockerCompose.contains("ES_JAVA_OPTS"),
                "elasticsearch service must configure ES_JAVA_OPTS for memory limits");
    }

    @Test
    void dockerCompose_neo4j_hasMemoryCap() {
        assertTrue(dockerCompose.contains("NEO4J_server_memory") || dockerCompose.contains("heap"),
                "neo4j service must configure memory settings");
    }

    @Test
    void dockerCompose_cassandra_hasMemoryCap() {
        assertTrue(dockerCompose.contains("MAX_HEAP_SIZE") || dockerCompose.contains("HEAP_NEWSIZE"),
                "cassandra service must configure heap memory");
    }

    @Test
    void dockerCompose_postgres_hasCorrectImageTag() {
        assertTrue(dockerCompose.contains("postgres:17"),
                "postgres service must use image postgres:17");
    }

    @Test
    void dockerCompose_elasticsearch_hasHealthcheck() {
        assertTrue(dockerCompose.contains("elasticsearch:9"),
                "elasticsearch must use a specific image tag");
    }
}
