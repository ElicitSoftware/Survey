# Performance Monitoring Guide for Survey Service

## Overview

This guide explains how to use Micrometer metrics and OpenTelemetry tracing to diagnose the 10x performance degradation when running in Google Cloud (3400ms vs 340ms in local Docker).

**Observability Stack:**
- **Micrometer with Prometheus**: For metrics collection and custom @Timed annotations
- **OpenTelemetry**: For distributed tracing, spans, and request flow visualization
- **SmallRye Health**: For health checks and readiness probes

## Accessing Observability Endpoints

### Prometheus Metrics Endpoint
```bash
curl http://your-service-url/q/metrics
```

### Health Check Endpoint
```bash
curl http://your-service-url/q/health
```

### OpenTelemetry Traces
Traces are exported to an OTLP collector at `http://localhost:4317` (configurable in `application.properties`).
To visualize traces, use tools like:
- **Jaeger**: Visual trace timeline and service dependency graphs
- **Grafana Tempo**: Trace storage and querying
- **Zipkin**: Distributed tracing UI

## What is OpenTelemetry?

OpenTelemetry provides automatic instrumentation for:
- **HTTP requests**: Automatic spans for all incoming/outgoing HTTP calls
- **Database queries**: Spans for JDBC/Hibernate operations
- **REST endpoints**: Automatic tracing of REST API calls
- **Vert.x operations**: HTTP, Event Bus, SQL client instrumentation

**Key benefits for diagnosing the cloud performance issue:**
1. **Request flow visualization**: See exactly where time is spent in each request
2. **Service dependencies**: Identify slow external service calls
3. **Database query breakdown**: See individual query timings within a trace
4. **Context propagation**: Trace IDs in logs for correlation

### Example Trace Structure
```
survey-request (3400ms)                    <- Total request time
├── login (50ms)                           <- TokenService.login()
├── init (2500ms)                          <- QuestionService.init() ← BOTTLENECK
│   ├── database-query: findSurvey (20ms)
│   ├── database-query: findRespondent (15ms)
│   └── buildInitialAnswers (2465ms)      <- Likely culprit
│       ├── database-query: findQuestions (200ms)
│       ├── database-query: insertAnswers (2200ms) ← N+1 query?
│       └── database-query: updateState (65ms)
└── navigate (80ms)                        <- QuestionManager.navigate()
```

## Accessing Metrics

### Prometheus Metrics Endpoint
```bash
curl http://your-service-url/q/metrics
```

### Health Check Endpoint
```bash
curl http://your-service-url/q/health
```

## Key Custom Metrics

The following custom timing metrics have been added to critical service methods:

### 1. Survey Initialization
**Metric:** `survey_init_seconds`
- **Location:** `QuestionService.init()`
- **What it measures:** Time to initialize survey for a respondent, including generating initial answers
- **Why it matters:** This is called when a user first accesses a survey

**Prometheus Query:**
```promql
# Average time
rate(survey_init_seconds_sum[5m]) / rate(survey_init_seconds_count[5m])

# 95th percentile
histogram_quantile(0.95, rate(survey_init_seconds_bucket[5m]))
```

### 2. Survey Navigation
**Metric:** `survey_navigate_seconds`
- **Location:** `QuestionManager.navigate()`
- **What it measures:** Time to navigate to a survey section and load questions
- **Why it matters:** This is called on every page navigation during survey completion

**Prometheus Query:**
```promql
# 95th percentile navigation time
histogram_quantile(0.95, rate(survey_navigate_seconds_bucket[5m]))

# Compare local vs cloud
histogram_quantile(0.95, rate(survey_navigate_seconds_bucket{environment="local"}[5m]))
histogram_quantile(0.95, rate(survey_navigate_seconds_bucket{environment="cloud"}[5m]))
```

