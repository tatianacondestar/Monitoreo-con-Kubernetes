# 🏋️ Gym Microservices — Kubernetes + Prometheus + Grafana

Sistema de microservicios para gestión de un gimnasio, con monitoreo completo en Kubernetes.

| Servicio | Descripción | Puerto |
|----------|-------------|--------|
| **gym-service** | Catálogo de membresías (Servicio A) | 8080 / NodePort 30080 |
| **booking-service** | Reservas de clases — consulta a gym-service (Servicio B) | 8081 / NodePort 30081 |

---

## 🗂️ Estructura del repositorio

```
gym-microservices/
├── microservices/
│   ├── gym-service/        # Servicio A: membresías
│   └── booking-service/    # Servicio B: reservas (consulta a gym-service)
├── k8s/
│   ├── namespace.yaml
│   ├── gym-service/        # Deployment, Service, ServiceMonitor
│   ├── booking-service/    # Deployment, Service, ServiceMonitor
│   ├── prometheus-rules.yaml
│   └── monitoring/         # values.yaml de Helm + dashboard JSON
├── load-testing/
│   └── stress.sh
├── scripts/
│   ├── setup.sh            # Levanta todo desde cero
│   └── teardown.sh         # Limpia todo
└── docs/
    └── informe.md
```

---

## 🛠️ Instalación desde cero

### Prerequisitos

Instala en orden:

**1. Docker Desktop**
```
https://www.docker.com/products/docker-desktop
```

**2. Minikube**
```bash
# Windows (con chocolatey)
choco install minikube

# macOS
brew install minikube

# Linux
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
```

**3. kubectl**
```bash
# Windows
choco install kubernetes-cli

# macOS
brew install kubectl

# Linux
sudo apt-get install -y kubectl
```

**4. Helm**
```bash
# Windows
choco install kubernetes-helm

# macOS
brew install helm

# Linux
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

Verifica instalaciones:
```bash
docker --version
minikube version
kubectl version --client
helm version
```

---

## 🚀 Levantar el sistema completo

```bash
bash scripts/setup.sh
```

El script hace todo automáticamente:
1. Verifica prerequisitos
2. Inicia Minikube con 4 CPUs y 6GB RAM
3. Habilita metrics-server
4. Instala kube-prometheus-stack con Helm
5. Construye las imágenes Docker dentro de Minikube
6. Aplica todos los manifiestos de Kubernetes
7. Muestra las URLs al finalizar

---

## 🌐 URLs del sistema

| Servicio | URL |
|----------|-----|
| gym-service | `http://$(minikube ip):30080` |
| booking-service | `http://$(minikube ip):30081` |
| Grafana | `http://$(minikube ip):32000` — admin / GymApp2024! |
| Prometheus | `http://$(minikube ip):32090` |
| AlertManager | `http://$(minikube ip):32093` |

---

## 📡 Endpoints de la API

### gym-service (puerto 30080)

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/health` | Health check propio |
| GET | `/metrics` | Métricas para Prometheus |
| GET | `/api/memberships` | Lista todas las membresías |
| GET | `/api/memberships/{id}` | Busca membresía por ID |
| POST | `/api/memberships` | Crea nueva membresía |
| PUT | `/api/memberships/{id}` | Actualiza membresía |
| DELETE | `/api/memberships/{id}` | Elimina membresía |
| POST | `/api/memberships/delay` | Configura delay artificial (casuística 2) |

### booking-service (puerto 30081)

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/health` | Health check propio |
| GET | `/metrics` | Métricas para Prometheus |
| GET | `/api/bookings` | Lista todas las reservas |
| GET | `/api/bookings/{id}` | Busca reserva por ID |
| POST | `/api/bookings` | Crea reserva (valida membresía con gym-service) |
| PUT | `/api/bookings/{id}/cancel` | Cancela reserva |
| DELETE | `/api/bookings/{id}` | Elimina reserva |
| GET | `/api/bookings/status/{status}` | Cuenta por estado |

---

## 📊 Métricas expuestas

### gym-service
| Métrica | Tipo | Descripción |
|---------|------|-------------|
| `gym_service_membership_lookups_total` | Counter | Total de consultas al catálogo |
| `gym_service_memberships_created_total` | Counter | Total de membresías creadas |
| `gym_service_active_memberships` | Gauge | Membresías activas en tiempo real |
| `gym_service_artificial_delay_ms` | Gauge | Delay artificial configurado (casuística 2) |
| `gym_service_lookup_duration_seconds` | Timer | Latencia de búsqueda por ID |

### booking-service
| Métrica | Tipo | Descripción |
|---------|------|-------------|
| `booking_service_bookings_created_total` | Counter | Reservas creadas exitosamente |
| `booking_service_bookings_rejected_total` | Counter | Reservas rechazadas |
| `booking_service_gym_service_errors_total` | Counter | Errores de comunicación con gym-service |
| `booking_service_confirmed_bookings` | Gauge | Reservas confirmadas en memoria |
| `booking_service_creation_duration_seconds` | Timer | Latencia de creación de reserva |

---

## ⚡ Casuísticas

### Casuística 1 — Servicio Caído
```bash
kubectl scale deployment/booking-service -n gym-app --replicas=0
# Resolución:
kubectl scale deployment/booking-service -n gym-app --replicas=1
```

### Casuística 2 — Servicio Lento
```bash
# Activar delay de 4s (supera timeout de 3s)
curl -X POST http://$(minikube ip):30080/api/memberships/delay \
  -H "Content-Type: application/json" -d '{"delayMs": 4000}'
# Resolución:
curl -X POST http://$(minikube ip):30080/api/memberships/delay \
  -H "Content-Type: application/json" -d '{"delayMs": 0}'
```

### Casuística 3 — Membresías Inactivas
```bash
# Ver docs/informe.md para el script completo
```

---

## 🧹 Limpiar el sistema

```bash
bash scripts/teardown.sh          # elimina recursos, mantiene cluster
bash scripts/teardown.sh --all    # elimina todo incluyendo Minikube
```

---

## 🔥 Pruebas de carga

```bash
bash load-testing/stress.sh $(minikube ip) 60 2
```
