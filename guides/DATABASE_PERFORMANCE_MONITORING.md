# Database Performance Monitoring Guide

## Current Metrics Available

Your Survey application already exports database-related metrics. Here's how to visualize slow database problems:

## 1. Prometheus Queries for Database Performance

### Connection Pool Health (Agroal)

```promql
# Average connection acquisition wait time (ms)
agroal_blocking_time_average_milliseconds{datasource="default"}

# Maximum connection acquisition wait time (ms) - spikes indicate problems
agroal_blocking_time_max_milliseconds{datasource="default"}

# Connection pool utilization percentage
(agroal_active_count{datasource="default"} / 
 (agroal_active_count{datasource="default"} + agroal_available_count{datasource="default"})) * 100

# Threads waiting for connections (pool exhaustion indicator)
agroal_awaiting_count{datasource="default"}

# Connection creation time (slow = DB or network issues)
agroal_creation_time_average_milliseconds{datasource="default"}
agroal_creation_time_max_milliseconds{datasource="default"}

# Connection leaks detected
rate(agroal_leak_detection_count_total{datasource="default"}[5m])

# Connection reap rate (idle timeout) - high = possible leak
rate(agroal_reap_count_total{datasource="default"}[5m])
```

### Worker Thread Performance (DB queries run here)

```promql
# Average query execution time (approximate via worker pool)
rate(worker_pool_usage_seconds_sum{pool_name="vert.x-worker-thread"}[5m]) / 
rate(worker_pool_usage_seconds_count{pool_name="vert.x-worker-thread"}[5m])

# Queue wait time before execution
rate(worker_pool_queue_delay_seconds_sum{pool_name="vert.x-worker-thread"}[5m]) / 
rate(worker_pool_queue_delay_seconds_count{pool_name="vert.x-worker-thread"}[5m])

# Queue size (backlog of work)
worker_pool_queue_size{pool_name="vert.x-worker-thread"}

# Thread pool utilization
worker_pool_ratio{pool_name="vert.x-worker-thread"}
```

### HTTP Endpoint Performance (Indirect DB Performance)

```promql
# Slowest endpoints (likely DB-heavy operations)
topk(10, 
  rate(http_server_requests_seconds_sum[5m]) / 
  rate(http_server_requests_seconds_count[5m])
)

# P95 latency for specific endpoint
histogram_quantile(0.95, 
  rate(http_server_requests_seconds_bucket{uri="root"}[5m])
)

# Requests per second by endpoint
rate(http_server_requests_seconds_count[5m])
```

## 2. Grafana Dashboard Panels

### Panel 1: Connection Pool Overview
**Type**: Stat / Gauge
```promql
# Available connections
agroal_available_count{datasource="default"}

# Active connections
agroal_active_count{datasource="default"}

# Threads waiting
agroal_awaiting_count{datasource="default"}
```

### Panel 2: Connection Acquisition Latency
**Type**: Time Series Graph
```promql
agroal_blocking_time_average_milliseconds{datasource="default"}
agroal_blocking_time_max_milliseconds{datasource="default"}
```
**Alert Threshold**: Average > 100ms or Max > 500ms

### Panel 3: Connection Pool Utilization
**Type**: Gauge (0-100%)
```promql
(agroal_max_used_count{datasource="default"} / 
 (agroal_active_count{datasource="default"} + agroal_available_count{datasource="default"})) * 100
```
**Color Thresholds**:
- Green: 0-60%
- Yellow: 60-80%
- Red: 80-100%

### Panel 4: Database Transaction Rate
**Type**: Time Series
```promql
rate(agroal_acquire_count_total{datasource="default"}[1m])
```

### Panel 5: Slow Endpoints (Top 10)
**Type**: Bar Chart
```promql
topk(10, 
  avg by (uri, method) (
    rate(http_server_requests_seconds_sum[5m]) / 
    rate(http_server_requests_seconds_count[5m])
  )
)
```

### Panel 6: Worker Thread Queue Backlog
**Type**: Time Series
```promql
worker_pool_queue_size{pool_name="vert.x-worker-thread"}
```

### Panel 7: Connection Leaks & Issues
**Type**: Time Series
```promql
# Leak detections
rate(agroal_leak_detection_count_total{datasource="default"}[5m])

# Connections reaped (idle timeout)
rate(agroal_reap_count_total{datasource="default"}[5m])

# Invalid connections
rate(agroal_invalid_count_total{datasource="default"}[5m])
```

## 3. Identifying Slow Database Problems

### Symptom: High Connection Acquisition Times
**Metrics to check**:
```promql
agroal_blocking_time_max_milliseconds{datasource="default"} > 200
```
**Possible causes**:
- Pool exhaustion (increase `quarkus.datasource.jdbc.max-size`)
- Long-running queries holding connections
- Connection leaks

### Symptom: Pool Exhaustion
**Metrics to check**:
```promql
agroal_awaiting_count{datasource="default"} > 0
```
**Possible causes**:
- Too many concurrent requests
- Slow queries
- Connection leaks

