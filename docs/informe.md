# Informe Técnico — Gym Microservices con Monitoreo en Kubernetes

**Alumno:** [Tatiana Conde]  
**Dominio:** Sistema de Gimnasio — Membresías y Reservas de Clases  
**Fecha:** Junio 2026

---

## Parte A — Conceptos

### 1. ¿Qué es Micrometer y por qué se usa en lugar de la librería directa de Prometheus?

Micrometer es una **fachada de métricas** para aplicaciones JVM, similar a lo que SLF4J es para logging. Actúa como una capa de abstracción entre tu código y el sistema de monitoreo final (Prometheus, Datadog, InfluxDB, etc.).

En lugar de usar la librería directa de Prometheus (`io.prometheus:simpleclient`), usamos Micrometer porque:

- **Independencia del vendor**: si mañana el equipo decide cambiar de Prometheus a Datadog, solo cambia la dependencia del registry, no el código de la aplicación.
- **API más idiomática para Java**: la API de Micrometer está diseñada para Spring y Java, mientras que la librería directa de Prometheus fue diseñada principalmente para Go.
- **Integración nativa con Spring Boot Actuator**: con solo agregar `micrometer-registry-prometheus` al `pom.xml`, Spring Boot expone automáticamente decenas de métricas de la JVM, HTTP, etc.

**Ejemplo de mi código** (`GymMetrics.java`):
```java
// Con Micrometer — limpio y agnóstico al registry
Counter.builder("gym_service_membership_lookups_total")
    .description("Total de consultas al catálogo de membresías")
    .register(registry);  // 'registry' puede ser Prometheus, Datadog, etc.
```

---

### 2. ¿Cuál es la diferencia entre Counter, Gauge y Timer?

| Tipo | Comportamiento | Cuándo usarlo |
|------|---------------|---------------|
| **Counter** | Solo sube, nunca baja. Representa un conteo acumulado. | Eventos que ocurren: requests, errores, transacciones |
| **Gauge** | Puede subir y bajar. Representa un valor puntual en el tiempo. | Estado actual: conexiones activas, tamaño de cola, delay |
| **Timer** | Mide duración y conteo de operaciones. Genera histograma para percentiles. | Latencia de operaciones: llamadas HTTP, queries, etc. |

**Ejemplos de mi código:**

```java
// Counter — en GymMetrics.java
// Solo sube. Cada vez que alguien consulta una membresía, se incrementa.
this.membershipLookups = Counter.builder("gym_service_membership_lookups_total")
    .description("Total de consultas al catálogo de membresías")
    .register(registry);
// Uso: metrics.membershipLookups.increment();

// Gauge — en GymMetrics.java
// Refleja en tiempo real cuántas membresías activas hay en memoria.
Gauge.builder("gym_service_active_memberships", repo, MembershipRepository::countActive)
    .description("Número de membresías activas en memoria")
    .register(registry);
// Se actualiza automáticamente al llamar repo.countActive()

// Timer — en GymMetrics.java
// Mide cuánto tarda buscar una membresía por ID (incluyendo el delay artificial).
this.membershipLookupTimer = Timer.builder("gym_service_lookup_duration_seconds")
    .description("Latencia de búsqueda de membresía por ID")
    .register(registry);
// Uso: metrics.membershipLookupTimer.record(() -> { ... operación ... });
```

---

### 3. ¿Qué es un ServiceMonitor y cómo sabe el Prometheus Operator qué monitorear?

Un **ServiceMonitor** es un Custom Resource Definition (CRD) que instala el Prometheus Operator. Le indica a Prometheus **cómo y de dónde** raspar métricas, sin modificar la configuración de Prometheus directamente.

El flujo es:
1. El **Prometheus Operator** observa continuamente los objetos `ServiceMonitor` en el cluster.
2. Cuando encuentra uno, lee su campo `selector` para identificar qué `Service` de Kubernetes debe monitorear.
3. Genera automáticamente la configuración de scraping de Prometheus apuntando a los pods detrás de ese `Service`.

