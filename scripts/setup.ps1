# =============================================================================
# setup.ps1 — Levanta el sistema gym-microservices en Windows
# Ejecutar desde la raiz del proyecto con:
#   powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
# =============================================================================

$ErrorActionPreference = "Stop"
$ROOT = Split-Path $MyInvocation.MyCommand.Path -Parent | Split-Path -Parent

function Write-Info  { param($msg) Write-Host "[INFO]  $msg" -ForegroundColor Green }
function Write-Warn  { param($msg) Write-Host "[WARN]  $msg" -ForegroundColor Yellow }
function Write-Err   { param($msg) Write-Host "[ERROR] $msg" -ForegroundColor Red; exit 1 }

Set-Location $ROOT
Write-Info "Directorio de trabajo: $ROOT"

# ── 1. Verificar prerequisitos ──────────────────────────────────
Write-Info "Verificando prerequisitos..."
foreach ($tool in @("docker","minikube","kubectl","helm")) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        Write-Err "$tool no encontrado. Instalalo antes de continuar."
    }
}
Write-Info "Todos los prerequisitos estan instalados OK"

# ── 2. Iniciar Minikube ─────────────────────────────────────────
Write-Info "Iniciando Minikube..."
$status = minikube status 2>&1
if ($status -match "Running") {
    Write-Warn "Minikube ya esta corriendo, continuando..."
} else {
    minikube start --cpus=4 --memory=6144 --disk-size=20g --driver=docker
}

Write-Info "Habilitando metrics-server..."
minikube addons enable metrics-server

# ── 3. Instalar stack de monitoreo ──────────────────────────────
Write-Info "Agregando repo prometheus-community..."
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts 2>$null
helm repo update

Write-Info "Instalando kube-prometheus-stack..."
$helmStatus = helm status prometheus -n monitoring 2>&1
if ($helmStatus -match "deployed") {
    Write-Warn "Ya instalado, actualizando..."
    helm upgrade prometheus prometheus-community/kube-prometheus-stack `
        --namespace monitoring `
        -f k8s\monitoring\prometheus-values.yaml `
        --wait --timeout=5m
} else {
    helm install prometheus prometheus-community/kube-prometheus-stack `
        --namespace monitoring --create-namespace `
        -f k8s\monitoring\prometheus-values.yaml `
        --wait --timeout=5m
}
Write-Info "Stack de monitoreo instalado OK"

# ── 4. Construir imagenes dentro de Minikube ────────────────────
Write-Info "Apuntando Docker al daemon de Minikube..."
# Obtener variables de entorno de Minikube y aplicarlas
$minikubeEnv = minikube docker-env --shell=powershell | Out-String
Invoke-Expression $minikubeEnv

Write-Info "Construyendo imagen gym-service..."
docker build -t gym-service:1.0.0 microservices\gym-service\

Write-Info "Construyendo imagen booking-service..."
docker build -t booking-service:1.0.0 microservices\booking-service\

Write-Info "Imagenes construidas OK"

# ── 5. Aplicar manifiestos ──────────────────────────────────────
Write-Info "Aplicando manifiestos de Kubernetes..."
kubectl apply -f k8s\namespace.yaml
kubectl apply -f k8s\gym-service\deployment.yaml
kubectl apply -f k8s\gym-service\service.yaml
kubectl apply -f k8s\gym-service\service-monitor.yaml
kubectl apply -f k8s\booking-service\deployment.yaml
kubectl apply -f k8s\booking-service\service.yaml
kubectl apply -f k8s\booking-service\service-monitor.yaml
kubectl apply -f k8s\prometheus-rules.yaml

Write-Info "Manifiestos aplicados OK"

# ── 6. Esperar pods ─────────────────────────────────────────────
Write-Info "Esperando gym-service..."
kubectl rollout status deployment/gym-service -n gym-app --timeout=120s

Write-Info "Esperando booking-service..."
kubectl rollout status deployment/booking-service -n gym-app --timeout=120s

# ── 7. Mostrar URLs ─────────────────────────────────────────────
$IP = minikube ip

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  Sistema levantado exitosamente!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  gym-service:     http://${IP}:30080" -ForegroundColor White
Write-Host "  booking-service: http://${IP}:30081" -ForegroundColor White
Write-Host "  Grafana:         http://${IP}:32000  (admin / GymApp2024!)" -ForegroundColor Yellow
Write-Host "  Prometheus:      http://${IP}:32090" -ForegroundColor Yellow
Write-Host "  AlertManager:    http://${IP}:32093" -ForegroundColor Yellow
Write-Host ""
Write-Host "  kubectl get pods -n gym-app" -ForegroundColor Gray
Write-Host ""
