#!/usr/bin/env bash
# =============================================================================
# stress.sh — Genera tráfico hacia los microservicios del gimnasio
# Uso:
#   bash load-testing/stress.sh <MINIKUBE_IP> [duracion_segundos] [concurrencia]
#
# Ejemplos:
#   bash load-testing/stress.sh 192.168.49.2           # 60s, 1 hilo
#   bash load-testing/stress.sh 192.168.49.2 120 3     # 120s, 3 hilos
# =============================================================================

MINIKUBE_IP=${1:-$(minikube ip)}
DURATION=${2:-60}
CONCURRENCY=${3:-2}

GYM_URL="http://${MINIKUBE_IP}:30080"
BOOKING_URL="http://${MINIKUBE_IP}:30081"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info() { echo -e "${GREEN}[STRESS]${NC} $*"; }

info "Iniciando stress test → gym: ${GYM_URL} | booking: ${BOOKING_URL}"
info "Duración: ${DURATION}s | Concurrencia: ${CONCURRENCY} hilos"
echo ""

END_TIME=$((SECONDS + DURATION))

# Función de carga para un hilo
run_load() {
    local thread_id=$1
    while [ $SECONDS -lt $END_TIME ]; do
        # Listar membresías
        curl -sf "${GYM_URL}/api/memberships" > /dev/null

        # Obtener membresía por ID
        curl -sf "${GYM_URL}/api/memberships/M001" > /dev/null
        curl -sf "${GYM_URL}/api/memberships/M002" > /dev/null

        # Listar reservas
        curl -sf "${BOOKING_URL}/api/bookings" > /dev/null

        # Crear reserva válida
        curl -sf -X POST "${BOOKING_URL}/api/bookings" \
            -H "Content-Type: application/json" \
            -d "{\"membershipId\":\"M00${thread_id}\",\"memberName\":\"Usuario ${thread_id}\",\"className\":\"Yoga\"}" \
            > /dev/null

        # Crear reserva con membresía inválida (para disparar rechazos)
        curl -sf -X POST "${BOOKING_URL}/api/bookings" \
            -H "Content-Type: application/json" \
            -d '{"membershipId":"INVALID","memberName":"Test","className":"Spinning"}' \
            > /dev/null

        sleep 0.5
    done
    info "Hilo ${thread_id} terminó"
}

# Lanzar hilos concurrentes
for i in $(seq 1 $CONCURRENCY); do
    run_load $i &
done

# Mostrar progreso
while [ $SECONDS -lt $END_TIME ]; do
    remaining=$((END_TIME - SECONDS))
    echo -ne "\r  ⏱️  Tiempo restante: ${remaining}s  "
    sleep 5
done

wait
echo ""
info "Stress test completado ✓"
