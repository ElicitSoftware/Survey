# Segregate surveys by host name (Survey)

## Context

Survey already holds several surveys in one database, but nothing ties a request to one of
them by host: `MainView` offers every row of `survey.surveys` in a picker (auto-selecting when
there is exactly one), and `AboutView` lists every survey before login. The goal is that a host
or sub-domain serves exactly one survey.

Decisions taken:

- The host decides **only which survey is offered**. Brand and invitation links stay one value
  per deployment. The site's offered languages stay one value too, as the ceiling; which of them
  a respondent is shown is the survey's to narrow (see "Languages follow the survey").
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

## Author and Admin on sub-domains of the same name

A site that serves `familyhistory.survey.app.med.umich.edu` and `census.survey.app.med.umich.edu`
can serve `author.survey.app.med.umich.edu` and `admin.survey.app.med.umich.edu` too. That is
**outside this change and needs no code in any module**: the host column above decides which
survey *one Survey instance* offers, whereas the Author and Admin hosts are routed by the reverse
proxy to different containers before Survey sees the request. The two features do not interact.

What the site configures:

- **Proxy routing by host.** The proxy sends `author.` to the Author container (8084 in Author's
  compose file), `admin.` to Admin (8081) and every survey host to Survey (8080), forwarding
  `X-Forwarded-Host` and `X-Forwarded-Proto`. All three applications already set
  `quarkus.http.proxy.enable-forwarded-host=true` with address, prefix and `X-Forwarded`
  handling (Survey `application.properties:162-165`, Admin `:117-120`, Author `:93-96`), so
  Admin's and Author's OIDC redirect URIs are built from the public host.
- **OIDC clients.** Both apps answer the provider on the fixed path `/callback` and sign out
  to `/`, so the provider's `elicit-admin` and `elicit-author` clients (app registrations, on
  Entra) need the exact redirect URIs `https://admin.survey.app.med.umich.edu/callback` and
  `https://author.survey.app.med.umich.edu/callback`, and the post-logout URIs
  `https://admin.survey.app.med.umich.edu/` and `https://author.survey.app.med.umich.edu/`.
  Wildcards such as the seeded realm's `http://localhost:8081/*` are a Keycloak convenience;
  Entra accepts none. This is done on the site's provider, not in the bundled realm file. The
  browser is sent to `quarkus.oidc.auth-server-url`, so that must be the provider's public
  address in both apps, not the `host.docker.internal:8180` default. Keycloak stands in for any
  OIDC provider throughout; the provider-specific settings (role claim path, principal claim,
  `offline_access`) are listed in Author's access-control plan, section 6.
- **Sessions stay on their host.** No application sets a cookie domain, so Admin's and Author's
  session cookies are never presented to the survey hosts, and a survey host's Vaadin session is
  never presented to a sibling. The `Strict-Transport-Security: includeSubDomains` header each
  app sends covers only hosts *beneath* its own name (`*.author.survey...`), never a sibling,
  so it changes nothing here. The Survey mapping sees only `admin.` or `author.` if the proxy
  mis-routes them, and then shows "no surveys" — a visible misconfiguration, not a leak.
- **Admin to Survey is unaffected.** Admin reaches Survey at `elicit.survey.url`, the internal
  address, and the REST API is not host-gated (above), so the apply, the ETL build and the
  schema rename keep working whatever the public hosts are.
- **Author to Survey is unaffected.** Author never calls the production Survey. Its one Survey
  dependency, `author.preview.survey.url`, is the preview instance (`author-survey`, the
  `author` database), which has no host mappings and so behaves as today.

One thing to settle in Author if the preview is also to get a host, say
`preview.survey.app.med.umich.edu`: `author.preview.survey.url` serves both a server-to-server
call (`PreviewSurveyClient`, dropping a reporting schema before a delete) and the browser-facing
preview link on the Reports page (`ReportsView`). Author's compose file sets it to the internal
`http://author-survey:8080`, which a browser cannot open. Either the property is set to the public
URL and the Author container can resolve it, or it is split into an internal and a public
property. That is an Author issue, separate from this one.

## The Author preview as a consumer

Author's preview is a second Survey instance (`author-survey` in Author's compose file) over the
`author` database, with `accessCode.autoRegister=true`. Its login page offers every authored
survey in the picker and any typed code enters any of them. Author's per-survey access control
(`Author/docs/plan/survey-access-control.md`) hides ungranted surveys inside Author but names the
preview as outside its reach. This change is what lets Author close that gap; **nothing extra is
needed here**, only the behaviour above used in a particular way:

- Author maps **every** authored survey to a host (`<slug>.preview.<site>`) through
  `SurveyHostResource` on create and import, and clears it on delete, at the internal address it
  already holds in `author.preview.survey.url`. Once one survey is mapped the bare preview host
  shows "no surveys", which is the intended state there.