**Ejemplo de mi `service-monitor.yaml`:**
```yaml
spec:
  selector:
    matchLabels:
      app: gym-service    # debe coincidir EXACTAMENTE con el label del Service
  endpoints:
    - port: http          # nombre del puerto declarado en el Service
      path: /metrics
      interval: 15s
```

El `Service` correspondiente tiene el label `app: gym-service` y el puerto se llama `http`. Si no coinciden exactamente, el target no aparece en Prometheus.

---

### 4. ¿Cuál es la diferencia entre liveness probe y readiness probe?

| Probe | Pregunta que responde | Acción de K8s si falla |
|-------|-----------------------|------------------------|
| **Liveness** | "¿Está vivo el proceso? ¿Sigue funcionando?" | Reinicia el container (puede causar CrashLoopBackOff) |
| **Readiness** | "¿Está listo para recibir tráfico?" | Saca el pod del Service (sin reiniciarlo) |

**En mi configuración** (`deployment.yaml`):
```yaml
livenessProbe:
  httpGet:
    path: /health    # si falla 3 veces seguidas → K8s reinicia el container
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /health    # si falla → K8s deja de mandar tráfico a ese pod
    port: 8080
  initialDelaySeconds: 20
  periodSeconds: 5
```

Un caso real: durante el arranque de Spring Boot (que tarda ~15s), el **readiness probe** falla (el pod no recibe tráfico todavía), pero el **liveness probe** pasa (el proceso JVM está vivo). Una vez que la app levantó, ambos pasan y el pod recibe tráfico.

---

### 5. ¿Por qué es necesario apuntar Docker al daemon de Minikube antes de construir las imágenes?

Cuando corres `docker build` en tu máquina, la imagen queda en el **registry local de tu sistema operativo**. Pero los pods de Kubernetes corren dentro de la VM de Minikube, que tiene **su propio daemon Docker interno**, completamente separado.

Si construyes la imagen en tu Docker local y luego intentas desplegarla en Minikube con `imagePullPolicy: Never`, Kubernetes buscará la imagen en el daemon de Minikube y **no la encontrará**.

La solución es ejecutar antes:
```bash
eval $(minikube docker-env)
```
Esto redirige los comandos `docker` de tu terminal para que apunten al daemon **dentro de Minikube**. Todas las imágenes que construyas después quedarán disponibles para los pods.

---

### 6. ¿Qué ocurre si no configuras el selector de ServiceMonitors en el values.yaml del chart?

Por defecto, el chart `kube-prometheus-stack` configura el Prometheus Operator para que solo detecte ServiceMonitors que tengan el label `release: prometheus` **y** estén en el mismo namespace que Prometheus (`monitoring`).

Si no configuras el `serviceMonitorSelector` correctamente en el `values.yaml`, ocurre lo siguiente:
- Creas tus ServiceMonitors en el namespace `gym-app` → Prometheus los **ignora completamente**.
- Los targets no aparecen en `/targets` de Prometheus.
- No hay métricas de tus servicios aunque estén corriendo perfectamente.

**Mi configuración correcta en `prometheus-values.yaml`:**
```yaml
prometheusSpec:
  serviceMonitorSelectorNilUsesHelmValues: false
  serviceMonitorSelector: {}           # acepta ServiceMonitors con cualquier label
  serviceMonitorNamespaceSelector: {}  # acepta ServiceMonitors de cualquier namespace
```

Con `{}` vacío, el selector coincide con TODO, permitiendo que Prometheus detecte los ServiceMonitors de `gym-app`.

---

## Parte B — PromQL

### 1. Tasa de requests por minuto de booking-service en los últimos 5 minutos

```promql
rate(http_server_requests_seconds_count{job="booking-service"}[5m]) * 60
```

### 2. Latencia p95 del endpoint más crítico (creación de reserva)

```promql
histogram_quantile(0.95,
  rate(booking_service_creation_duration_seconds_bucket[5m])
)
```

### 3. Estado UP/DOWN de ambos servicios al mismo tiempo

```promql
up{job=~"gym-service|booking-service"}
```

Retorna `1` (UP) o `0` (DOWN) para cada servicio en el mismo panel.

### 4. Query de negocio — Tasa de rechazo de reservas (% de reservas rechazadas)

