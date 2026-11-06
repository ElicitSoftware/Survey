# Segregate surveys by host name (Survey)

## Context

Survey already holds several surveys in one database, but nothing ties a request to one of
them by host: `MainView` offers every row of `survey.surveys` in a picker (auto-selecting when
there is exactly one), and `AboutView` lists every survey before login. The goal is that a host
or sub-domain serves exactly one survey.

Decisions taken:

- The host decides **only which survey is offered**. Brand, offered languages and invitation
  links stay one value per deployment.
- **Strict**: a host reaches only its own survey; an access code for another survey is refused
  there; a host with no mapping shows no survey. A deployment with **no mappings at all behaves
  exactly as today**.
- **One survey per host**, so a mapped host never shows a picker.
- The mapping is **site-local data in the database**, like `report_schema`: never carried in a
  `.elicit` file, never set by an import.

**How hard:** small. It is one repo (Survey), one nullable column, one small pure class, three
touched classes and one REST resource. The existing code already does the right thing once the
survey list is filtered: `MainView.java:271-291` shows "no surveys" for an empty list and
auto-selects for a list of one. No Admin, FHHS or compose change is required.

Why it stays small:

- Survey has no OIDC, so there are no redirect URIs or per-host clients to manage.
- `quarkus.http.proxy.enable-forwarded-host=true` is already set
  (`application.properties:162-165`), so the request's server name is already the public host
  behind a proxy. `PDFService.java:534` already relies on `request.getServerName()`.
- No cookie domain is set, so a session is already bound to one host.
- No `@Push`, so every UI event arrives as an ordinary HTTP request with a host.
- Invitation links are typed by hand into each department's template in Admin
  (`https://<your survey host>/#/login/<ACCESS_CODE>`), so a per-survey host needs no code there.

## Behaviour

Let *mapped* mean `survey.surveys.host` is not null.

| Deployment state | Request host | Offered |
| --- | --- | --- |
| No survey is mapped | any | all surveys, as today |
| At least one is mapped | equals a survey's `host` | that survey only |
| At least one is mapped | matches none | no survey |

Consequence to document: mapping the first survey takes every unmapped survey off line until it
is mapped too. That is what "an unmapped host shows no survey" means in a mixed deployment.

Comparison is on the host name only: lower-cased, port and trailing dot removed. Hosts are
stored in that form.

The REST API is **not** host-gated. Admin reaches Survey at an internal address
(`elicit.survey.url`, default `http://survey:8080`), and every `/api` path identifies its
survey by key or respondent id already. `DeploymentScript.md:19-23` already tells a site to
refuse `/api` on the public host except `/api/pdf/download`.

This is routing, not a secrecy boundary: a client that forges `Host` only gets the login page it
could have opened directly, and still needs a valid access code for that survey.

## Changes (all in `Survey/`)

Branch `feature/<issue>-survey-per-host` from `V3`, after opening the issue. The umbrella's
`elicit-i18n/survey/` bundles need a branch of the same name only if an English key is added;
this plan adds none (the existing `mainView.surveys.isEmpty` covers the unmapped host).

### 1. Migration `V025__Survey_Host.sql`

In **both** `src/main/resources/db/migration/` and `db/migration-v3/` (the upgrade track mirrors
V019 onward). Modelled on `V021__Report_Schema_Per_Survey.sql`:

- `ALTER TABLE survey.surveys ADD COLUMN host character varying(253);`
- `surveys_host_un UNIQUE (host)` — this is what makes it one survey per host.
- `surveys_host_ck CHECK (host IS NULL OR host ~ '<lower-case host name pattern>')`.
- `COMMENT ON COLUMN` saying it is site-local and never carried in a definition file.

### 2. `model/Survey.java`

Add `host`, mapped read-only (`insertable = false, updatable = false`) exactly like
`reportSchema` at `:87`, so nothing persisting a `Survey` can overwrite it.

Admin's import and update services never touch `report_schema` and it survives an update, so a
column Admin's entity does not map is safe in the same way. Confirm with one apply-over-existing
run in verification.

### 3. New `SurveyHosts` (pure, no CDI, no database)

In `com.elicitsoftware`, beside `AccessCodeService`. Same role `etl/ReportSchemaNames` plays for
schema names:

- `normalize(String host)` — lower-case, strip port and trailing dot; null for blank.
- `objection(String host)` — null when valid, otherwise the reason (used by the resource).
- `offered(List<Survey> all, String requestHost)` — the table above.

