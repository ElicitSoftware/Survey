# Elicit Survey — Vision

> **Draft.** This is the AIUP seed document. Edit freely — `/requirements`,
> `/entity-model`, and the rest of the AIUP workflow read from here.

## Mission

Elicit Survey presents questions to respondents and records their answers. It is
a survey-agnostic decision-support engine: it does not embed any single
questionnaire. Instead, it walks each respondent through a configured decision
tree and persists the path they take, so downstream tools (reporting, ETL,
clinical follow-up) can analyze both the answers and the navigation that
produced them.

## Target Users

- **Respondents** — patients, study participants, or members of the public
  invited to complete a survey via a tokenized link in an email.
- **Survey administrators** — staff (in the companion Admin app) who register
  subjects, generate invitation tokens, and monitor completion.
- **Survey authors** — designers (in the companion Authoring tool) who define
  question banks, branching logic, and publish surveys to the database.
- **Researchers / clinicians** — consumers of the recorded responses and the
  derived reports (PDF, ETL feeds).

Elicit Survey itself is the respondent-facing module of the broader
[Elicit Software](https://github.com/ElicitSoftware/) platform.

## Goals

- Render any survey defined in the database without code changes.
- Adapt the next question shown based on prior answers (decision tree, not just
  linear forms).
- Record the full traversal — questions answered, skipped, or revisited — for
  auditability.
- Generate a respondent-facing report (PDF) summarizing what was submitted.
- Integrate with downstream ETL for analytical reporting.
- Operate as a Quarkus service deployable via Docker.

## Scope

**In scope**
- Token-based respondent entry from invitation emails.
- Question rendering, validation, branching, and submission via the Vaadin UI.
- Persistence of answers and decision-tree paths in PostgreSQL.
- PDF report generation for the respondent.
- ETL hand-off (Kimball Type 2 dimensions — see `research/Kimball_type_2.md`).
- OpenTelemetry-based observability (see `guides/OPENTELEMETRY_SETUP.md`,
  `guides/METRICS_GUIDE.md`).

**Out of scope** (handled by sibling modules)
- Authoring surveys / defining question trees → Authoring tool.
- Subject registration, token generation, invitation email dispatch → Admin app.
- Cross-survey analytics dashboards → downstream reporting tools.

## Constraints

- **License:** PolyForm Noncommercial 1.0.0.
- **Stack (do not deviate without explicit approval):**
  - Java 25
  - Quarkus 3.34.x
  - Vaadin 25.1.x (Flow / server-side UI)
  - Hibernate ORM with Panache (JPA) — *not* jOOQ
  - PostgreSQL
  - Maven build, Docker deploy
- **Branching:** part of the multi-module Elicit Software platform; database
  schema and message contracts are shared with the Admin and Authoring modules.
- **Compliance:** survey content may include PHI in clinical deployments
  (e.g., the Family Health History Survey) — treat respondent answers as
  potentially sensitive.
- **Accessibility:** Vaadin components should be used in a way that preserves
  the framework's built-in WCAG support; avoid raw HTML that bypasses it.

## Reference Deployment

The [Family Health History Survey (FHHS)](https://github.com/ElicitSoftware/FHHS)
is the canonical reference deployment of Elicit Survey and the best place to
see the system in action end-to-end.