```promql
rate(booking_service_bookings_rejected_total[5m])
/
(rate(booking_service_bookings_created_total[5m]) + rate(booking_service_bookings_rejected_total[5m]))
* 100
```

Responde: "¿Qué porcentaje de los intentos de reserva están siendo rechazados?" Un valor alto indica membresías expiradas masivas o que gym-service está fallando.

---

## Parte C — Evidencias de Casuísticas

### 🔴 Casuística 1 — El Servicio Caído

**Contexto narrativo:**  
Un desarrollador despliega accidentalmente una versión del `booking-service` con una variable de entorno incorrecta que hace que la aplicación Spring Boot no pueda arrancar. El servicio entra en `CrashLoopBackOff`.

**Síntoma:**  
Los usuarios intentan reservar clases y reciben error 503. El equipo de soporte reporta que "la app de reservas no funciona".

**Activación:**
```bash
# Forzar CrashLoopBackOff con una imagen inexistente o comando inválido
kubectl set env deployment/booking-service -n gym-app SPRING_PROFILES_ACTIVE=crash-profile-inexistente
# O alternativamente, escalar a 0 para simular caída total:
kubectl scale deployment/booking-service -n gym-app --replicas=0
```

**Diagnóstico:**  
- En Grafana: el panel "Estado de Servicios" muestra `booking-service = DOWN` (rojo).
- En Prometheus (`/targets`): `booking-service` aparece en estado DOWN.
- La alerta `BookingServiceDown` se dispara después de 1 minuto.
- `kubectl get pods -n gym-app` muestra `CrashLoopBackOff` o `0/1 Running`.

**Resolución:**
```bash
# Restaurar la variable correcta
kubectl set env deployment/booking-service -n gym-app SPRING_PROFILES_ACTIVE-
# O restaurar réplicas
kubectl scale deployment/booking-service -n gym-app --replicas=1
```

**Lección técnica:**  
Los **liveness probes** detectan que el container no puede arrancar y Kubernetes lo reinicia en bucle (CrashLoopBackOff). La diferencia con **readiness**: liveness dice "está vivo el proceso", readiness dice "está listo para tráfico". Durante CrashLoopBackOff, ninguno pasa. Grafana nos permite ver exactamente cuándo cayó y cuándo se recuperó.

---

### 🟡 Casuística 2 — El Servicio Lento

**Contexto narrativo:**  
Una actualización en gym-service introduce una consulta costosa a un sistema externo (simulada aquí con un delay artificial). booking-service tiene un timeout de 3 segundos configurado. Las reservas empiezan a fallar con timeout sin que sea evidente cuál servicio es el culpable.

**Síntoma:**  
Los usuarios reportan que las reservas "a veces funcionan, a veces dan error". El equipo no sabe si el problema es en booking-service o en gym-service.

**Activación:**
```bash
MINIKUBE_IP=$(minikube ip)
# Configurar 4 segundos de delay en gym-service (supera el timeout de 3s de booking-service)
curl -X POST http://${MINIKUBE_IP}:30080/api/memberships/delay \
  -H "Content-Type: application/json" \
  -d '{"delayMs": 4000}'

# Generar tráfico para ver los errores
bash load-testing/stress.sh ${MINIKUBE_IP} 60 2
```

**Diagnóstico:**  
- En Grafana: el panel "Delay Artificial gym-service" sube a 4000ms.
- El panel "Latencia p95 — gym-service lookup" supera los 3s.
- El panel "Reservas Creadas vs Rechazadas" muestra incremento de rechazos.
- La alerta `GymServiceHighLatency` se dispara.
- `booking_service_gym_service_errors_total` aumenta.

**Resolución:**
```bash
# Quitar el delay artificial
curl -X POST http://${MINIKUBE_IP}:30080/api/memberships/delay \
  -H "Content-Type: application/json" \
  -d '{"delayMs": 0}'
```

**Lección técnica:**  
La latencia se propaga entre microservicios. Sin observabilidad, el síntoma (errores en booking-service) apunta en la dirección equivocada. Gracias al Gauge `gym_service_artificial_delay_ms` podemos ver en Grafana que el problema está en gym-service, no en booking-service. Esto demuestra el valor del **distributed tracing** y las métricas por servicio.

