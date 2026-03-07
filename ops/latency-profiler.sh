#!/bin/bash
# Maollar Latency Profiler (P4 Phase)
# A lightweight benchmarking tool using curl to measure P95/Avg latency and error rate.
# Usage: ./latency-profiler.sh <URL> <TOTAL_REQUESTS> <CONCURRENCY (not supported in pure bash, sequential for now)>

set -euo pipefail

URL=${1:-"http://127.0.0.1:8888/actuator/health"}
TOTAL_REQUESTS=${2:-10}
TEMP_FILE="latency_results.txt"

echo "🚀 [PROFILER] Benchmarking: $URL ($TOTAL_REQUESTS requests)"
echo "" > "$TEMP_FILE"

PROGRESS=0
ERRORS=0

for ((i=1; i<=$TOTAL_REQUESTS; i++)); do
    # Measure time_total (seconds)
    TIME=$(curl -s -o /dev/null -w "%{time_total}" "$URL")
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$URL")
    
    if [[ "$HTTP_CODE" -lt 200 || "$HTTP_CODE" -ge 400 ]]; then
        ERRORS=$((ERRORS + 1))
    fi
    
    echo "$TIME" >> "$TEMP_FILE"
    
    # Progress bar
    PERCENT=$((i * 100 / TOTAL_REQUESTS))
    if [[ "$PERCENT" -ne "$PROGRESS" ]]; then
        printf "\rProgress: [%-20s] %d%%" $(printf "#%.0s" $(seq 1 $((PERCENT / 5)))) "$PERCENT"
        PROGRESS=$PERCENT
    fi
done

echo -e "\n\n📊 [RESULTS]"
SORTED_LATENCIES=$(sort -n "$TEMP_FILE")
AVG=$(awk '{ sum += $1; n++ } END { if (n > 0) print sum / n; else print 0 }' "$TEMP_FILE")
MIN=$(head -n 1 <<< "$SORTED_LATENCIES")
MAX=$(tail -n 1 <<< "$SORTED_LATENCIES")
P95_LINE=$((TOTAL_REQUESTS * 95 / 100))
P95=$(sed -n "${P95_LINE}p" <<< "$SORTED_LATENCIES")

# Throughput calculation (roughly)
TOTAL_TIME=$(awk '{ sum += $1 } END { print sum }' "$TEMP_FILE")
TPS=$(awk "BEGIN { print $TOTAL_REQUESTS / $TOTAL_TIME }")

echo "-----------------------------------"
echo "Avg Latency:    ${AVG}s"
echo "Min Latency:    ${MIN}s"
echo "Max Latency:    ${MAX}s"
echo "P95 Latency:    ${P95}s"
echo "Throughput:     ${TPS} req/sec (sequential)"
echo "Error Rate:     $((ERRORS * 100 / TOTAL_REQUESTS))% ($ERRORS errors)"
echo "-----------------------------------"

rm "$TEMP_FILE"
