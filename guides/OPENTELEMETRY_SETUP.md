# OpenTelemetry Setup for Survey Application

## Summary

OpenTelemetry has been successfully added to the Survey application following Quarkus best practices from https://quarkus.io/guides/opentelemetry.

## What Was Configured

### 1. Dependency Added (pom.xml)
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-opentelemetry</artifactId>
</dependency>
```

### 2. Configuration Added (application.properties)

#### Core OpenTelemetry Settings
- **Service name**: `elicit-survey` (identified in traces)
- **Tracing**: Enabled by default with `parentbased_always_on` sampler
- **Metrics**: Disabled (experimental feature, enable if needed)
- **Logs**: Disabled (experimental feature, enable if needed)

#### OTLP Exporter
- **Protocol**: gRPC (default)
- **Endpoint**: `http://localhost:4317`
- **Export interval**: 5 seconds for spans

#### Automatic Instrumentation
Enabled for:
- ✅ Vert.x HTTP
- ✅ Vert.x Event Bus
- ✅ Vert.x SQL Client
- ✅ Quarkus REST
- ✅ RESTEasy

#### Trace Context in Logs
All console logs now include:
- `traceId`: Unique request identifier
- `parentId`: Parent span identifier
- `spanId`: Current operation identifier
- `sampled`: Whether this trace is sampled

### 3. Documentation Updated

- **METRICS_GUIDE.md**: Enhanced with comprehensive OpenTelemetry sections
  - How traces complement metrics
  - Trace visualization with Jaeger
  - Common trace patterns and solutions
  - Google Cloud Trace integration
  - Local testing instructions

## How It Works

### Automatic Tracing

OpenTelemetry automatically creates spans for:

1. **HTTP Requests**: Every incoming HTTP request gets a root span
2. **Database Queries**: JDBC/Hibernate operations are traced
3. **REST Client Calls**: Outgoing HTTP requests are traced
4. **Custom Methods**: Methods with `@Timed` also appear in traces

### Trace Structure Example

```
HTTP GET /api/survey/init (3400ms)
├── survey.login (50ms)
│   └── SELECT * FROM tokens (45ms)
├── survey.init (2500ms)
│   ├── SELECT * FROM surveys (20ms)
│   ├── SELECT * FROM respondents (15ms)
│   └── buildInitialAnswers (2465ms)
│       ├── SELECT * FROM questions (200ms)
│       └── INSERT INTO answers (2200ms) ← Bottleneck
└── survey.navigate (80ms)
    └── SELECT * FROM questions (75ms)
```

## Using OpenTelemetry for Performance Diagnosis

### Scenario: 10x Slower in Google Cloud

**Problem**: Survey initialization takes 3400ms in Google Cloud vs 340ms locally.

**Diagnosis with OpenTelemetry**:

1. **Collect traces** from both environments
2. **Compare trace waterfalls** side-by-side in Jaeger
3. **Identify the slow span**:
   - Local: `INSERT INTO answers` takes 220ms
   - Cloud: `INSERT INTO answers` takes 2200ms (10x slower!)
4. **Root cause**: Network latency to Cloud SQL database
5. **Solution**: Enable Private IP connection, optimize bulk inserts

### Why This is Better Than Metrics Alone

| Aspect | Metrics (Micrometer) | Traces (OpenTelemetry) |
|--------|---------------------|------------------------|
| **View** | Aggregated (p50, p95, p99) | Individual requests |
| **Use case** | "Is there a problem?" | "Where is the problem?" |
| **Detail** | Total method time | Breakdown of operations |
| **Example** | `survey_init_seconds` = 2.5s | See 15 database queries taking 2.2s |

**Best practice**: Use metrics to detect issues, traces to diagnose them.

## Testing the Setup

### Local Testing with Jaeger

1. **Start Jaeger** (all-in-one):
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

3. **Generate some traffic**:
   ```bash
   curl http://localhost:8080/api/survey/init?respondentId=1&token=TEST
   ```

4. **View traces**: Open http://localhost:16686
   - Service: `elicit-survey`
   - Click "Find Traces"
   - Select a trace to see the waterfall

### Verify Configuration

```bash
# Check that OpenTelemetry is enabled
curl http://localhost:8080/q/health

# Look for these log lines on startup:
# - OpenTelemetry SDK initialized
# - OTLP exporter configured
# - Instrumentation enabled for: ...
```

## Production Deployment

### Google Cloud Run

Update your Cloud Run service to export traces to Google Cloud Trace:

```bash
# Set environment variables
gcloud run services update survey \
  --set-env-vars="OTEL_EXPORTER_OTLP_ENDPOINT=https://cloudtrace.googleapis.com" \
  --set-env-vars="DEPLOYMENT_ENV=production"
```