---

### 🟠 Casuística 3 — Explosión de Membresías Inactivas

**Contexto narrativo:**  
El equipo de negocio ejecuta un script de mantenimiento que desactiva masivamente membresías expiradas en gym-service. booking-service empieza a rechazar todas las reservas de usuarios afectados porque sus membresías ya no están activas. El contador de rechazos se dispara.

**Síntoma:**  
Usuarios con membresía activa (aparentemente) no pueden reservar clases. El call center se satura de reclamos. Los ingresos del día caen abruptamente.

**Activación:**
```bash
MINIKUBE_IP=$(minikube ip)

# Simular: crear membresías activas y luego desactivarlas masivamente
for i in T001 T002 T003 T004 T005; do
  curl -sf -X POST http://${MINIKUBE_IP}:30080/api/memberships \
    -H "Content-Type: application/json" \
    -d "{\"id\":\"${i}\",\"name\":\"Plan Temp ${i}\",\"type\":\"BASIC\",\"price\":29.99,\"maxClassesPerMonth\":8,\"active\":true}"
done

# Generar reservas con esas membresías
for i in T001 T002 T003 T004 T005; do
  curl -sf -X POST http://${MINIKUBE_IP}:30081/api/bookings \
    -H "Content-Type: application/json" \
    -d "{\"membershipId\":\"${i}\",\"memberName\":\"Usuario ${i}\",\"className\":\"Yoga\"}"
done

# Desactivar todas las membresías temporales (simula el script de mantenimiento)
for i in T001 T002 T003 T004 T005; do
  curl -sf -X PUT http://${MINIKUBE_IP}:30080/api/memberships/${i} \
    -H "Content-Type: application/json" \
    -d "{\"id\":\"${i}\",\"name\":\"Plan Temp ${i}\",\"type\":\"BASIC\",\"price\":29.99,\"maxClassesPerMonth\":8,\"active\":false}"
done

# Intentar reservar con membresías ahora inactivas (dispara rechazos)
bash load-testing/stress.sh ${MINIKUBE_IP} 30 1
```

**Diagnóstico:**  
- En Grafana: el panel "Reservas Creadas vs Rechazadas" muestra pico de rechazos.
- El Gauge `gym_service_active_memberships` cae notablemente.
- La alerta `HighBookingRejectionRate` se dispara.

**Resolución:**
```bash
# Reactivar las membresías afectadas
for i in T001 T002 T003 T004 T005; do
  curl -sf -X PUT http://${MINIKUBE_IP}:30080/api/memberships/${i} \
    -H "Content-Type: application/json" \
    -d "{\"id\":\"${i}\",\"name\":\"Plan Temp ${i}\",\"type\":\"BASIC\",\"price\":29.99,\"maxClassesPerMonth\":8,\"active\":true}"
done
```

**Lección técnica:**  
Las métricas de negocio (tasa de rechazos, membresías activas) son tan importantes como las métricas técnicas (CPU, memoria). Sin el Gauge `gym_service_active_memberships` y el Counter `booking_service_bookings_rejected_total`, este problema habría tardado horas en diagnosticarse. El monitoreo debe cubrir tanto la salud técnica como los KPIs del negocio.

---

## Parte D — Evidencias del sistema

> **Nota:** Reemplaza las secciones marcadas con capturas de pantalla reales después de ejecutar el sistema.

### 1. Prometheus /targets — ambos servicios UP
![Prometheus targets UP](../docs/screenshots/prometheus-targets-up.png)

### 2. Dashboard de Grafana — 6 paneles funcionando
![Grafana dashboard](../docs/screenshots/grafana-dashboard.png)

### 3. kubectl get pods — todos en Running
```
# Comando: kubectl get pods -n gym-app
NAME                               READY   STATUS    RESTARTS   AGE
gym-service-xxx-xxx                1/1     Running   0          5m
booking-service-xxx-xxx            1/1     Running   0          5m
```
![kubectl get pods](../docs/screenshots/kubectl-pods-running.png)

### 4. Alerta en estado FIRING
![Alerta firing](../docs/screenshots/alert-firing.png)
