#!/usr/bin/env bash
# Full cluster deployment script for the Uber Ride-Hailing Platform (M3).
# Run from the repo root: bash k8s/deploy-all.sh
# Requires: kubectl configured against the target cluster.

set -euo pipefail
K8S="$(cd "$(dirname "$0")" && pwd)"

wait_ready() {
  local kind="$1" name="$2" ns="${3:-uber}"
  echo "  Waiting for $kind/$name in ns=$ns ..."
  kubectl rollout status "$kind/$name" -n "$ns" --timeout=300s
}

# ── 1. Namespaces ──────────────────────────────────────────────────────────────
echo "[1/9] Namespaces"
kubectl apply -f "$K8S/namespaces/namespace.yaml"
kubectl apply -f "$K8S/namespaces/monitoring-namespace.yaml"

# ── 2. Secrets ────────────────────────────────────────────────────────────────
echo "[2/9] Secrets"
kubectl apply -f "$K8S/secrets/driver-postgres-secret.yaml"
kubectl apply -f "$K8S/secrets/payment-postgres-secret.yaml"

# ── 3. ConfigMaps ─────────────────────────────────────────────────────────────
echo "[3/9] ConfigMaps"
kubectl apply -f "$K8S/configmaps/user-service-configmap.yaml"
kubectl apply -f "$K8S/configmaps/driver-service-configmap.yaml"
kubectl apply -f "$K8S/configmaps/payment-service-configmap.yaml"
kubectl apply -f "$K8S/configmaps/gateway-configmap.yaml"
kubectl apply -f "$K8S/monitoring/loki/loki-configmap.yaml"
kubectl apply -f "$K8S/monitoring/prometheus/prometheus-configmap.yaml"
kubectl apply -f "$K8S/monitoring/grafana/grafana-datasources.yaml"
kubectl apply -f "$K8S/monitoring/grafana/grafana-dashboards.yaml"

# ── 4. PersistentVolumeClaims ─────────────────────────────────────────────────
echo "[4/9] PersistentVolumeClaims"
kubectl apply -f "$K8S/pvcs/redis-pvc.yaml"
kubectl apply -f "$K8S/pvcs/payment-postgres-pvc.yaml"
kubectl apply -f "$K8S/monitoring/loki/loki-pvc.yaml"
kubectl apply -f "$K8S/monitoring/prometheus/prometheus-pvc.yaml"
kubectl apply -f "$K8S/monitoring/grafana/grafana-pvc.yaml"

# ── 5. Infrastructure StatefulSets (shared infra first) ───────────────────────
echo "[5/9] Infrastructure StatefulSets"
kubectl apply -f "$K8S/statefulsets/mongo-statefulset.yaml"
kubectl apply -f "$K8S/statefulsets/redis-statefulset.yaml"
kubectl apply -f "$K8S/statefulsets/rabbitmq-statefulset.yaml"
kubectl apply -f "$K8S/statefulsets/elasticsearch-statefulset.yaml"

# Database StatefulSets (service-owned)
kubectl apply -f "$K8S/statefulsets/driver-postgres-statefulset.yaml"
kubectl apply -f "$K8S/statefulsets/payment-postgres-statefulset.yaml"

echo "  Waiting for infra StatefulSets to become ready ..."
wait_ready statefulset mongo
wait_ready statefulset redis
wait_ready statefulset rabbitmq
wait_ready statefulset elasticsearch
wait_ready statefulset driver-postgres
wait_ready statefulset payment-postgres

# ── 6. Services (DB / infra tier) ─────────────────────────────────────────────
echo "[6/9] Infrastructure Services"
kubectl apply -f "$K8S/services/mongo-svc.yaml"
kubectl apply -f "$K8S/services/redis-svc.yaml"
kubectl apply -f "$K8S/services/rabbitmq-svc.yaml"
kubectl apply -f "$K8S/services/elasticsearch-svc.yaml"
kubectl apply -f "$K8S/services/driver-postgres-svc.yaml"
kubectl apply -f "$K8S/services/payment-postgres-svc.yaml"

# ── 7. Application Deployments ────────────────────────────────────────────────
echo "[7/9] Application Deployments"
kubectl apply -f "$K8S/deployments/user-service-deployment.yaml"
kubectl apply -f "$K8S/deployments/driver-service-deployment.yaml"
kubectl apply -f "$K8S/deployments/payment-service-deployment.yaml"

echo "  Waiting for application Deployments to become ready ..."
wait_ready deployment user-service
wait_ready deployment driver-service
wait_ready deployment payment-service

kubectl apply -f "$K8S/services/user-service-svc.yaml"
kubectl apply -f "$K8S/services/driver-service-svc.yaml"
kubectl apply -f "$K8S/services/payment-service-svc.yaml"

# ── 8. API Gateway ────────────────────────────────────────────────────────────
echo "[8/9] API Gateway"
kubectl apply -f "$K8S/api-gateway/gateway-deployment.yaml"
wait_ready deployment api-gateway
kubectl apply -f "$K8S/api-gateway/gateway-service.yaml"

# ── 9. Monitoring Stack ───────────────────────────────────────────────────────
echo "[9/9] Monitoring Stack (Loki → Prometheus → Grafana)"
kubectl apply -f "$K8S/monitoring/loki/loki-statefulset.yaml"
kubectl apply -f "$K8S/monitoring/loki/loki-service.yaml"
wait_ready statefulset loki monitoring

kubectl apply -f "$K8S/monitoring/prometheus/prometheus-deployment.yaml"
kubectl apply -f "$K8S/monitoring/prometheus/prometheus-service.yaml"
wait_ready deployment prometheus monitoring

kubectl apply -f "$K8S/monitoring/grafana/grafana-deployment.yaml"
kubectl apply -f "$K8S/monitoring/grafana/grafana-service.yaml"
wait_ready deployment grafana monitoring

echo ""
echo "Deployment complete."
echo "  API Gateway NodePort : $(kubectl get svc api-gateway -n uber -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null || echo 'see kubectl get svc api-gateway -n uber')"
echo "  Grafana NodePort     : $(kubectl get svc grafana -n monitoring -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null || echo 'see kubectl get svc grafana -n monitoring')"