### 3. Respondent Login
**Metric:** `survey_login_seconds`
- **Location:** `TokenService.login()`
- **What it measures:** Time to authenticate and login a respondent
- **Why it matters:** First operation when user accesses survey with token

**Prometheus Query:**
```promql
# Average login time
rate(survey_login_seconds_sum[5m]) / rate(survey_login_seconds_count[5m])
```

### 4. Answer Saving
**Metric:** `survey_save_answer_seconds`
- **Location:** `QuestionService.saveAnswer()`
- **What it measures:** Time to save an answer and process downstream dependencies
- **Why it matters:** Called on every answer submission; includes complex dependency resolution

**Prometheus Query:**
```promql
# Maximum save time in last hour
max_over_time(survey_save_answer_seconds_max[1h])
```

### 5. Review Generation
**Metric:** `survey_review_seconds`
- **Location:** `QuestionService.review()`
- **What it measures:** Time to generate the review page with all answers
- **Why it matters:** Complex query joining multiple tables

### 6. Post-Survey Actions
**Metric:** `survey_post_actions_seconds`
- **Location:** `QuestionService.PostSurveyActions()`
- **What it measures:** Time to execute external HTTP calls after survey completion
- **Why it matters:** Network latency to external services

## Built-in Metrics to Monitor

### HTTP Request Duration
```promql
# 95th percentile request duration by endpoint
histogram_quantile(0.95, 
  rate(http_server_requests_seconds_bucket[5m])
) by (uri)

# Slowest endpoints
topk(5, 
  rate(http_server_requests_seconds_sum[5m]) / 
  rate(http_server_requests_seconds_count[5m])
) by (uri)
```

### Database Query Performance
```promql
# Hibernate query time (95th percentile)
histogram_quantile(0.95, rate(hibernate_query_seconds_bucket[5m]))

# Database connection pool usage
agroal_active_count / agroal_max_used

# Connection wait time
rate(agroal_blocking_time_average_ms[5m])
```

### JVM Metrics
```promql
# Garbage collection time
rate(jvm_gc_pause_seconds_sum[5m])

# Heap memory usage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}
```

### Connection Pool Health
```promql
# Available connections
agroal_available_count

# Connection acquisition time
rate(agroal_creation_time_average_ms[5m])
```

## Diagnosing the Cloud Performance Issue

### Step 1: Compare Overall HTTP Response Times
```promql
# Local environment
histogram_quantile(0.95, 
  rate(http_server_requests_seconds_bucket{environment="local"}[5m])
)

# Cloud environment
histogram_quantile(0.95, 
  rate(http_server_requests_seconds_bucket{environment="cloud"}[5m])
)
```

### Step 2: Identify Which Operation is Slow
Compare each custom metric between environments:
```promql
# Navigation time comparison
histogram_quantile(0.95, rate(survey_navigate_seconds_bucket[5m])) by (environment)

# Login time comparison
histogram_quantile(0.95, rate(survey_login_seconds_bucket[5m])) by (environment)

# Answer save time comparison
histogram_quantile(0.95, rate(survey_save_answer_seconds_bucket[5m])) by (environment)
```

### Step 3: Check Database Connection Latency
```promql
# Database connection time
rate(agroal_creation_time_average_ms[5m])

# Query execution time
histogram_quantile(0.95, rate(hibernate_query_seconds_bucket[5m]))
```

### Step 4: Analyze External Service Calls
```promql
# Post-survey action time (includes external HTTP calls)
histogram_quantile(0.95, rate(survey_post_actions_seconds_bucket[5m]))

# HTTP client metrics (if available)
http_client_requests_seconds_sum / http_client_requests_seconds_count
```

## Using OpenTelemetry Traces

### Trace Configuration

OpenTelemetry is configured in `application.properties` with:
- **Service name**: `elicit-survey` (identifies this service in traces)
- **OTLP endpoint**: `http://localhost:4317` (where traces are exported)
- **Automatic instrumentation**: Enabled for HTTP, REST, Vert.x, and SQL

