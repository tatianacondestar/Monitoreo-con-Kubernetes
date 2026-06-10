# 🏋️ Gym Microservices — Monitoreo con Kubernetes
### Taller: Kubernetes con Prometheus & Grafana — Valle Grande

---

## Slide 1 — Contexto del Sistema

**Dominio:** Sistema de Gimnasio

| Servicio | Rol | Puerto |
|----------|-----|--------|
| `gym-service` | Catálogo de membresías (Servicio A) | 8080 |
| `booking-service` | Reservas de clases (Servicio B) | 8081 |

**Regla de negocio:** booking-service consulta a gym-service para validar
si una membresía existe y está activa antes de confirmar una reserva.

---

## Slide 2 — Arquitectura del Sistema

```
[Usuario]
    │
    ▼
[booking-service:8081]
    │  valida membresía via WebClient (timeout 3s)
    ▼
[gym-service:8080]
    │
    ▼
[Datos en memoria (HashMap)]

[Prometheus] ──scraping /metrics──► ambos servicios
[Grafana]    ──dashboards──────────► Prometheus
[AlertManager] ◄──alertas──────────── PrometheusRules
```

---

## Slide 3 — Funcionamiento del Monitoreo

### Stack instalado con Helm (kube-prometheus-stack):
- **Prometheus** — raspa métricas cada 15s desde `/metrics`
- **Grafana** — dashboards con 6 paneles en tiempo real
- **AlertManager** — gestiona y notifica alertas
- **Prometheus Operator** — gestiona ServiceMonitors como CRDs

### Métricas expuestas por cada servicio:
- **Counters:** reservas creadas, rechazadas, errores de comunicación
- **Gauges:** membresías activas, delay artificial en tiempo real
- **Timers:** latencia p50/p95 de operaciones críticas

---

## Slide 4 — Pruebas Realizadas

### Casuística 1 — Servicio Caído 🔴
- **Activación:** `kubectl scale deployment/booking-service -n gym-app --replicas=0`
- **Observado en Grafana:** panel de estado pasa a rojo, alerta `BookingServiceDown` en FIRING
- **Resolución:** `kubectl scale deployment/booking-service -n gym-app --replicas=1`

### Casuística 2 — Servicio Lento 🟡
- **Activación:** configurar delay artificial de 4000ms en gym-service (supera timeout de 3s)
- **Observado:** latencia p95 sube, errores de timeout en booking-service visibles en Grafana
- **Resolución:** setear delay a 0ms

### Casuística 3 — Membresías Inactivas Masivas 🟠
- **Activación:** desactivar múltiples membresías vía API
- **Observado:** tasa de rechazo de reservas sube, alerta `HighBookingRejectionRate` dispara
- **Resolución:** reactivar membresías afectadas

---

## Slide 5 — Problemas Encontrados

| Problema | Causa | Solución |
|----------|-------|----------|
| `/metrics` devolvía 404 | Spring Boot Actuator no mapeaba `/metrics` por defecto | Agregar `path-mapping: prometheus: metrics` en `application.yml` |
| Pods no arrancaban (ImagePullBackOff) | Build de Docker hecho en daemon del host, no de Minikube | Ejecutar `minikube docker-env` antes del `docker build` |
| ServiceMonitor no aparecía en Prometheus | `serviceMonitorSelector` no configurado en `values.yaml` | Agregar `serviceMonitorSelector: {}` en `prometheus-values.yaml` |
| `bash` no reconocido en Windows CMD | WSL no configurado | Usar PowerShell con scripts `.ps1` |

---

## Slide 6 — Recomendaciones

1. **Siempre configurar `serviceMonitorSelector: {}`** en el values.yaml del chart — es el error más común y silencioso.

2. **Usar `imagePullPolicy: Never`** en los Deployments cuando se trabaja con imágenes locales en Minikube.

3. **Separar namespaces:** `gym-app` para la aplicación y `monitoring` para el stack — evita contaminación de recursos.

4. **Definir `resources.limits`** en todos los Deployments — sin límites, un pod mal configurado puede consumir toda la RAM del nodo.

5. **Exponer el delay artificial como Gauge** — en producción, cualquier variable de configuración que afecte la latencia debería ser visible en Grafana en tiempo real.

6. **Documentar las queries PromQL** — las alertas deben tener mensajes claros en las anotaciones para que el equipo on-call sepa qué hacer.

---

## Slide 7 — Conclusiones

- **El monitoreo no es opcional:** sin Prometheus y Grafana, la Casuística 2 (servicio lento) habría tardado horas en diagnosticarse. Con monitoreo, se ve en segundos qué servicio tiene el problema.

- **Los probes de Kubernetes salvan producción:** la diferencia entre liveness y readiness es crítica — un pod puede estar "vivo" pero no "listo" para recibir tráfico.

- **Las métricas de negocio importan tanto como las técnicas:** el contador de reservas rechazadas fue más útil para detectar la Casuística 3 que cualquier métrica de CPU.

- **La observabilidad se diseña, no se agrega después:** instrumentar el código con Micrometer desde el principio (Counters, Gauges, Timers) es lo que hace posible que Grafana muestre información útil.

- **El stack kube-prometheus-stack es el estándar de la industria** para Kubernetes — conocerlo es una habilidad directamente aplicable en entornos productivos.
