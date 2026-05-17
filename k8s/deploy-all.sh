#!/usr/bin/env bash
# Full cluster deployment script for the Uber Ride-Hailing Platform (M3).
# Run from the repo root: bash k8s/deploy-all.sh [--build]
# --build  : build JARs, Docker images, and load into Minikube before deploying (first run)
# Requires: kubectl configured against the target cluster.

set -euo pipefail
K8S="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$K8S")"

# ── 0. Build (--build flag) ────────────────────────────────────────────────────
BUILD=false
for arg in "$@"; do [[ "$arg" == "--build" ]] && BUILD=true; done

if $BUILD; then
  echo "[0/9] Building JARs, Docker images, and loading into Minikube"

  echo "  Building all Maven modules (skipping tests) ..."
  (cd "$ROOT" && ./mvnw package -DskipTests -q)

  SERVICES=(user-service driver-service payment-service ride-service location-service api-gateway)
  for svc in "${SERVICES[@]}"; do
    echo "  docker build $svc ..."
    docker build -t "$svc:latest" "$ROOT/$svc"
  done

  echo "  Loading images into Minikube ..."
  for svc in "${SERVICES[@]}"; do
    echo "    minikube image load $svc:latest"
    minikube image load "$svc:latest"
  done

  echo "  Build complete."
fi

wait_ready() {
  local kind="$1" name="$2" ns="${3:-uber}" timeout="${4:-600s}"
  echo "  Waiting for $kind/$name in ns=$ns (timeout=${timeout}) ..."
  if ! kubectl rollout status "$kind/$name" -n "$ns" --timeout="$timeout"; then
    echo ""
    echo "  [ERROR] $kind/$name in ns=$ns did not become ready. Pod status:"
    kubectl get pods -n "$ns" -l "app=$name" -o wide 2>/dev/null || true
    echo "  [EVENTS]"
    kubectl describe "$kind/$name" -n "$ns" 2>/dev/null | tail -20 || true
    exit 1
  fi
}

# ── 1. Namespaces ──────────────────────────────────────────────────────────────
echo "[1/9] Namespaces"
kubectl apply -f "$K8S/namespaces/namespace.yaml"
kubectl apply -f "$K8S/namespaces/monitoring-namespace.yaml"

# ── 2. Secrets ────────────────────────────────────────────────────────────────
echo "[2/9] Secrets"
kubectl apply -f "$K8S/secrets/jwt-secret.yaml"
kubectl apply -f "$K8S/secrets/user-postgres-secret.yaml"
kubectl apply -f "$K8S/secrets/driver-postgres-secret.yaml"
kubectl apply -f "$K8S/secrets/payment-postgres-secret.yaml"
kubectl apply -f "$K8S/secrets/ride-postgres-secret.yaml"
kubectl apply -f "$K8S/secrets/location-postgres-secret.yaml"

# ── 3. ConfigMaps ─────────────────────────────────────────────────────────────
echo "[3/9] ConfigMaps"
kubectl apply -f "$K8S/configmaps/user-service-configmap.yaml"
kubectl apply -f "$K8S/configmaps/driver-service-configmap.yaml"
kubectl apply -f "$K8S/configmaps/payment-service-configmap.yaml"
kubectl apply -f "$K8S/configmaps/ride-service-configmap.yaml"
kubectl apply -f "$K8S/configmaps/location-service-configmap.yaml"
kubectl apply -f "$K8S/configmaps/gateway-configmap.yaml"
kubectl apply -f "$K8S/monitoring/loki/loki-configmap.yaml"
kubectl apply -f "$K8S/monitoring/prometheus/prometheus-configmap.yaml"
kubectl apply -f "$K8S/monitoring/grafana/grafana-datasources.yaml"
kubectl apply -f "$K8S/monitoring/grafana/grafana-dashboard-provider.yaml"
kubectl apply -k "$K8S/monitoring/grafana/"

# ── 4. PersistentVolumeClaims ─────────────────────────────────────────────────
echo "[4/9] PersistentVolumeClaims"
kubectl apply -f "$K8S/pvcs/mongo-pvc.yaml"
kubectl apply -f "$K8S/pvcs/neo4j-pvc.yaml"
kubectl apply -f "$K8S/pvcs/redis-pvc.yaml"
kubectl apply -f "$K8S/pvcs/user-postgres-pvc.yaml"
kubectl apply -f "$K8S/pvcs/payment-postgres-pvc.yaml"
kubectl apply -f "$K8S/pvcs/ride-postgres-pvc.yaml"
kubectl apply -f "$K8S/monitoring/loki/loki-pvc.yaml"
kubectl apply -f "$K8S/monitoring/prometheus/prometheus-pvc.yaml"
kubectl apply -f "$K8S/monitoring/grafana/grafana-pvc.yaml"

# ── 5. Governing Services (must exist before StatefulSets) ────────────────────
echo "[5a/9] Governing Services (headless, for StatefulSet DNS)"
kubectl apply -f "$K8S/services/mongo-svc.yaml"
kubectl apply -f "$K8S/services/redis-svc.yaml"
kubectl apply -f "$K8S/services/rabbitmq-svc.yaml"
kubectl apply -f "$K8S/services/elasticsearch-svc.yaml"
kubectl apply -f "$K8S/services/cassandra-svc.yaml"
kubectl apply -f "$K8S/services/neo4j-service.yaml"
kubectl apply -f "$K8S/services/user-postgres-svc.yaml"
kubectl apply -f "$K8S/services/driver-postgres-svc.yaml"
kubectl apply -f "$K8S/services/payment-postgres-svc.yaml"
kubectl apply -f "$K8S/services/ride-postgres-svc.yaml"
kubectl apply -f "$K8S/services/location-postgres-svc.yaml"

