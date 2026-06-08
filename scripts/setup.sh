#!/usr/bin/env bash
# =============================================================================
# setup.sh — Levanta el sistema gym-microservices desde cero
# Uso: bash scripts/setup.sh
# =============================================================================
set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── 1. Verificar prerequisitos ──────────────────────────────────
info "Verificando prerequisitos..."
command -v docker    &>/dev/null || error "Docker no encontrado. Instálalo desde https://www.docker.com/products/docker-desktop"
command -v minikube  &>/dev/null || error "Minikube no encontrado. Ver: https://minikube.sigs.k8s.io/docs/start/"
command -v kubectl   &>/dev/null || error "kubectl no encontrado. Ver: https://kubernetes.io/docs/tasks/tools/"
command -v helm      &>/dev/null || error "Helm no encontrado. Ver: https://helm.sh/docs/intro/install/"
info "Todos los prerequisitos están instalados ✓"

# ── 2. Iniciar Minikube ─────────────────────────────────────────
info "Iniciando Minikube con recursos suficientes..."
if minikube status &>/dev/null; then
    warn "Minikube ya está corriendo, continuando..."
else
    minikube start --cpus=4 --memory=6144 --disk-size=20g
fi

info "Habilitando metrics-server..."
minikube addons enable metrics-server

# ── 3. Instalar stack de monitoreo con Helm ─────────────────────
info "Agregando repositorio prometheus-community..."
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts 2>/dev/null || true
helm repo update

info "Instalando kube-prometheus-stack en namespace 'monitoring'..."
if helm status prometheus -n monitoring &>/dev/null; then
    warn "Stack de monitoreo ya instalado, actualizando..."
    helm upgrade prometheus prometheus-community/kube-prometheus-stack \
        --namespace monitoring \
        -f k8s/monitoring/prometheus-values.yaml \
        --wait --timeout=5m
else
    helm install prometheus prometheus-community/kube-prometheus-stack \
        --namespace monitoring --create-namespace \
        -f k8s/monitoring/prometheus-values.yaml \
        --wait --timeout=5m
fi
info "Stack de monitoreo instalado ✓"

# ── 4. Construir imágenes Docker dentro de Minikube ─────────────
info "Apuntando Docker al daemon interno de Minikube..."
eval $(minikube docker-env)

info "Construyendo imagen gym-service..."
docker build -t gym-service:1.0.0 microservices/gym-service/

info "Construyendo imagen booking-service..."
docker build -t booking-service:1.0.0 microservices/booking-service/

info "Imágenes construidas ✓"

# ── 5. Aplicar manifiestos de Kubernetes ────────────────────────
info "Creando namespace gym-app..."
kubectl apply -f k8s/namespace.yaml

info "Desplegando gym-service..."
kubectl apply -f k8s/gym-service/deployment.yaml
kubectl apply -f k8s/gym-service/service.yaml
kubectl apply -f k8s/gym-service/service-monitor.yaml

info "Desplegando booking-service..."
kubectl apply -f k8s/booking-service/deployment.yaml
kubectl apply -f k8s/booking-service/service.yaml
kubectl apply -f k8s/booking-service/service-monitor.yaml

info "Aplicando reglas de alerta..."
kubectl apply -f k8s/prometheus-rules.yaml

# ── 6. Esperar que los pods estén listos ────────────────────────
info "Esperando que gym-service esté listo..."
kubectl rollout status deployment/gym-service -n gym-app --timeout=120s

info "Esperando que booking-service esté listo..."
kubectl rollout status deployment/booking-service -n gym-app --timeout=120s

# ── 7. Mostrar URLs ─────────────────────────────────────────────
MINIKUBE_IP=$(minikube ip)

echo ""
echo "════════════════════════════════════════════════"
echo "  ✅  Sistema levantado exitosamente"
echo "════════════════════════════════════════════════"
echo ""
echo "  🏋️  gym-service:      http://${MINIKUBE_IP}:30080"
echo "  📅  booking-service:  http://${MINIKUBE_IP}:30081"
echo "  📊  Grafana:          http://${MINIKUBE_IP}:32000  (admin / GymApp2024!)"
echo "  🔥  Prometheus:       http://${MINIKUBE_IP}:32090"
echo "  🚨  AlertManager:     http://${MINIKUBE_IP}:32093"
echo ""
echo "  Verifica pods: kubectl get pods -n gym-app"
echo ""
