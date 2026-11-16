# UC-016: Authenticate a Platform Service Call

## Overview

- **ID:** UC-016
- **Name:** Authenticate a Platform Service Call
- **Primary Actor:** Platform Service (the Admin module, the Authoring module)
- **Secondary Actors:** System Operator, External Report Service, External Post-Survey-Action Service
- **Goal:** Let the modules of one deployment call each other's REST paths, and nobody else: a call to Survey's paths under `/api/` is served only when it carries the deployment's service token, and Survey sends that token only to the services the site's own configuration names.
- **Status:** Implemented

## Preconditions

- The site has generated one service token for the deployment and supplied it to every module as `ELICIT_SERVICE_TOKEN` (`elicit.service.token`).
- Where Survey calls a platform service at an address a survey definition carries (a report service, a post-survey action), the site has listed that service's origin in `ELICIT_SERVICE_TRUSTED_ORIGINS` (`elicit.service.trusted-origins`).

## Main Success Scenario

1. A platform service sends a request to a path under `/api/` with `Authorization: Bearer <token>`.
2. The system finds that the path is not the respondent's PDF download (BR-001).
3. The system compares the presented token with the configured one (BR-004).
4. The tokens are equal, and the request proceeds to the resource it names (UC-008, UC-010, UC-011, UC-014).

## Alternative Flows

**A1: No token, or a different one**
- **Trigger:** The `Authorization` header is missing, is not a `Bearer` credential, or carries another value in step 3.
- The system answers `401` with `WWW-Authenticate: Bearer` and an empty body, and does nothing else. A missing token and a wrong one get the same answer.

**A2: Survey has no token configured**
- **Trigger:** `elicit.service.token` is unset or blank.
- At startup the system logs one WARN naming `ELICIT_SERVICE_TOKEN`, and from then on reports not ready through the readiness check `service-token`, whose reason is "ELICIT_SERVICE_TOKEN is not set".
- A request to a protected path is answered `503` with `{"error":"service token not configured"}`, whatever it carries.
- A call Survey would have sent the token with is not made (BR-007).

**A3: The configured token is too short**
- **Trigger:** `elicit.service.token` is set and shorter than 32 characters.
- The application does not start. The message names the variable and the length required, and neither the value nor its length.

**A4: A trusted origin is not an origin**
- **Trigger:** An entry of `elicit.service.trusted-origins` has no `http` or `https` scheme or no host, or carries user information, a path, a query or a fragment.
- The application does not start, and the message names the entry.

**A5: The token is the development one**
- **Trigger:** `elicit.service.token` equals the well-known value the development stack and the `dev` profile use.
- The system starts, and logs one WARN saying that a real site must replace it.

**A6: Survey calls a service**
- **Trigger:** The report page calls an external report service (UC-005), or a post-survey action is delivered (UC-015).
- The system reduces the address it is about to call, after its placeholders are filled, to an origin and sends the token only when that origin is trusted (BR-007).

## Postconditions

**Success:**
- The request was served to a caller holding the token; nothing about the token was logged.

**Failure:**
- The request was refused before its body was read and before any resource ran; nothing was changed.

## Business Rules

- **BR-001: Protected unless listed.** Every REST path under `/api/` requires the platform service token, except `/api/pdf/download`, which a respondent's browser calls and which is keyed by its own link (UC-005 BR-013). The exception is a list of one; a path added under `/api/` is protected unless it is added to that list.
- **BR-002: The token is the site's.** It is configuration, `elicit.service.token` (environment `ELICIT_SERVICE_TOKEN`), sent as `Authorization: Bearer <token>` and in no other way. Survey never generates, stores or rotates it. No value ships with the application; only the `dev` and `test` profiles carry one.
- **BR-003: Closed when unset.** With no token configured, a protected path answers `503` and does nothing, Survey reports not ready, and Survey makes no call that would carry the token. With a token configured, a request with no token or a different one answers `401`. Neither answer says anything about the resource asked for.
- **BR-004: Strength and comparison.** A configured token shorter than 32 characters stops the application at startup. Tokens are compared in constant time over their SHA-256 digests, so neither the position of the first difference nor the length shows in the time taken.
- **BR-005: Never shown.** The token and the header a caller presented are never written to a log, a span, a response or the diagnostics report, which says only whether a token is set (UC-007 BR-007). Survey configures no capture of request headers in its telemetry and no REST client logging.
- **BR-006: Authentication is not authorization.** A caller holding the token may ask for any respondent and any survey. Which respondent a person may see is decided by the module that person signed in to, not by Survey.
- **BR-007: Outbound, only to a trusted origin.** Survey sends the token only when the address it is about to call, after any placeholder is filled, has no user information, an `http` or `https` scheme and a host, and its origin (scheme and host in lower case, and the port, 80 or 443 when the address names none) equals an entry of `elicit.service.trusted-origins`. Nothing but equality counts: no wildcard, no prefix or suffix, no name resolution. A call that carries the token never follows a redirect. A call to a trusted origin is not made while Survey has no token; it fails with `service token not configured` where that call's failure is already reported.
- **BR-008: What needs no token.** The respondent's pages, `/login/<access code>`, brand files, the PDF download and the health endpoints are served without it, and the diagnostic probes (UC-007) never send it.

## Notes / Known Gaps

- One token is shared by every module of a deployment, so a holder can call every receiver, and rotation means restarting all of them together.

## Reference

Traces to NFR-019 and NFR-020. Implemented by `security/ServiceToken` (the token, the trusted origins and the startup checks), `security/ServiceTokenFilter` (the receiver), `security/ServiceTokenHealthCheck` (readiness), and the callers `ReportView` and `PostSurveyActionDelivery`. Verified by `ServiceTokenTest`, `ServiceTokenFilterTest`, `ServiceTokenFilterUnitTest`, `ServiceTokenHealthCheckTest`, `ServiceTokenConfigurationTest`, `ReportServiceCallTest`, `PostSurveyActionCallerTest` and `PostSurveyActionDeliveryTest`.