- The preview runs with `accessCode.autoRegister=false`. Author mints the preview respondent
  itself, for a survey the signed-in author is granted, and opens `https://<host>/#/login/<code>`.
  The host offers one survey, `login` refuses a code belonging to another, and with
  auto-registration off a visitor with no minted code gets no further than the login page.
- The two instances share the `host` column by schema only. Values are site-local, so the
  production and preview mappings never meet, and a `.elicit` carries neither.

What this does not do: hide a survey's *name* from someone who guesses its preview host. The
login page and `/about` show that one survey's title. Author accepts the same class of leak for
name collisions; deriving the preview host from the survey key rather than its name closes it.

If a site cannot provide a wildcard record and certificate for `*.preview.<site>`, the fallback
is a different Survey change, not part of this one: a `/login/<code>` link that resolves the
survey from the code and hides the picker. The bare host would still list every survey, so the
per-host form is preferred.

## Languages follow the survey

Today the language choice is one list per deployment, and with several surveys in one
installation the mixed result is visible. Two things decide what a respondent sees:

- `LanguageSwitcher` (`i18n/LanguageSwitcher.java:37-41`) lists `provider.getProvidedLocales()`:
  English plus every bundle mounted under `/opt/i18n/survey`, narrowed by `i18n.offered.locales`
  (UC-009 BR-002). It never looks at a survey, and `LocaleSelection.resolve` (`:55`) checks a
  `?lang=` tag against the same list.
- `ContentTranslator.language` (`:140-165`) serves a survey's text in the session language only
  when the survey publishes it (`surveys.content_languages`, authored in Author and carried in the
  `.elicit`) **and** the site offers it for the chrome (BR-005, BR-009). Otherwise the questions
  fall back to the survey's base language.

So a site serving a Spanish-and-Arabic family history survey beside an English-only census lets
the census respondent pick Arabic and read English questions between Arabic buttons. BR-009
prevents the reverse case, translated questions between English buttons, but not this one.

### Behaviour

Let the *survey set* of a survey be its base language plus its published content languages, and
the *site set* be what `ElicitI18NProvider` provides. The languages offered to a respondent are
the **intersection of the two**, resolved exact-tag-first then same-language, the chain
`LocaleSelection.resolve` already uses:

| Where | Survey in play | Offered |
| --- | --- | --- |
| Login page, mapped host | the host's survey | site set ∩ survey set |
| Login page, no mapping | none yet | site set, as today |
| After login (and after session restore) | the respondent's survey | site set ∩ survey set |

- The switcher lists the offered set and stays hidden when it has one entry, as it does now.
- `?lang=` and the switcher refuse a language outside the offered set.
- At login, a session language outside the respondent's survey set is replaced by the survey's
  base language. That is a change from today, where a chosen chrome language survives into any
  survey.
- A survey with no published languages offers its base language alone, so a mounted Arabic
  bundle is hidden on that survey's host. That is the point, but it is a visible change for
  existing English-only surveys.
- The site set remains the ceiling: `i18n.offered.locales` still narrows every survey, and a
  survey cannot offer a language the site has not mounted (BR-002 unchanged). Direction and font
  scale follow the language as before (BR-004, BR-011).
- A survey whose base language is not English, and which publishes nothing else, offers that
  language alone: the survey set removes English even though the provider always offers it
  (BR-002). English remains the fallback for a chrome key the mounted bundle lacks (BR-001). A
  French-only survey therefore needs French as its *base* language, not English plus a French
  translation, and a French chrome bundle mounted by the site.

No new configuration. The author's published set is already in the database, and the site keeps
one mount and one offered list. If a site later wants to narrow one survey below what its author
published, that is a site-local column like `host`, set by the same kind of endpoint and never
carried in the file; not in this change.

### Changes (`Survey/`, can be its own issue and PR after the host change)

1. **`i18n/OfferedLanguages`** (pure, beside `SurveyHosts`): `offered(List<Locale> site, Survey
   survey)`; null survey returns the site set. The same-language matching of `ContentTranslator`
   moves here so the three callers agree.
2. **`ContentTranslator.language`** calls it instead of its own loop; behaviour unchanged.
3. **`LocaleSelection.resolve`** takes the survey in play: the respondent's survey from
   `UISessionDataService` when one is in session, otherwise the single offered survey on a mapped
   host (`AccessCodeService.offeredSurveys()` of size one), otherwise null.
4. **`LanguageSwitcher`** is built from the same offered set and rebuilt on login and on session
   restore, since the set changes when the survey becomes known. `MainLayout.java:151-152` creates
   it once today.
5. **Login** (`AccessCodeService.login`, and so session restore too): after a successful sign-in,
   if the stored session locale is outside the survey's offered set, `LocaleSelection.apply` the
   base language.
6. **AIUP**: UC-009 gains BR-012 "The languages offered follow the survey in play" with the table
   above; BR-002 gains the sentence that the site's set is the ceiling; FR for UC-009 amended;
   UC-015 cross-references it.

### Tests (each names UC-009 BR-012)