Or configure in `application.properties`:
```properties
%prod.quarkus.otel.exporter.otlp.endpoint=https://cloudtrace.googleapis.com
%prod.quarkus.otel.exporter.otlp.headers=authorization=Bearer ${GOOGLE_CLOUD_TOKEN}
%prod.quarkus.otel.traces.sampler=traceidratio
%prod.quarkus.otel.traces.sampler.arg=0.1
```

### Grafana Cloud

For Grafana Tempo:
```properties
quarkus.otel.exporter.otlp.endpoint=https://tempo-us-central1.grafana.net:443
quarkus.otel.exporter.otlp.headers=authorization=Basic <base64-encoded-token>
```

## Advanced Configuration

### Enable Experimental Metrics

To use OpenTelemetry metrics (in addition to Micrometer):
```properties
quarkus.otel.metrics.enabled=true
quarkus.otel.metric.export.interval=60s
```

### Enable Logging Integration

To export logs via OpenTelemetry:
```properties
quarkus.otel.logs.enabled=true
```

### Sampling Strategies

**Development** (trace everything):
```properties
quarkus.otel.traces.sampler=always_on
```

**Production** (10% sampling):
```properties
quarkus.otel.traces.sampler=traceidratio
quarkus.otel.traces.sampler.arg=0.1
```

**Custom sampling** (always trace slow requests):
Implement `io.opentelemetry.sdk.trace.samplers.Sampler` as a CDI bean.

### Custom Spans

Add manual instrumentation for specific code blocks:

```java
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import jakarta.inject.Inject;

@ApplicationScoped
public class MyService {
    
    @Inject
    Tracer tracer;
    
    public void complexOperation() {
        Span span = tracer.spanBuilder("complex-operation").startSpan();
        try {
            // Your code here
            span.setAttribute("custom.attribute", "value");
        } finally {
            span.end();
        }
    }
}
```

## Combining with Existing Monitoring

### Micrometer + OpenTelemetry

The application now has **both** monitoring stacks:

| Stack | Purpose | Endpoint |
|-------|---------|----------|
| **Micrometer** | Metrics aggregation, Prometheus format | `/q/metrics` |
| **OpenTelemetry** | Distributed tracing, detailed request flow | OTLP endpoint |

**They complement each other**:
- Use Micrometer for dashboards and alerts (Grafana/Prometheus)
- Use OpenTelemetry for debugging specific slow requests (Jaeger/Tempo)

### @Timed Annotations

The existing `@Timed` annotations work seamlessly with OpenTelemetry:
- Micrometer collects timing metrics
- OpenTelemetry creates spans for the same methods
- Both share the same operation name

Example:
```java
@Timed(value = "survey.init", histogram = true)
public void init(int respondentId, String displaykey) {
    // This method produces:
    // 1. Micrometer metric: survey_init_seconds
    // 2. OpenTelemetry span: survey.init
}
```

## Troubleshooting

### Traces Not Appearing in Jaeger

1. **Check OTLP endpoint connectivity**:
   ```bash
   curl -v http://localhost:4317
   ```

2. **Verify OpenTelemetry is enabled**:
   ```bash
   # Look for this in application logs:
   grep -i "opentelemetry" logs/app.log
   ```

3. **Check Jaeger collector logs**:
   ```bash
   docker logs jaeger
   ```

### High Overhead

If tracing causes performance issues:

1. **Reduce sampling**:
   ```properties
   quarkus.otel.traces.sampler=traceidratio
   quarkus.otel.traces.sampler.arg=0.05  # 5% sampling
   ```

2. **Disable non-critical instrumentation**:
   ```properties
   quarkus.otel.instrument.vertx-event-bus=false
   ```

3. **Increase batch size**:
   ```properties
   quarkus.otel.bsp.max.export.batch.size=1024
   ```

### Missing Database Spans

Ensure JDBC telemetry is enabled:
```properties
quarkus.datasource.jdbc.telemetry=true  # Already configured
```

## Resources

- **Quarkus OpenTelemetry Guide**: https://quarkus.io/guides/opentelemetry
- **Quarkus OpenTelemetry Tracing**: https://quarkus.io/guides/opentelemetry-tracing
- **OpenTelemetry Java**: https://opentelemetry.io/docs/languages/java/
- **Jaeger Documentation**: https://www.jaegertracing.io/docs/
- **OTLP Specification**: https://opentelemetry.io/docs/specs/otlp/

## Next Steps

1. ✅ OpenTelemetry dependency added
2. ✅ Configuration completed
3. ✅ Documentation updated
4. 🔄 Test locally with Jaeger
5. 🔄 Deploy to Google Cloud
6. 🔄 Compare traces between local and cloud
7. 🔄 Identify performance bottleneck
8. 🔄 Implement optimization

See `METRICS_GUIDE.md` for detailed diagnosis instructions.