### Understanding Spans

Each traced operation creates a **span** with:
- **Trace ID**: Unique identifier for the entire request flow
- **Span ID**: Unique identifier for this specific operation
- **Parent Span ID**: Links child operations to parent
- **Duration**: How long the operation took
- **Attributes**: Metadata like HTTP method, status code, SQL query

### Finding Trace IDs in Logs

With OpenTelemetry enabled, all log messages include trace context:
```
14:23:45 INFO  traceId=a1b2c3d4e5f6, parentId=123abc, spanId=456def, sampled=true [com.elicitsoftware.QuestionService] (executor-thread-1) Initializing survey for respondent 42
```

Use the `traceId` to find the complete request trace in Jaeger/Tempo.

### Visualizing Traces with Jaeger

1. **Start Jaeger locally**:
   ```bash
   docker run -d --name jaeger \
     -p 16686:16686 \
     -p 4317:4317 \
     jaegertracing/all-in-one:latest
   ```

2. **Access Jaeger UI**: http://localhost:16686

3. **Search for traces**:
   - Service: `elicit-survey`
   - Operation: HTTP endpoints like `/login`, `/api/survey/init`
   - Duration: Filter for slow requests (>1000ms)

4. **Analyze the waterfall**:
   - See which operations take the most time
   - Identify sequential vs parallel operations
   - Find N+1 query patterns (many small database spans)

### Common Trace Patterns and Solutions

#### Pattern 1: Sequential Database Queries (N+1 Problem)
```
survey-init (2500ms)
  ├── SELECT question WHERE survey_id=1 (20ms)
  ├── INSERT answer (survey_id=1, question_id=1) (200ms)
  ├── INSERT answer (survey_id=1, question_id=2) (200ms)
  ├── INSERT answer (survey_id=1, question_id=3) (200ms)
  ... (10 more queries)
```
**Solution**: Use batch inserts or Hibernate bulk operations

#### Pattern 2: Long Network Latency to Database
```
survey-init (2500ms)
  ├── SELECT questions (2000ms) ← High latency
  └── process-results (500ms)
```
**Solution**: 
- Use Private IP for Cloud SQL connection
- Move app closer to database (same region/zone)
- Enable connection pooling

#### Pattern 3: Slow External Service Call
```
survey-post-actions (3000ms)
  ├── HTTP POST to webhook (2800ms) ← Timeout?
  └── database-update (200ms)
```
**Solution**:
- Implement async processing with message queue
- Add circuit breaker pattern
- Reduce timeout values

#### Pattern 4: Resource Contention
```
survey-request (5000ms)
  ├── wait-for-connection (4000ms) ← Connection pool exhausted
  └── execute-query (1000ms)
```
**Solution**:
- Increase connection pool size
- Optimize connection lifecycle
- Add connection timeout alerts

### Correlating Traces with Metrics

Combine OpenTelemetry traces with Micrometer metrics:

1. **Use traces to identify slow requests**:
   - Filter Jaeger for traces > 1000ms
   - Find the specific operation causing delay

2. **Use metrics to confirm the pattern**:
   - Check if `survey_init_seconds` p95 is consistently high
   - Verify with `histogram_quantile(0.95, rate(survey_init_seconds_bucket[5m]))`

3. **Deep dive with traces**:
   - Select a specific slow trace
   - Examine the database query details
   - Look for spans with high self-time (excluding children)

### Exporting Traces to Google Cloud Trace

For production deployment in Google Cloud, configure OTLP to export to Cloud Trace:

```properties
# application.properties for Google Cloud
quarkus.otel.exporter.otlp.endpoint=https://cloudtrace.googleapis.com
quarkus.otel.exporter.otlp.headers=authorization=Bearer ${GOOGLE_CLOUD_TOKEN}
```