Keeping it pure matters because the view tests run on `PlainViewTestSupport` with a stubbed
`AccessCodeService` and no Quarkus.

### 4. `AccessCodeService.java`

- Inject `HttpServletRequest` (as `PDFService.java:94-95` does) and add
  `offeredSurveys()` = `SurveyHosts.offered(Survey.findAll().list(), request.getServerName())`.
- `getSurveys()` (`:73-77`) returns `offeredSurveys()` instead of every row.
- `login(int surveyId, String accessCode)` (`:190`) returns null when `surveyId` is not among
  `offeredSurveys()`, before any lookup or auto-registration. This also covers
  `SessionPersistenceService.restoreSessionData()` (`:116`), which signs in again through
  `login`, so a restored session cannot cross hosts.

`MainView` needs no logic change.

### 5. `flow/AboutView.java`

Replace `Survey.findAll().list()` at `:65` with the offered list from `AccessCodeService`, so a
segregated host does not print the other surveys' titles and descriptions before login.

### 6. New `SurveyHostResource`

`/api/surveys/{surveyKey}/host`, written like `etl/ETLSchemaResource.java` and reusing
`ETLBuildResource.respond` / `escape` for the JSON:

- `POST ?name=<host>` — set. `200 ok`, `400 invalid`, `404 unknown`, `409 taken`.
- `DELETE` — clear. `200 ok` (also when already clear), `404 unknown`.

Writes go through a native `UPDATE survey.surveys SET host = :host WHERE id = :id`, the way
`ETLService.java:323, 401, 441` write `report_schema`. Unauthenticated like every REST path
Survey exposes (UC-008 BR-004).

### 7. AIUP artifacts (`docs/`)

- `requirements.md`: new **FR-036** "Serve One Survey per Host".
- `use_cases.puml` and new `use_cases/UC-015-serve-a-survey-by-host.md` (actors: respondent,
  administrator; business rules = the table above, the normalisation, site-local, REST not gated).
- `UC-001-enter-survey-via-access-code.md`: the picker is over the surveys offered at this host.
- `entity_model.md`: the `host` attribute on Survey.

### Not in this change

- An Admin screen to set the host (a dialog like Admin's `RenameReportingSchemaDialog` with a
  client like `ReportingSchemaRenameClient`). Follow-up issue in Admin; the REST call is enough
  to operate the feature.
- Per-host brand, languages, or an invitation-link placeholder.
- Umbrella docs (`DeploymentScript.md`, the installation manual's proxy section) — a short
  follow-up in the umbrella repo describing the setting and the reverse-proxy requirement.

## Tests (each names UC-015, or UC-001 where it extends the sign-in)

- `SurveyHostsTest` — plain JUnit: the three rows of the table, port and case handling,
  trailing dot, invalid names.
- `SurveyHostResourceTest` — `@QuarkusTest`, following `etl/ETLSchemaResourceTest`: set, clear,
  unknown key, invalid name, host taken by another survey.
- `AccessCodeService` — `@QuarkusTest`: with a mapping in place, `login` for another survey's id
  returns null and auto-registration creates nothing; with no mapping, behaviour is unchanged.
- `MainViewLoginFlowTest` — one case: a stubbed list of one offered survey among several shows
  no `login-survey-select` (the existing single-survey assertions at `:257, :265` are the model).

## Verification

1. Run the suite through the Quarkus Dev MCP (`devui-testing_runTests`), not `mvn test`.
2. End to end against the local stack with two surveys imported:
   - With no host set, `http://localhost:8080` shows the picker with both (unchanged).
   - `curl -X POST 'http://localhost:8080/api/surveys/<keyA>/host?name=a.localhost'` and the
     same for B with `b.localhost` (`*.localhost` resolves to loopback in browsers).
   - `http://a.localhost:8080` shows no picker and accepts only A's access codes; a B access
     code is refused there. `/about` lists only A.
   - `http://localhost:8080` now shows "no surveys".
   - `curl -H 'X-Forwarded-Host: b.localhost' http://localhost:8080/` serves B's page,
     confirming the proxy path.
   - `DELETE` both hosts; the picker with both returns.
3. Re-apply survey A's `.elicit` in Admin over the existing survey and confirm
   `select name, host from survey.surveys;` still shows the host.
4. `resetDatabase.sh V2` then start: confirm the upgrade track applies V025.