### Symptom: Slow Query Execution
**Metrics to check**:
```promql
# High worker thread usage time
worker_pool_usage_seconds_max{pool_name="vert.x-worker-thread"} > 1.0

# High endpoint latency
http_server_requests_seconds_max{uri="root"} > 2.0
```
**Action required**:
- Enable SQL logging temporarily
- Check for missing indexes
- Review query patterns

### Symptom: Connection Creation Spikes
**Metrics to check**:
```promql
rate(agroal_creation_count_total{datasource="default"}[1m]) > 0.5
```
**Possible causes**:
- Connections being killed/reaped
- Database restarts
- Network issues

## 4. Alerting Rules

Create these Prometheus alerting rules:

```yaml
groups:
  - name: database_performance
    rules:
      # High connection wait times
      - alert: HighDatabaseConnectionWait
        expr: agroal_blocking_time_average_milliseconds{datasource="default"} > 100
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High database connection acquisition time"
          description: "Average connection wait time is {{ $value }}ms"

      # Pool exhaustion
      - alert: DatabasePoolExhaustion
        expr: agroal_awaiting_count{datasource="default"} > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Database connection pool exhausted"
          description: "{{ $value }} threads waiting for connections"

      # Connection leaks
      - alert: DatabaseConnectionLeak
        expr: rate(agroal_leak_detection_count_total{datasource="default"}[5m]) > 0
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "Potential database connection leak detected"

      # Slow queries (via worker thread)
      - alert: SlowDatabaseQueries
        expr: |
          rate(worker_pool_usage_seconds_sum{pool_name="vert.x-worker-thread"}[5m]) / 
          rate(worker_pool_usage_seconds_count{pool_name="vert.x-worker-thread"}[5m]) > 1.0
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "Slow database queries detected"
          description: "Average query time is {{ $value }}s"
```

## 5. Enhanced Monitoring (Optional)

To get more detailed query-level metrics, you can:

### Option A: Enable SQL Logging Temporarily
Edit `application.properties`:
```properties
quarkus.hibernate-orm.log.sql=true
quarkus.hibernate-orm.log.bind-parameters=true
quarkus.log.category."org.hibernate.SQL".level=DEBUG
quarkus.log.category."org.hibernate.type.descriptor.sql.BasicBinder".level=TRACE
```

### Option B: Add Hibernate Metrics (if not already exposed)
The metrics should include:
- `hibernate_sessions_open_total`
- `hibernate_transactions_total`
- `hibernate_query_executions_max_seconds`
- `hibernate_cache_*`

Check your `/q/metrics` endpoint for these.

### Option C: Use OpenTelemetry Traces
Your JDBC telemetry is already enabled. View database spans in your trace viewer (Jaeger/Tempo):
```properties
quarkus.datasource.jdbc.telemetry=true  # Already enabled
quarkus.otel.instrument.vertx-sql-client=true  # Already enabled
```

## 6. Quick Diagnostic Commands

### Check current metrics snapshot
```bash
curl http://localhost:8080/q/metrics | grep -E '(agroal|worker_pool|http_server_requests)'
```

### Check health endpoint
```bash
curl http://localhost:8080/q/health
```

### View OpenTelemetry traces
If using Jaeger at http://localhost:16686, search for:
- Service: `elicit-survey`
- Operation: `SELECT`, `INSERT`, `UPDATE`
- Min Duration: `> 100ms`

## 7. Example Analysis from Your Metrics

From your provided metrics:

```
✅ Connection Pool Status: HEALTHY
- Owner datasource: 1 available, 0 active
- Default datasource: 5 available, 0 active

⚠️ Historical Connection Issues Detected:
- Owner datasource: avg wait 176ms, max wait 388ms
- Default datasource: avg wait 0ms, max wait 302ms

✅ Worker Thread Pool: HEALTHY
- 79,150 tasks completed
- Average execution: ~3.77ms
- Queue delays: minimal

⚠️ Slow Endpoint Detected:
- POST /root: max 1.45s, count: 6
  This endpoint is likely database-heavy

📊 System Stats:
- Uptime: 38.6 hours
- GC pauses: 755 minor, 1 major
- HTTP requests: ~44k
- Bytes read: 1.1 MB
- Bytes written: 14.2 MB
```

**Recommendation**: The `owner` datasource shows historical connection acquisition delays (176ms average). Consider:
1. Increasing pool size for owner datasource
2. Investigating slow transactions
3. Checking for connection leaks (237 reaped connections suggests possible issues)

## 8. Connection Pool Tuning

Current configuration in `application.properties`:
```properties
quarkus.datasource.jdbc.min-size=5
# Add max-size if not present:
quarkus.datasource.jdbc.max-size=20

# For owner datasource
quarkus.datasource.owner.jdbc.min-size=2
quarkus.datasource.owner.jdbc.max-size=10
```

Monitor and adjust based on:
- Concurrent user load
- Query complexity
- Transaction duration