Then view traces in [Google Cloud Console → Trace](https://console.cloud.google.com/traces).

### OpenTelemetry Sampling

To reduce overhead in production:
```properties
# Sample 10% of requests
quarkus.otel.traces.sampler=traceidratio
quarkus.otel.traces.sampler.arg=0.1
```

For debugging, use:
```properties
# Sample 100% of requests
quarkus.otel.traces.sampler=always_on
```

## Common Cloud Performance Issues and Solutions

### 1. Database Connection Latency
**Symptoms:**
- High `agroal_creation_time_average_ms`
- High `agroal_blocking_time_average_ms`
- Slow `survey_navigate_seconds` and `survey_init_seconds`

**Solutions:**
- Ensure Cloud SQL instance is in the same region as application
- Use Private IP for database connection (not public IP)
- Increase connection pool size in `application.properties`:
  ```properties
  quarkus.datasource.jdbc.min-size=10
  quarkus.datasource.jdbc.max-size=20
  ```

### 2. Cold Start Issues (Cloud Run)
**Symptoms:**
- First request after idle period is 10x slower
- High variance in `http_server_requests_seconds`

**Solutions:**
- Increase minimum instances: `gcloud run services update survey --min-instances=1`
- Enable CPU always allocated: `--cpu-always-allocated`

### 3. Network Egress to External Services
**Symptoms:**
- High `survey_post_actions_seconds`
- Normal internal operation times

**Solutions:**
- Use VPC peering for service-to-service communication
- Implement HTTP connection pooling
- Add circuit breakers for external calls

### 4. Resource Constraints
**Symptoms:**
- High `jvm_gc_pause_seconds_sum`
- Memory pressure in `jvm_memory_used_bytes`

**Solutions:**
- Increase memory allocation in Cloud Run service
- Optimize JVM heap settings:
  ```bash
  -Xmx1024m -XX:MaxMetaspaceSize=256m
  ```

### 5. Slow Queries Due to Missing Indexes
**Symptoms:**
- High `hibernate_query_seconds` for specific queries
- Normal connection times but slow `survey_navigate_seconds`

**Solutions:**
- Enable SQL logging temporarily: `quarkus.hibernate-orm.log.sql=true`
- Analyze slow queries with PostgreSQL `pg_stat_statements`
- Add indexes on frequently queried columns

## Grafana Dashboard Setup

Create a Grafana dashboard with these panels:

### Panel 1: Request Duration Over Time
```promql
histogram_quantile(0.95, 
  sum(rate(http_server_requests_seconds_bucket[5m])) by (uri, le)
)
```

### Panel 2: Service Method Performance
```promql
# Compare all timed methods
rate(survey_init_seconds_sum[5m]) / rate(survey_init_seconds_count[5m])
rate(survey_navigate_seconds_sum[5m]) / rate(survey_navigate_seconds_count[5m])
rate(survey_login_seconds_sum[5m]) / rate(survey_login_seconds_count[5m])
rate(survey_save_answer_seconds_sum[5m]) / rate(survey_save_answer_seconds_count[5m])
```

### Panel 3: Database Performance
```promql
# Query duration
histogram_quantile(0.95, rate(hibernate_query_seconds_bucket[5m]))

# Connection pool
agroal_active_count
agroal_available_count
```

### Panel 4: JVM Health
```promql
# GC time
rate(jvm_gc_pause_seconds_sum[5m])

# Memory usage percentage
100 * (jvm_memory_used_bytes / jvm_memory_max_bytes)
```

## Next Steps

1. **Deploy to Cloud with observability enabled** - Ensure `/q/metrics` endpoint is accessible
2. **Start OpenTelemetry collector** - Use Jaeger or configure Cloud Trace endpoint
3. **Generate test load** - Run a survey completion workflow multiple times
4. **Analyze traces** - Look for spans with high duration in Jaeger UI
5. **Compare metrics** - Compare cloud metrics with local Docker metrics
6. **Identify bottleneck** - Use both traces (detailed view) and metrics (aggregated view)
7. **Apply targeted fix** - Use the solutions above based on identified bottleneck

## Testing the OpenTelemetry Setup

### Local Testing with Jaeger

1. **Start Jaeger**:
   ```bash
   docker run -d --name jaeger \
     -e COLLECTOR_OTLP_ENABLED=true \
     -p 16686:16686 \
     -p 4317:4317 \
     -p 4318:4318 \
     jaegertracing/all-in-one:latest
   ```

2. **Build and run the application**:
   ```bash
   ./buildDockerImage.sh
   docker run -p 8080:8080 elicitsoftware/survey:latest
   ```

3. **Generate traffic**:
   ```bash
   # Login request
   curl http://localhost:8080/login?token=TEST_TOKEN
   
   # Navigate survey
   curl http://localhost:8080/api/survey/navigate?respondentId=1
   ```

4. **View traces in Jaeger**: http://localhost:16686
   - Select service: `elicit-survey`
   - Click "Find Traces"
   - Examine the trace waterfall

### Verify Metrics are Collected

```bash
# Check Micrometer metrics
curl http://localhost:8080/q/metrics | grep survey_

# Expected output:
# survey_init_seconds_count 5.0
# survey_init_seconds_sum 1.234
# survey_navigate_seconds_count 10.0
# ...

# Check OpenTelemetry configuration
curl http://localhost:8080/q/health
```

### Dev Mode Testing

For rapid testing during development:
```bash
mvn quarkus:dev
```

Then access:
- Application: http://localhost:8080
- Metrics: http://localhost:8080/q/metrics
- Health: http://localhost:8080/q/health

OpenTelemetry traces will be exported to `http://localhost:4317` automatically.

## Next Steps

1. **Deploy to Cloud with metrics enabled** - Ensure `/q/metrics` endpoint is accessible
2. **Generate test load** - Run a survey completion workflow multiple times
3. **Compare metrics** - Compare cloud metrics with local Docker metrics
4. **Identify bottleneck** - Look for the metric with the largest difference
5. **Apply targeted fix** - Use the solutions above based on identified bottleneck

## Example: Finding the Bottleneck

If you find:
- `survey_login_seconds`: 50ms (both environments) ✓
- `survey_init_seconds`: 100ms local, 2500ms cloud ❌ **BOTTLENECK**
- `survey_navigate_seconds`: 80ms local, 150ms cloud (acceptable)
- `survey_save_answer_seconds`: 60ms (both environments) ✓

Then the issue is in survey initialization, likely:
1. Database connection latency (check `agroal_creation_time_average_ms`)
2. Multiple N+1 query issues (check `hibernate_query_seconds_count`)
3. Network round trips in `QuestionManager.init()` method

Focus optimization efforts on the `QuestionManager.init()` and `buildInitialAnswers()` methods.

## Support

For questions about observability and performance tuning:

### Documentation
1. **Quarkus OpenTelemetry**: https://quarkus.io/guides/opentelemetry
2. **Quarkus Micrometer**: https://quarkus.io/guides/micrometer
3. **OpenTelemetry Documentation**: https://opentelemetry.io/docs/
4. **Prometheus Query Language**: https://prometheus.io/docs/prometheus/latest/querying/basics/
5. **Jaeger Documentation**: https://www.jaegertracing.io/docs/

### Cloud-Specific Guides
1. **Google Cloud Run Performance**: https://cloud.google.com/run/docs/tips/general
2. **Google Cloud Trace**: https://cloud.google.com/trace/docs
3. **Cloud SQL Performance**: https://cloud.google.com/sql/docs/postgres/best-practices

### Troubleshooting
- Check application logs for trace IDs
- Verify OTLP endpoint connectivity: `curl http://localhost:4317`
- Ensure Jaeger is receiving spans: Check collector logs
- Validate metrics endpoint: `curl http://localhost:8080/q/metrics | head`