# ── 5b. Infrastructure StatefulSets ───────────────────────────────────────────
echo "[5b/9] Infrastructure StatefulSets"

# Shared infra
kubectl apply -f "$K8S/statefulsets/mongo-statefulset.yaml"
kubectl apply -f "$K8S/statefulsets/redis-statefulset.yaml"
kubectl apply -f "$K8S/statefulsets/rabbitmq-statefulset.yaml"
kubectl apply -f "$K8S/statefulsets/elasticsearch-statefulset.yaml"
kubectl apply -f "$K8S/statefulsets/cassandra-statefulset.yaml"
kubectl apply -f "$K8S/statefulsets/neo4j-statefulset.yaml"

# Service-owned databases
kubectl apply -f "$K8S/statefulsets/user-postgres-statefulset.yaml"
kubectl apply -f "$K8S/statefulsets/driver-postgres-statefulset.yaml"
kubectl apply -f "$K8S/statefulsets/payment-postgres-statefulset.yaml"
kubectl apply -f "$K8S/statefulsets/ride-postgres-statefulset.yaml"
kubectl apply -f "$K8S/statefulsets/location-postgres-statefulset.yaml"

echo "  Waiting for infra StatefulSets to become ready ..."
wait_ready statefulset mongo          uber 600s
wait_ready statefulset redis          uber 600s
wait_ready statefulset rabbitmq       uber 900s
wait_ready statefulset elasticsearch  uber 900s
wait_ready statefulset cassandra      uber 900s
wait_ready statefulset neo4j          uber 900s
wait_ready statefulset user-postgres     uber 600s
wait_ready statefulset driver-postgres   uber 600s
wait_ready statefulset payment-postgres  uber 600s
wait_ready statefulset ride-postgres     uber 600s
wait_ready statefulset location-postgres uber 600s

# ── 6. Application Services ───────────────────────────────────────────────────
echo "[6/9] Application Services"
kubectl apply -f "$K8S/services/user-service-svc.yaml"
kubectl apply -f "$K8S/services/driver-service-svc.yaml"
kubectl apply -f "$K8S/services/payment-service-svc.yaml"
kubectl apply -f "$K8S/services/ride-service-service.yaml"
kubectl apply -f "$K8S/services/location-service-svc.yaml"
kubectl apply -f "$K8S/api-gateway/gateway-service.yaml"

# ── 7. Application Deployments (all 5 services + API Gateway) ────────────────
echo "[7/9] Application Deployments"
kubectl apply -f "$K8S/deployments/user-service-deployment.yaml"
kubectl apply -f "$K8S/deployments/driver-service-deployment.yaml"
kubectl apply -f "$K8S/deployments/payment-service-deployment.yaml"
kubectl apply -f "$K8S/deployments/ride-service-deployment.yaml"
kubectl apply -f "$K8S/deployments/location-service-deployment.yaml"
kubectl apply -f "$K8S/api-gateway/gateway-deployment.yaml"

echo "  Waiting for application Deployments to become ready ..."
# 1200s = 20 min: allows for 300s liveness initial delay + ~15 min startup under CPU pressure
wait_ready deployment user-service     uber 1200s
wait_ready deployment driver-service   uber 1200s
wait_ready deployment payment-service  uber 1200s
wait_ready deployment ride-service     uber 1200s
wait_ready deployment location-service uber 1200s
wait_ready deployment api-gateway      uber 1200s

# ── 8. Monitoring Stack ───────────────────────────────────────────────────────
echo "[8/9] Monitoring Stack (Loki → Prometheus → Grafana)"
kubectl apply -f "$K8S/monitoring/loki/loki-statefulset.yaml"
kubectl apply -f "$K8S/monitoring/loki/loki-service.yaml"
wait_ready statefulset loki monitoring 600s

kubectl apply -f "$K8S/monitoring/prometheus/prometheus-deployment.yaml"
kubectl apply -f "$K8S/monitoring/prometheus/prometheus-service.yaml"
wait_ready deployment prometheus monitoring 600s

kubectl apply -f "$K8S/monitoring/grafana/grafana-deployment.yaml"
kubectl apply -f "$K8S/monitoring/grafana/grafana-service.yaml"
wait_ready deployment grafana monitoring 600s

# ── 9. Expose API Gateway via port-forward ────────────────────────────────────
echo "[9/9] Exposing API Gateway on localhost:8080"

# Kill any existing port-forward for the gateway
pkill -f "kubectl port-forward svc/api-gateway" 2>/dev/null || true
sleep 1

kubectl port-forward svc/api-gateway -n uber 8080:8080 \
  >> /tmp/gateway-port-forward.log 2>&1 &
PF_PID=$!
echo "  Port-forward started (PID=${PF_PID}). Logs: /tmp/gateway-port-forward.log"
sleep 2

if kill -0 "$PF_PID" 2>/dev/null; then
  echo "  Gateway is reachable at http://localhost:8080"
else
  echo "  [WARN] Port-forward exited early — check /tmp/gateway-port-forward.log"
fi

echo ""
echo "Deployment complete."
echo "All traffic enters through the API Gateway at http://localhost:8080"
echo ""
echo "  Routes:"
echo "    Users    : http://localhost:8080/api/users/**"
echo "    Auth     : http://localhost:8080/api/auth/**"
echo "    Drivers  : http://localhost:8080/api/drivers/**"
echo "    Rides    : http://localhost:8080/api/rides/**"
echo "    Locations: http://localhost:8080/api/locations/**"
echo "    Payments : http://localhost:8080/api/payments/**"
echo ""
echo "  Note: port-forward runs in the background (PID=${PF_PID})."
echo "  To stop it: kill ${PF_PID}  or  pkill -f 'kubectl port-forward svc/api-gateway'"
