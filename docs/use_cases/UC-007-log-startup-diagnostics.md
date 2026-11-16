# UC-007: Log Startup Diagnostics

## Overview

- **ID:** UC-007
- **Name:** Log Startup Diagnostics
- **Primary Actor:** System Operator
- **Secondary Actors:** External Report Service, External Post-Survey-Action Service
- **Goal:** Learn from the application log, shortly after Survey starts, whether this deployment is wired correctly: what is running, whether both database connections work and which migration the schema is at, which brand resolved, whether every report service and post-survey action is reachable from where Survey runs, and which settings need attention.
- **Status:** Implemented

## Preconditions

- The application has started and is serving respondents. Nothing in this use case blocks or fails startup.
- Survey has no administrator login (C-009); the log is the only channel an operator has into a running instance, which is why the report is written there rather than shown on a page as the Admin and Author modules do.

## Main Success Scenario

1. `elicit.diagnostics.startup.delay` (default 10 seconds) after startup, the system builds the report on a background thread so the first outbound connections have had time to settle.
2. The system writes what is running: application name, version, build time, active profile, start time and uptime.
3. The system probes the application connection (`survey_user`) and the owner connection (`elicit_owner`), reads the latest applied Survey migration through the application connection, and counts the installed surveys.
4. The system reports which brand directory resolved and, for each expected brand asset, whether it came from the mounted directory, the local directory or the embedded default.
5. For every report service and post-survey action configured in the database, and for the telemetry collector when telemetry is enabled, the system probes the address from its own network position and reports it reachable or not. Report services and post-survey actions are only ever GET-probed; the real calls carry respondent data and are never made here. Each report service's line names the credential a real call would carry (`service token` or `none`, UC-005 BR-020); the probe itself never carries one, and a `401` or `503` from a service that requires the token counts as reachable. For each post-survey action the line also says which credential a real call would carry (`bearer token`, `service token` or `none`, UC-015 BR-003, BR-010), and whether it sends the subject's External ID; an External-ID action at a plain `http` address is reported at WARN, because every call to it will be refused (UC-015 BR-002).
6. The system lists the effective values of the deployment settings that matter (datasource addresses and users, auto-registration, the reporting ETL flag, the brand path, proxy forwarding and the trusted proxy, the platform service token and its trusted origins, telemetry, the log level), reporting secrets only as present or absent (the service token as set or not set).
7. The system ends the report with a count of the warnings above it. Each line is logged on its own: findings the operator has to act on at WARN, everything else at INFO.

## Alternative Flows

**A1: A target does not answer**
- **Trigger:** A probe in step 5 is refused, times out after the 5 second bound, or has an address that cannot be probed.
- System reports that target as DOWN (or UNKNOWN for an unprobeable address) with the reason, at WARN, and continues with the next target.

**A2: The application user cannot read the Flyway history**
- **Trigger:** The read in step 3 as `survey_user` is denied because the schema predates the V017 grant.
- System reads the history through the owner connection instead and reports the version at WARN, naming the application user's failure, because the Author module's System page reads the same table as the same user.

**A3: A test-only setting is on**
- **Trigger:** `accessCode.autoRegister` is true in step 6.
- System reports the setting at WARN with the reminder that any access code a visitor types creates a respondent and the setting must be removed for production.

**A4: The build information was not recorded**
- **Trigger:** The version or build timestamp was not filtered into the packaged configuration (step 2).
- System writes "unknown" for that value instead of failing.

**A5: No survey is installed**
- **Trigger:** `survey.surveys` is empty in step 3.
- System reports zero surveys at WARN with the instruction to import one through Admin and restart.

**A6: A brand asset is missing or unreadable**
- **Trigger:** An asset in step 4 resolves nowhere, or the file exists but cannot be read.
- System reports that asset at WARN; the page renders without it, which is exactly why the report names it.

**A7: The report itself fails**
- **Trigger:** An unexpected error while building the report.
- System logs the failure at WARN and does nothing else; the application is unaffected (BR-002).

**A8: The report is disabled**
- **Trigger:** `elicit.diagnostics.startup.enabled=false` (the test profile sets this).
- System writes nothing.

**A9: Forwarded headers are honored from any client**
- **Trigger:** `quarkus.http.proxy.proxy-address-forwarding` is true and `quarkus.http.proxy.trusted-proxy` is unset in step 6.
- System reports the trusted-proxy setting at WARN with the advice to name the proxy's address, or to turn forwarding off where there is no proxy (BR-006).

**A10: The platform service token is missing or is the development one**
- **Trigger:** `elicit.service.token` is not set, or equals the well-known development value, in step 6.
- System reports the setting at WARN (BR-007).

## Postconditions

**Success:**
- The report is in the log; no data is changed.

**Failure:**
- None; the report is read-only and the application state is untouched either way.

## Business Rules

- **BR-001:** No password or other secret is ever written to the report. A secret is reported as present or absent, and a password embedded in a JDBC URL is masked (NFR-008).
- **BR-002:** The report never delays or fails startup and never takes the application down: it runs on a daemon thread after the delay, and any failure is caught and logged.
- **BR-003:** Every probe is bounded by a 5 second timeout and is harmless to its target: a GET or a TCP connect, never the POST a respondent's finalize would make (NFR-009).
- **BR-004:** The report is visible at the default container log level. Containers run at `LOG_LEVEL=WARN`; the `com.elicitsoftware.diagnostics` category is pinned to INFO so the whole report appears, and the lines that need action are at WARN so they appear even if that pin is removed.
- **BR-005:** A post-survey action's credential is reported as configured or not, never by value (BR-001, UC-015 BR-003).
- **BR-006:** Forwarding switched on with no trusted proxy configured is reported at WARN: any client could then set the address, host and scheme Survey believes it is served under (NFR-018). A named proxy, or forwarding off, is reported at INFO.
- **BR-007:** The platform service token is reported as set or not set, never by value. Not set is reported at WARN: the paths under `/api/` are closed, Survey reports not ready, and no call that would carry the token is made (UC-016 BR-003). The well-known development value is reported at WARN as a value a real site must replace.

## Notes / Known Gaps

- The report captures the state at startup only. A report service that goes down later is found by a respondent's finalize (UC-004 A1), not by this report. An on-demand check reachable by the Admin module is a possible follow-up; the checks are plain CDI beans so they can be exposed without rewriting them.
- Reading the latest migration as `survey_user` depends on the V017 grant on the Flyway history table. A schema that predates it is read through the owner connection instead, and the report warns that the Author module's System page cannot read it either until the grant is applied.

- The health endpoints answer a status and a fixed text only (NFR-014); the cause of a failed check is in the log, and this report carries the detail about each connection.
- The `/version` page shows the container's time, from the JVM's clock, and the creation date of its image. `ContainerInfo` finds the date once per application start, each command bounded by 2 seconds, so viewing the page starts no process.

## Reference

Traces to FR-016, NFR-008, NFR-009 and NFR-018. Implemented by `StartupDiagnosticsReport` with `BuildInfo`, `DatabaseDiagnostics`, `BrandDiagnostics`, `ConnectionChecks` and `SettingsReport` (all under `com.elicitsoftware.diagnostics`). Verified by `StartupDiagnosticsReportTest`, `ConnectionChecksTest`, `BrandDiagnosticsTest` and `SettingsReportTest`.