- `OfferedLanguagesTest`, plain JUnit: the three rows, base language always present, a published
  language the site lacks is dropped, same-language resolution (`es` offered for `es-419`), an
  unpublished-but-mounted language dropped, null survey returns the site set.
- `LanguageSwitcherTest`: items narrow once a survey is known; hidden at one entry.
- `LocaleSelectionTest`: `?lang=ar` refused on an English-only survey's host; accepted on the
  family history survey's host.
- `AccessCodeService` `@QuarkusTest`: a session in Arabic that signs in to an English-only survey
  continues in English; one that signs in to a survey publishing Arabic keeps Arabic.

### Verification

Against the local stack with the family history survey (publishes `es-419` and `ar`) and the
census survey (English only) imported and both mounted languages offered:

- `http://localhost:8080` (no mapping): the switcher offers all three; pick Arabic, sign in to the
  census survey, the page returns to English and the switcher is hidden; sign in to the family
  history survey, Arabic is kept and the layout is right-to-left.
- With hosts mapped: `http://census.localhost:8080` shows no switcher and refuses `?lang=ar`;
  `http://familyhistory.localhost:8080` offers all three.
- `i18n.offered.locales=en,es-419`: Arabic disappears from the family history host too.

## Target deployment: what these plans deliver and what they do not

Reviewed 2026-11-06 against the intended Michigan Medicine layout, so that the gaps are on record
beside the plans. **Not being implemented now.** The site: `survey.med.umich.edu` as the base,
`author.` and `admin.` protected by Microsoft Entra ID with users limited to the surveys they are
authorized for, `family.` serving the family history survey in English, Spanish and Arabic,
`census.` serving the census in English only, `french.` serving a French survey in French only,
and no data, reports or statistics shared between surveys: to respondents, authors,
administrators and report users each survey behaves as if it had its own database.

| Host | Delivered by | Verdict |
| --- | --- | --- |
| `author.` | Author access-control plan PRs 1-3, its section 6 for Entra | Yes. Accepted leaks: unique survey names ("name in use"), reporting tags and dimensions as a shared vocabulary, the preview title on a guessed preview host. Previews live on a separate wildcard (`family.preview.survey.med.umich.edu`). |
| `admin.` | nothing | **No.** Admin scopes by department, which spans surveys; `survey.user_surveys` is mapped but never read; Register offers every survey, Participation has a picker over every survey, Search and the exports follow departments; message templates are per department with the invitation host typed by hand, so a department serving two surveys cannot send the right host to each. Needs an Admin plan: grants per survey enforced in services as Author's are, Register and Participation narrowed to granted surveys, templates keyed on survey as well as department. Recorded in the Author plan's limitations as the next gap. |
| `family.` | this change + "Languages follow the survey" | Yes. Mapped host; publishes `es-419` and `ar`; curated bundles mounted; all three offered. |
| `census.` | this change + "Languages follow the survey" | English only **with** the language section. The host change alone still offers Spanish and Arabic chrome around English questions. |
| `french.` | not with what exists | The curated set has no French: the site mounts a full French chrome bundle for Survey. The survey is authored with French as its base language. Author's `author.content.languages` must list French before it publishes it, and `checkLanguages.sh` requires the three curated `elicit-i18n/` directories to agree, so a *curated* French means French for Admin and Author too; a site-private mount avoids that. |
| `survey.med.umich.edu` | this change | Shows "no surveys" once any host is mapped (the third row of the behaviour table). Route it to a landing page at the proxy if wanted. Hosts are lower-cased, so `Family.` is `family.`. |

"As if it had its own database", by actor:

- **Respondents: holds.** One survey per host, another survey's code refused, sessions bound to
  the host, `/about` per host. **One condition is not optional:** the proxy must refuse `/api` on
  every public host except `/api/pdf/download` (`DeploymentScript.md:19-23`). The REST API is not
  host-gated and is unauthenticated, including `SurveyHostResource`, so an exposed `/api` lets
  anyone remap the hosts.
- **Reports: partly.** Each survey has its own `report_<slug>` schema (UC-008) and `surveyreport`
  holds only the static `dim_date` and `dim_status`. But `etl/Sql.java:88-90` grants `USAGE` and
  default `SELECT` on **every** survey's schema to the one `surveyreport_user` role, so a report
  user who connects to the database reads all of them. Nothing plans a role per schema. Small
  ETL change, its own Survey issue: one read role per report schema, created with the schema and
  dropped with it, `surveyreport_user` kept only for the shared lookups.
- **Statistics: holds.** The only cross-survey aggregates are the Quarkus metrics and Jaeger
  traces, per application and operator-facing; Prometheus is not wired into the umbrella compose.
- **Authors: holds**, with the three accepted leaks in the table.
- **Administrators: does not hold** until the Admin plan above exists.
- **Shared by design:** one brand (per-host brand is excluded above), one SMTP relay, one identity
  provider, one mounted language set as the ceiling.

Outstanding before the layout works as listed: the Admin per-survey plan, a read role per report
schema, the French chrome bundle and base language, and the `/api` refusal at the proxy.

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
