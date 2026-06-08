#!/usr/bin/env bash
# =============================================================================
# teardown.sh — Elimina todos los recursos del sistema gym-microservices
# Uso:
#   bash scripts/teardown.sh           # elimina recursos, mantiene el cluster
#   bash scripts/teardown.sh --all     # también elimina Minikube completo
# =============================================================================
set -e

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info() { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $*"; }

# ── Eliminar manifiestos de la app ──────────────────────────────
info "Eliminando recursos de gym-app..."
kubectl delete -f k8s/prometheus-rules.yaml       --ignore-not-found
kubectl delete -f k8s/booking-service/service-monitor.yaml --ignore-not-found
kubectl delete -f k8s/booking-service/service.yaml         --ignore-not-found
kubectl delete -f k8s/booking-service/deployment.yaml      --ignore-not-found
kubectl delete -f k8s/gym-service/service-monitor.yaml     --ignore-not-found
kubectl delete -f k8s/gym-service/service.yaml             --ignore-not-found
kubectl delete -f k8s/gym-service/deployment.yaml          --ignore-not-found
kubectl delete -f k8s/namespace.yaml                       --ignore-not-found

info "Recursos de gym-app eliminados ✓"

# ── Desinstalar stack de monitoreo ──────────────────────────────
if helm status prometheus -n monitoring &>/dev/null; then
    info "Desinstalando kube-prometheus-stack..."
    helm uninstall prometheus -n monitoring
    kubectl delete namespace monitoring --ignore-not-found
    info "Stack de monitoreo eliminado ✓"
else
    warn "Stack de monitoreo no estaba instalado"
fi

# ── Opción --all: eliminar Minikube ─────────────────────────────
if [[ "$1" == "--all" ]]; then
    info "Eliminando cluster Minikube completo..."
    minikube delete
    info "Cluster eliminado ✓"
else
    info "Cluster Minikube mantenido. Usa --all para eliminarlo también."
fi

echo ""
echo "═══════════════════════════════════════"
echo "  ✅  Teardown completado"
echo "═══════════════════════════════════════"
