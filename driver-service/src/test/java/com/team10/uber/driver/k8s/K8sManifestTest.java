package com.team10.uber.driver.k8s;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class K8sManifestTest {

    private static final Path K8S = Paths.get("../k8s");

    private String read(String relativePath) throws IOException {
        Path p = K8S.resolve(relativePath);
        assertThat(p).as("File must exist: %s", p.toAbsolutePath()).exists();
        return Files.readString(p);
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx++;
        }
        return count;
    }

    @Nested
    @DisplayName("ConfigMap - k8s/configmaps/driver-service-configmap.yaml")
    class ConfigMapTests {

        @Test
        @DisplayName("file exists in correct §10.1 location")
        void fileExists() throws IOException {
            read("configmaps/driver-service-configmap.yaml");
        }

        @Test
        @DisplayName("namespace is 'uber' (§10.2)")
        void namespace() throws IOException {
            assertThat(read("configmaps/driver-service-configmap.yaml")).contains("namespace: uber");
        }

        @Test
        @DisplayName("datasource URL -> driver-postgres:5432/uberdb-drivers (§1.1 DB isolation)")
        void datasourceUrl() throws IOException {
            assertThat(read("configmaps/driver-service-configmap.yaml"))
                    .contains("driver-postgres:5432/uberdb-drivers");
        }

        @Test
        @DisplayName("FEIGN_RIDE_SERVICE_URL points to internal DNS (§2.2)")
        void feignRideUrl() throws IOException {
            assertThat(read("configmaps/driver-service-configmap.yaml"))
                    .contains("FEIGN_RIDE_SERVICE_URL: http://ride-service:8080");
        }

        @Test
        @DisplayName("FEIGN_USER_SERVICE_URL points to internal DNS (§2.2)")
        void feignUserUrl() throws IOException {
            assertThat(read("configmaps/driver-service-configmap.yaml"))
                    .contains("FEIGN_USER_SERVICE_URL: http://user-service:8080");
        }

        @Test
        @DisplayName("RabbitMQ host is 'rabbitmq' (§2.6)")
        void rabbitmqHost() throws IOException {
            assertThat(read("configmaps/driver-service-configmap.yaml"))
                    .contains("SPRING_RABBITMQ_HOST: rabbitmq");
        }

        @Test
        @DisplayName("auto-ACK mode enabled (§2.7 - auto ACK + DLQ)")
        void autoAck() throws IOException {
            assertThat(read("configmaps/driver-service-configmap.yaml"))
                    .contains("SPRING_RABBITMQ_LISTENER_SIMPLE_ACKNOWLEDGE_MODE: auto");
        }

        @Test
        @DisplayName("default-requeue-rejected=false (§2.7 - route to DLQ on exhaustion)")
        void noRequeue() throws IOException {
            assertThat(read("configmaps/driver-service-configmap.yaml"))
                    .contains("SPRING_RABBITMQ_LISTENER_SIMPLE_DEFAULT_REQUEUE_REJECTED: \"false\"");
        }

        @Test
        @DisplayName("retry max-attempts = 3 (§2.7)")
        void retryAttempts() throws IOException {
            assertThat(read("configmaps/driver-service-configmap.yaml"))
                    .contains("SPRING_RABBITMQ_LISTENER_SIMPLE_RETRY_MAX_ATTEMPTS: \"3\"");
        }
    }

    @Nested
    @DisplayName("Deployment - k8s/deployments/driver-service-deployment.yaml")
    class DeploymentTests {

        @Test
        @DisplayName("file exists in correct §10.1 location")
        void fileExists() throws IOException {
            read("deployments/driver-service-deployment.yaml");
        }

        @Test
        @DisplayName("namespace is 'uber'")
        void namespace() throws IOException {
            assertThat(read("deployments/driver-service-deployment.yaml")).contains("namespace: uber");
        }

        @Test
        @DisplayName("memory limit is 768Mi (§10.8 - Spring Boot services)")
        void memoryLimit() throws IOException {
            assertThat(read("deployments/driver-service-deployment.yaml")).contains("memory: 768Mi");
        }

        @Test
        @DisplayName("memory request is 512Mi (§10.8)")
        void memoryRequest() throws IOException {
            assertThat(read("deployments/driver-service-deployment.yaml")).contains("memory: 512Mi");
        }

        @Test
        @DisplayName("readiness probe path is /actuator/health (§10.5)")
        void readinessPath() throws IOException {
            assertThat(read("deployments/driver-service-deployment.yaml")).contains("/actuator/health");
        }

        @Test
        @DisplayName("readiness probe initialDelaySeconds: 30 (§10.5)")
        void readinessDelay() throws IOException {
            assertThat(read("deployments/driver-service-deployment.yaml"))
                    .contains("initialDelaySeconds: 30");
        }

        @Test
        @DisplayName("liveness probe initialDelaySeconds: 60 (§10.5)")
        void livenessDelay() throws IOException {
            assertThat(read("deployments/driver-service-deployment.yaml"))
                    .contains("initialDelaySeconds: 60");
        }

        @Test
        @DisplayName("references driver-service-configmap (§10.5)")
        void refsConfigmap() throws IOException {
            assertThat(read("deployments/driver-service-deployment.yaml"))
                    .contains("driver-service-configmap");
        }

        @Test
        @DisplayName("references driver-postgres-secret (§10.5)")
        void refsDbSecret() throws IOException {
            assertThat(read("deployments/driver-service-deployment.yaml"))
                    .contains("driver-postgres-secret");
        }

        @Test
        @DisplayName("references jwt-secret (§10.3 - JWT_SECRET env)")
        void refsJwtSecret() throws IOException {
            assertThat(read("deployments/driver-service-deployment.yaml")).contains("jwt-secret");
        }

        @Test
        @DisplayName("imagePullPolicy: IfNotPresent (required for MiniKube local images)")
        void imagePullPolicy() throws IOException {
            assertThat(read("deployments/driver-service-deployment.yaml"))
                    .contains("imagePullPolicy: IfNotPresent");
        }
    }

    @Nested
    @DisplayName("Service - k8s/services/driver-service-svc.yaml")
    class ServiceTests {

        @Test
        @DisplayName("file exists in correct §10.1 location")
        void fileExists() throws IOException {
            read("services/driver-service-svc.yaml");
        }

        @Test
        @DisplayName("type is ClusterIP (§10.6 - only gateway uses NodePort)")
        void typeClusterIP() throws IOException {
            assertThat(read("services/driver-service-svc.yaml")).contains("type: ClusterIP");
        }

        @Test
        @DisplayName("port is 8080 (§10.1 table - all services run on 8080)")
        void port8080() throws IOException {
            assertThat(read("services/driver-service-svc.yaml")).contains("port: 8080");
        }

        @Test
        @DisplayName("selector app: driver-service matches deployment labels")
        void selector() throws IOException {
            assertThat(read("services/driver-service-svc.yaml")).contains("app: driver-service");
        }

        @Test
        @DisplayName("namespace is 'uber'")
        void namespace() throws IOException {
            assertThat(read("services/driver-service-svc.yaml")).contains("namespace: uber");
        }
    }

    @Nested
    @DisplayName("Grafana Dashboard - k8s/monitoring/grafana/dashboards/driver-dashboard.json")
    class DashboardTests {

        @Test
        @DisplayName("file exists in correct §12 location")
        void fileExists() throws IOException {
            read("monitoring/grafana/dashboards/driver-dashboard.json");
        }

        @Test
        @DisplayName("contains 'panels' array (valid Grafana JSON schema)")
        void hasPanelsKey() throws IOException {
            assertThat(read("monitoring/grafana/dashboards/driver-dashboard.json"))
                    .contains("\"panels\"");
        }

        @Test
        @DisplayName("uid is 'uber-driver-service' (unique across all 5 dashboards)")
        void uid() throws IOException {
            assertThat(read("monitoring/grafana/dashboards/driver-dashboard.json"))
                    .contains("\"uid\": \"uber-driver-service\"");
        }

        @Test
        @DisplayName("has ≥3 PromQL targets with job=driver-service (§11.4 requires ≥3 PromQL panels)")
        void hasMinimum3PromQLPanels() throws IOException {
            String content = read("monitoring/grafana/dashboards/driver-dashboard.json");
            int count = countOccurrences(content, "job=\\\"driver-service\\\"");
            assertThat(count)
                    .as("Expected ≥3 PromQL targets with job=driver-service (got %d). §11.4 requires ≥3 PromQL panels.", count)
                    .isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("schemaVersion is 36 (Grafana 10.x compatibility)")
        void schemaVersion() throws IOException {
            assertThat(read("monitoring/grafana/dashboards/driver-dashboard.json"))
                    .contains("\"schemaVersion\": 36");
        }

        @Test
        @DisplayName("refresh interval is set (dashboard auto-refreshes)")
        void refresh() throws IOException {
            assertThat(read("monitoring/grafana/dashboards/driver-dashboard.json"))
                    .contains("\"refresh\"");
        }
    }

    @Nested
    @DisplayName("application.yml - actuator config (§11.1)")
    class ApplicationYmlTests {

        @Test
        @DisplayName("exposes 'prometheus' endpoint")
        void prometheusEndpoint() throws IOException {
            Path p = Paths.get("src/main/resources/application.yml");
            assertThat(p).as("application.yml must exist").exists();
            assertThat(Files.readString(p)).contains("prometheus");
        }

        @Test
        @DisplayName("exposes 'health' endpoint (required for K8s readiness/liveness probes)")
        void healthEndpoint() throws IOException {
            assertThat(Files.readString(Paths.get("src/main/resources/application.yml")))
                    .contains("health");
        }

        @Test
        @DisplayName("percentiles-histogram for http.server.requests (feeds P99 latency panel)")
        void percentilesHistogram() throws IOException {
            assertThat(Files.readString(Paths.get("src/main/resources/application.yml")))
                    .contains("percentiles-histogram")
                    .contains("http.server.requests");
        }
    }

    @Nested
    @DisplayName("pom.xml - dependency declarations")
    class PomTests {

        @Test
        @DisplayName("declares contracts dependency (§12.1)")
        void contractsDep() throws IOException {
            assertThat(Files.readString(Paths.get("pom.xml")))
                    .contains("<artifactId>contracts</artifactId>");
        }

        @Test
        @DisplayName("declares micrometer-registry-prometheus (§11.1)")
        void micrometerDep() throws IOException {
            assertThat(Files.readString(Paths.get("pom.xml")))
                    .contains("micrometer-registry-prometheus");
        }

        @Test
        @DisplayName("declares spring-boot-starter-actuator (§11.1)")
        void actuatorDep() throws IOException {
            assertThat(Files.readString(Paths.get("pom.xml")))
                    .contains("spring-boot-starter-actuator");
        }
    }
}
