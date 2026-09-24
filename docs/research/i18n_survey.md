# Translating Survey Content in the Database

**Status:** research / proposal, 2026-09-20. Nothing here is implemented.

Source: the 2020 design in `../Survey_i18n/survey_ms_question` (its `I18nEntity.java`,
`Question.java` with `@PostLoad internationalize()`, and the Flyway scripts under
`src/main/resources/db/migration`); this module's `docs/requirements.md` (C-014,
FR-018 to FR-023), UC-009, `docs/entity_model.md`, `research/Kimball_type_2.md`,
`QuestionManager.java`, `QuestionService.java`, `etl/Sql.java`, the `i18n` package and
the two Flyway tracks; Author's `docs/requirements.md` (C-015, C-017, C-019, C-021,
NFR-011, FR-036), UC-034, `author/definition/ElicitFormat.java`,
`SurveyDefinitionExporter.java`, `SurveyDefinitionImporter.java`; Admin's
`SurveyDefinitionImportService.java`, `SurveyDefinitionUpdateService.java`,
`SurveyDefinitionExportService.java`, `RespondentExportService.java`,
`RespondentImportService.java`, UC-011 and UC-012.

Three facts fix the frame. Nothing on the V3 track has shipped: not the Survey V3
schema, not Author, not Admin V3, not the `ELICIT_SURVEY_EXPORT_V1` file. The file
definition and the V3 migrations can therefore change without a compatibility layer.
Every structural table already carries a UUID element key (`survey_key`,
`question_key`, `step_key`, `section_key`, `select_item_key`, `select_group_key`,
`relationship_key`, `steps_sections_key`, `sections_question_key`, `report_key`,
`post_survey_action_key`, `dimension_key`, `ontology_key`, `metadata_key`; V015), and
that key, not the surrogate `id`, is the identity that survives import, export and
versioning. And the `i18n` branch already resolves a locale per session for the
application chrome, which content translation can reuse.

Four choices were made while this document was researched and are treated as
settled below: one generic translations table rather than a side table per entity;
that table is Kimball Type 2 like the eight structural tables, so translations are
versioned, pinned per respondent, and retired rather than deleted;
`answers.display_text` keeps the base language and gains a localized copy; and the
`.elicit` file is redefined in place under its existing `ELICIT_SURVEY_EXPORT_V1`
header.

## Question

Several years ago a design was written for storing other languages in the survey
database: every structural table had a `<table>_lang` child keyed by a language code,
each entity mapped that child as a map, and a `@PostLoad` callback copied the current
language into transient fields with an English fallback. Can that design be implemented
in today's Survey, in Author, and made to work with the `.elicit` import and export?

## Short Answer

Yes, the idea can be implemented; the mechanics cannot be ported and should not be.

What survives from 2020 is the shape of the problem and three of its answers: other
languages live in side rows rather than in the structural row; a missing translation
falls back to the base language rather than failing; and the translation is projected
onto the entity at read time rather than stored as a sibling row. What cannot be ported
is everything that made those answers work in a 2020 Quarkus 1.9 REST service: the
language stashed in a Hibernate `Session` property through an internal API that no
longer exists; side tables keyed on surrogate ids that Type 2 versioning and every
import now reallocate; a `@PostLoad` that overwrites mapped fields on managed entities;
per-endpoint parsing of `Accept-Language`; and a review view with hardcoded
`_en`/`_es`/`_ar` columns.

The recommendation is:

- **One table, `survey.translations`**, one current row per `(survey_id, element_key,
  field, language)` with the translated `value` and a `source_hash` of the base text it
  was translated from. Base text stays in the structural row. The table is Type 2 in the
  same shape as `questions`: a durable `translation_id`, a `translation_key` UUID,
  `version`, `effective_from`, `effective_to` and the `scd_close_predecessor` trigger,
  so a respondent sees the translation that was current when they first accessed the
  survey, a corrected translation is a new version, and removal is a closed
  `effective_to`. A translation whose hash no longer matches the base text is stale and
  is not served.
- **A `ContentTranslator` service** in Survey that reads the session locale through
  the existing `Translations.currentLocale()`, bulk-loads a survey's translations once,
  and hands render sites the translated string or the base string. No `@PostLoad`, no
  map on the entities.
- **`answers.display_text` keeps the base-language sentence**; two new columns,
  `display_text_local` and `display_language`, hold the sentence in the respondent's
  current language, written by the same `buildDipslayText` pass. Reporting, the ETL,
  Admin and the nav and review SQL keep reading the base column; the respondent-facing
  paths prefer the local one.
- **A `translations:` record type in the `.elicit` file** plus `base_language` and
  `content_languages` on the `surveys` record, redefined in place under the existing
  header, versioned like the other eight Type 2 records. Author writes it; Admin's
  import and update services match it by `translation_key`, version it on change,
  close it when retired, never delete.
- **A Translations view in Author**, one grid per language over every translatable
  string with translated / missing / stale status, rather than language tabs inside
  the five Binder-bound editor dialogs.
- **A hand-off file per survey and language**, JSON with the instructions, glossary,
  context and tokens of every string, that a translator or an AI agent fills in and
  Author imports back with per-item validation of tokens, length and HTML.
- **One file for every site, and a site rule.** Author is the only writer and every
  site applies the same file, so a language one site requested and verified reaches
  every site. A site offers a content language only when its chrome mount also carries
  that language, so a site that has not adopted the language holds inert rows. The
  respondent file stays language-neutral because `display_text` keeps the base
  language, so answers from any site import into a central instance for review; its
  identity columns must be redefined for that to work across databases, a defect that
  predates this design (section 11).

The rest of this document justifies each of those and lists what changes where.

## 1. The 2020 design

The old project is `survey_ms_question`, one of ten Quarkus 1.9 microservices, Java 11,
`javax.*`, RESTEasy with JSON-B, Hibernate Panache in active-record style, and Flyway
against a `survey_owner` schema ported from Oracle. It is not a git repository and has
no design note; the inline comments are the only statement of intent.

### 1.1 Storage

Structural tables held no text at all. `questions` was `id, survey_id, type_id,
required, min_value, max_value, validation_rule, select_group_id`. Every translatable
string lived in a child table:

```sql
CREATE TABLE survey_owner.questions_lang(
  id bigint NOT NULL,
  question_id bigint NOT NULL,
  lang character varying(25) NOT NULL,
  text character varying(4000) NOT NULL,
  short_text character varying(100),
  tool_tip character varying(255),
  validation_text character varying(255),
  CONSTRAINT questions_lang_pk PRIMARY KEY (id),
  CONSTRAINT questions_lang_questions_fk FOREIGN KEY ("question_id") REFERENCES survey_owner.questions ("id"),
  CONSTRAINT questions_lang_un UNIQUE (question_id, lang)
);
```

The same shape existed as `surveys_lang(name)`, `steps_lang(name)`,
`sections_lang(name)`, `select_items_lang(display_text, text_value)` and
`relationships_lang(default_upstream_value)`. `lang` was a free-text column with no
lookup table and no check. The unique constraints were inconsistent: only
`questions_lang` and `relationships_lang` used `(parent_id, lang)`; the others folded
the text into the key (`steps_lang_name_un UNIQUE (step_id, lang, name)`,
`select_items_lang_un UNIQUE (display_text, lang, item_id)`), which permits two rows for
one language and lets the entity map silently keep one of them.

English rows were seeded in `V1.0.2__POPULATE_QUSTION_DATA.sql` alongside the
structural rows, and Spanish in a separate `V1.0.3__POPULATE_QUSTION_DATA_ES.sql`, 187
rows keyed on the literal surrogate ids the English script happened to allocate. Adding
a language meant writing a migration.

### 1.2 Entities

`I18nEntity` was not a JPA type. It was an abstract class over `PanacheEntityBase` that
declared `internationalize()` and stored the request's language as a Hibernate
`Session` property:

```java
public abstract class I18nEntity extends PanacheEntityBase {
    abstract void internationalize();

    public static void setLanguage(String language) {
        JpaOperations.getEntityManager().unwrap(Session.class).setProperty("lang", language);
    }

    public static String getLanguage() {
        return (String) JpaOperations.getEntityManager().unwrap(Session.class).getProperties().get("lang");
    }
}
```

`Question` mapped the child rows as a lazy map keyed by `lang` and projected the chosen
language into transient fields after load:

```java
@OneToMany(mappedBy="question",fetch = FetchType.LAZY)
@MapKey(name = "lang")
private Map<String, QuestionLang> languages = new HashMap<String, QuestionLang>();

@Transient public String text;
@Transient public String textEn;
@Transient public String shortText;
@Transient public String toolTip;
@Transient public String validationText;
@Transient public String validationTextEn;

@PostLoad
void internationalize() {
    try {
        this.text = languages.get(I18nEntity.getLanguage()).text;
        this.shortText = languages.get(I18nEntity.getLanguage()).shortText;
        this.toolTip = languages.get(I18nEntity.getLanguage()).toolTip;
        this.validationText = languages.get(I18nEntity.getLanguage()).validationText;
    } catch (Exception e){
        //Fall back to english if there is no translation
        this.text = languages.get("en").text;
        this.shortText = languages.get("en").shortText;
        this.toolTip = languages.get("en").toolTip;
        this.validationText = languages.get("en").validationText;
    }
    this.textEn = languages.get("en").text;
    this.validationTextEn = languages.get("en").validationText;
}
```

`Step`, `Section`, `SelectItem`, `Survey` (eager) and `Relationship` repeated the
pattern; only `Relationship` guarded `containsKey("en")` before dereferencing. The
`languages` map was private so JSON-B never serialized it, and the child entities
carried `@JsonbTransient` on the back reference.

Two more classes took different routes. `Answer` stored the rendered text twice,
`display_text` in the respondent's language and `display_text_en`, and its
`@PostLoad` swapped the English copy in when the language was `en`. `Review` was an
`@Immutable` entity over a SQL view that pivoted `questions_lang` and
`select_items_lang` into `display_text_en`, `display_text_es`, `display_text_ar` with
three `LEFT JOIN ... AND lang = 'xx'` each, and a `switch` on the language string.

### 1.3 Language selection

There was no filter and no locale resolver. Each REST endpoint called a private method:

```java
private void setLanguage() {
    request.getHeader("Authorization");
    String lang = request.getHeader("Accept-language").substring(0, 2);
    if (lang == null) {
        lang = "en";
    }
    I18nEntity.setLanguage(lang);
}
```

The substring runs before the null check, so a missing header is a 500; the language is
the first two characters with no region or quality handling; the method is called in
three of the four endpoints, so entities loaded by the fourth are internationalized with
a null language and fall into the English branch by exception. The respondent's
language was also stored in `respondents.lang` at login by the access-code service but
never read by the question service.

### 1.4 What survives

| 2020 element | Verdict | Why |
| --- | --- | --- |
| Other languages in side rows, base text elsewhere | Survives, inverted: base text stays in the structural row, only other languages go to a side table | The structural row is what Type 2 versions, what the partial unique indexes guard, what the ETL and Admin compare. |
| `<table>_lang` keyed on the surrogate `id` | Cannot be ported | Surrogate ids change on every version bump (`scd_close_predecessor` inserts a new row) and on every import. The V015 element key is the stable identity. |
| Language as a Hibernate `Session` property | Cannot be ported | `io.quarkus.hibernate.orm.panache.runtime.JpaOperations` no longer exists; a session property is invisible to the detached entities Survey's views read. |
| Language from `Accept-Language` per endpoint | Replaced | Survey is Vaadin Flow; the session locale is already resolved by `LocaleInitializer` and `LocaleSelection` and readable through `Translations.currentLocale()`. |
| `@PostLoad` projecting `languages.get(lang)` into fields | Idea survives, mechanics do not | It overwrote mapped fields on managed entities (a dirty write of Spanish into `questions.text` at flush); it is one lazy select per entity; it bakes the load-time language into detached entities; it lives in classes the ETL and Author share. |
| English fallback | Survives as "fall back to the base row" | The base language becomes a survey attribute; the fallback is the row itself and cannot NPE. |
| `display_text` plus `display_text_en` on answers | Survives, inverted | Keep `display_text` as the base text so nothing downstream moves; add a localized copy beside it. |
| `respondents.lang` | Dropped | Nothing needs it, and UC-009 BR-007 says the language choice is session-only. |
| Review as a view with `_en/_es/_ar` columns | Cannot be ported | Languages are open-ended (mounted overlays, FR-020). The review SQL takes `:language` and joins the translations table. |
| Spanish seeded by migration on literal ids | Cannot be ported | Content is authored in Author and travels in the `.elicit` file. |

## 2. What today's codebase imposes

### 2.1 Type 2 versioning and element keys

Eight structural tables (`select_groups`, `select_items`, `steps`, `sections`,
`steps_sections`, `questions`, `sections_questions`, `relationships`) are Kimball Type
2: a durable id (`question_id`), a UUID element key (`question_key`), `version`,
`effective_from`, `effective_to` with the sentinel `9999-12-31 23:59:59+00`, and a
`scd_close_predecessor` trigger (`db/migration/V001__Create_Survey_Schema.sql`, the
`questions` table from line 432). Admin's update service versions an element by closing
the current row and inserting `version + 1` under the same durable id and key
(`Admin/.../SurveyDefinitionUpdateService.java:30-80`). Author never versions; every
row there is version 0, and removal is a closed `effective_to`
(`Author/.../author/survey/ElementService.java:169-196`).

A translation keyed on the surrogate `id` would orphan on the first version bump. Keyed
on the durable id it would not survive import, because durable ids are per instance.
Keyed on the element key it survives both, which is the whole point of V015.

Three partial unique indexes guard base text on current rows:
`steps_survey_name_un (survey_id, name)`, `select_groups_name_un (survey_id, name)`,
`select_items_display_text_un (select_group_id, display_text)`, all `WHERE effective_to
= sentinel`. Translations must never be sibling rows in those tables.

### 2.2 `answers.display_text` is a rendered snapshot

This is the crux and the reason a `@PostLoad` on `Question` would cover far less than
it appears to. The respondent does not see `questions.text`. When an answer row is
created, `QuestionManager` takes the question text (or the section or step name),
substitutes `{Q#}`, `{S#}` and the `{token}` placeholders with that respondent's earlier
answers, and stores the finished sentence:

```java
// QuestionManager.java:1158-1191
private void buildDipslayText(Answer answer) {
    ...
    String text = answer.displayText;
    if (answer.question != null) {
        text = answer.question.text;
    } else if (answer.sectionId != null) {
        Section s = getSectionByDisplayKey(answer.respondentId, answer.getDisplayKey());
        if (s != null) { text = s.name; }
    } else {
        Step s = getStepByDisplayKey(answer.respondentId, answer.getKey());
        if (s != null) { text = s.name; }
    }
    text = text.replaceAll("\\{Q#\\}", answer.getKey().getQuestionInstance() + "");
    text = text.replaceAll("\\{S#\\}", answer.getKey().getStepInstance() + "");
    TreeMap<String, String> values = getValuesMap(answer);
    answer.displayText = replaceTokens(text, values);
}
```

The base text is also written at creation (`QuestionManager.java:516`) and in the
dependent presets (`:802-806`) before this method runs. The token values come from
`Dependent` rows (`getKeyValues`, `:1759-1799`): for CHECKBOX, DROPDOWN, HTML, NUMBER
and RADIO upstreams the value is `relationship.defaultUpstreamValue` when set
(`:1778`), otherwise the raw `text_value`. `defaultUpstreamValue` is therefore
respondent-visible prose and translatable; `overrideUpstreamValue` has no reader in
`QuestionManager`.

Everything downstream reads the snapshot. Every `flow/input/Elicit*` widget passes
`answer.displayText` to its Vaadin component (`ElicitTextField.java:37`,
`ElicitHtml.java:35`, and fourteen more); the section page title is
`answer.displayText` (`SectionView.java:176`); the left navigation is native SQL over
`answers.display_text` (`QuestionManager.java:1817`); the review page is native SQL over
`a.display_text`, `q.short_text` and `i.display_text` (`QuestionService.java:77-110`).
Only tooltip, validation message and placeholder read the live `Question`
(`ElicitComponent.java:95-104`), and only option labels read the live `SelectItem`
(`ElicitComboBox.java:64` and the radio, checkbox-group and multi-select twins).

### 2.3 Where the locale already comes from

The `i18n` branch resolves a locale per session: `?lang=` on any route, the session
attribute `elicit.locale`, `Accept-Language`, then English (`LocaleInitializer`,
`LocaleSelection.resolve`/`apply` at `LocaleSelection.java:40-63`). Any code can read it:

```java
// i18n/Translations.java:43-53
public static Locale currentLocale() {
    UI ui = UI.getCurrent();
    if (ui != null) { return ui.getLocale(); }
    VaadinSession session = VaadinSession.getCurrent();
    if (session != null && session.getLocale() != null) { return session.getLocale(); }
    return ElicitI18NProvider.DEFAULT_LOCALE;
}
```

`QuestionService` is `@NormalUIScoped` (`QuestionService.java:57`) and is called
synchronously from the views, so during `init`, `navigate` and `review` the UI thread
holds the Vaadin session and `currentLocale()` returns the respondent's choice. On the
threads that also load these entities (the migrator, `ETLService` at startup and on
finalize, the test suite) it returns English, which is the right default.

The chrome languages are a deployment concern: the apps ship English only, Spanish
(`es-419`) and Arabic (`ar`) live on the `elicit-i18n/<app>/` mount, and the language
selector is hidden until a second language is mounted. Content languages are a
per-survey concern. The two sets are independent and the design must not conflate them.

### 2.4 Reporting

`dim_step.value` and `dim_section.value` come from `dimension_name`, not from the
respondent-facing `name` (`etl/Sql.java:73-83`); translating `steps.name` does not touch
them. One column does copy prose: `surveyreport.fact_sections.name` is filled from
`survey.steps.name` (`etl/Sql.java:282-325`). It must keep reading the base language.
Nothing in `etl/` reads `answers.display_text`.

### 2.5 The `.elicit` file and its readers

The file is line-oriented and pipe-delimited: `# ELICIT_SURVEY_EXPORT_V1`, count and
`# survey_revision:` headers, then `table: field|field|...` records in the order of
`ElicitFormat.TABLES` (`Author/.../author/definition/ElicitFormat.java:42-45`), with
arity pinned by `REQUIRED_FIELDS` (`:53-67`) and the retired-row check reading
`EFFECTIVE_TO_INDEX` (`:70-78`). There is no serialization layer: Author's exporter is
native SQL to `Object[]` to lines (`SurveyDefinitionExporter.java:58-83`) and the
importer is native `INSERT`s that remap surrogate ids and keep element keys verbatim
(`SurveyDefinitionImporter.java:138-151`, `parseKeyOrMint`). Unknown record types are
errors in all three readers (`SurveyDefinitionImporter.java:151`,
`Admin/.../SurveyDefinitionImportService.java:415`,
`SurveyDefinitionUpdateService.java:396`). Two Python generators under `Author/samples/`
write the format independently, and `SurveyDefinitionExporterTest` proves a
field-for-field round trip (NFR-011) with a normaliser that keys rows by
`table + ":" + fields[1]` (`:52-90`).

Admin is the consumer at deployed sites. Its update service matches every file row to
the target's current row by element key, versions Type 2 rows whose content differs,
updates Type 1 rows in place, closes rows the file marks retired, and ignores rows the
file no longer mentions; it never deletes (`SurveyDefinitionUpdateService.java:30-80`).
Translations must fit that loop or they will go stale at deployed sites.

### 2.6 Grants and migrations

Survey's V001 grants `survey_user` full DML on every table. Admin's own migrations grant
`surveyadmin_user` (`Admin/.../db/migration/V0.0.8__Add_Survey_Export_Import_Grants.sql`,
`V0.0.16__Add_Survey_Update_Grants.sql`; latest is `V0.0.18`), and Admin's tests
bootstrap their own copy of the survey schema
(`Admin/src/test/resources/db/test/V0.0.0.1__TEST_BOOTSTRAP.sql`). Author owns no
migrations; it mirrors Survey's under `src/test/resources/db/schema-mirror/` and
`SchemaMirrorFreshnessTest` fails until `scripts/sync-survey-schema.sh` is rerun.

Survey has two Flyway tracks, `db/migration` and `db/migration-v3`, and every version
must exist in both (`flyway/ManualSchemaMigrator.java:52-62`, enforced by
`ManualSchemaMigratorUpgradeTest`). `db/migration-v3` V001 to V009 are frozen copies of
the released V2 migrations and must not change. The latest version is V016 in both.

### 2.7 What the requirements currently say

Survey `docs/requirements.md` C-014: "Only application chrome is localized. Survey
content stored in the database ... is presented in the language it was authored in;
content translation is tracked on a separate branch." UC-009 BR-005 says content keeps
its authored language; BR-007 says the language choice never crosses sessions and
nothing about it is stored with the respondent. Author `docs/requirements.md` C-021 and
UC-034 BR-005 say the same for authored content and the exported file. This design
supersedes C-014, C-021 and both BR-005s and narrows BR-007; the amendments are in
section 9.

## 3. Storage

### 3.1 Options

| | A. `<table>_lang` per entity, keyed on element key | B. One `survey.translations` table | C. JSONB column on each structural row |
| --- | --- | --- | --- |
| Tables | Seven, in two Flyway tracks, the Author mirror and Admin's bootstrap | One | None |
| `.elicit` | Seven record types, seven exporter queries, seven importer and updater cases | One record type, one case each | JSON inside a pipe-delimited field |
| Adding a translatable field | A migration | A whitelist entry | A key inside JSON |
| Stale detection | A hash column per text column | One hash column | A hash per key inside JSON |
| Type 2 interaction | Fine | Fine | A translation edit dirties the base row, forcing a spurious version and a new `questions.id`, or Admin must compare JSON minus translations |
| Translator UI | Must flatten to (element, field, language) anyway | Already that shape | Must flatten |
| Typing | Columns typed per field | `value text`, lengths enforced by Author against the base column | JSON |

A is the 2020 shape with the key fixed. Its only advantage over B is per-column typing,
and the translator grid and the file record both need the flattened shape regardless.
C conflicts with versioning and with the update service. B is recommended.

### 3.2 Recommended DDL

A new `V017__Survey_Content_Translations.sql` in both `db/migration` and
`db/migration-v3`, identical DDL, differing only in the header comment, exactly as V016.

```sql
CREATE SEQUENCE survey.translations_seq INCREMENT 1 START 1;
CREATE SEQUENCE survey.translations_durable_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.translations_durable_seq TO ${survey_user};

CREATE TABLE survey.translations (
    id                integer      NOT NULL,
    survey_id         integer      NOT NULL,
    element_type      varchar(32)  NOT NULL,  -- surveys | steps | sections | questions | select_items | relationships | reports
    element_key       uuid         NOT NULL,  -- the owning row's *_key; never a surrogate or durable id
    field             varchar(32)  NOT NULL,  -- column name of the base text: text, short_text, name, display_text, ...
    language          varchar(35)  NOT NULL,  -- BCP-47 tag as the chrome bundles use it: es-419, ar
    value             text         NOT NULL,  -- removal is a closed effective_to, never an empty value
    source_hash       char(64)     NOT NULL,  -- sha256 of the base text this was translated from
    source_text       text,                   -- authoring-only snapshot for the stale diff; not exported
    translation_id    integer      NOT NULL DEFAULT nextval('survey.translations_durable_seq'),
    translation_key   uuid         NOT NULL,  -- cross-instance identity, minted once, preserved by import/export
    version           integer      NOT NULL DEFAULT 0,
    effective_from    timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    effective_to      timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    published_by      text,
    published_comment text,
    CONSTRAINT translations_pk PRIMARY KEY (id),
    CONSTRAINT translations_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id),
    CONSTRAINT translations_id_version_un  UNIQUE (translation_id, version),
    CONSTRAINT translations_key_version_un UNIQUE (translation_key, version),
    CONSTRAINT translations_element_type_ck
        CHECK (element_type IN ('surveys','steps','sections','questions','select_items','relationships','reports'))
);
CREATE TRIGGER translations_scd_close_predecessor BEFORE INSERT ON survey.translations
    FOR EACH ROW EXECUTE FUNCTION survey.scd_close_predecessor('translation_id');

-- Integrity: one current row per durable id (also the trigger's lookup).
CREATE UNIQUE INDEX translations_one_current_un
    ON survey.translations (translation_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

-- Integrity + Author grid: one current row per target string per language.
CREATE UNIQUE INDEX translations_target_current_un
    ON survey.translations (survey_id, language, element_key, field)
    WHERE effective_to = '9999-12-31 23:59:59+00';

-- Admin update: find the current row by its cross-instance key.
CREATE INDEX translations_key_current_idx
    ON survey.translations (translation_key)
    WHERE effective_to = '9999-12-31 23:59:59+00';

-- Runtime point lookups and the review SQL join, as-of any instant.
CREATE INDEX translations_lookup_idx
    ON survey.translations (element_key, field, language, effective_from, effective_to);

-- Runtime bulk load of one survey/language into the ContentTranslator cache, as-of.
CREATE INDEX translations_load_idx
    ON survey.translations (survey_id, language, effective_from, effective_to)
    INCLUDE (element_key, field, source_hash);

GRANT SELECT ON TABLE survey.translations TO ${survey_user};

ALTER TABLE survey.surveys
    ADD COLUMN base_language     varchar(35) NOT NULL DEFAULT 'en',
    ADD COLUMN content_languages varchar(255);

ALTER TABLE survey.answers
    ADD COLUMN display_text_local varchar(8000),
    ADD COLUMN display_language   varchar(35);
```

Points of the design:

- **Type 2, the same contract as the eight structural tables.** A translation is
  respondent-facing wording, and the reason the structural tables are Type 2 applies
  to it unchanged: a respondent who started under one wording must keep seeing it, and
  a later correction must not rewrite what earlier respondents saw. The durable
  `translation_id` is allocated per instance and links versions; `translation_key` is
  the cross-instance identity minted once in Author and preserved by import, export and
  Admin's update service; `version`, `effective_from`, `effective_to`, `published_by`
  and `published_comment` follow `questions` exactly, as do the `(durable id, version)`
  and `(key, version)` uniques, the one-current-row index and the
  `scd_close_predecessor` trigger (whose final form is V013). A second partial unique
  index, `translations_target_current_un`, guarantees one current row per
  `(survey_id, element_key, field, language)`, which is what the runtime and the
  Translations view look up by.
- **As-of resolution.** The runtime already pins every respondent to
  `respondents.first_access_dt` (`QuestionManager.resolveAsOf`, `:145-148`) and reads
  structural rows with `effective_from <= :asOf AND effective_to > :asOf`. Translations
  resolve the same way, so a respondent pinned to version 2 of a question sees the
  translation that was current at their first access, and a translation corrected after
  that never reaches them. A new respondent gets the current translation.
- **Removal is a closed `effective_to`**, never a delete and never an empty value
  (`value` is `NOT NULL`). Author retires a translation exactly as it retires a
  question (`ElementService.remove`), the file carries the closed `effective_to`, and
  Admin's existing retired-row handling closes the deployed row. Restoring an element
  in Author restores its translations retired at the same instant, as `restore` already
  does for sibling rows.
- **Retiring the base-language element retires every translation of it.** When a
  question, step, section, select item, relationship or report is retired, all current
  translation rows with that `element_key` are closed at the same instant, in the same
  transaction, exactly as `ElementService.remove` already closes the element's
  `sections_questions`, `steps_sections` and touching relationships (`:175-195`). A
  translation row is therefore never current while its owner is retired, and no reader
  needs an "is the owner retired" check. Restore reopens the translations closed at that
  instant together with the element, as `restore` (`:279-290`) already does for the
  sibling rows. The file carries the closed instants, and Admin closes the deployed
  rows through its existing retired-row path; as a belt-and-braces measure Admin's
  `retireCurrentRow` for a structural element also closes any still-current translation
  of that `element_key`, so a hand-edited file cannot leave a translation current under
  a retired owner.
- **Editing the base-language element does not touch its translations.** A rewrite of
  `questions.text`, a rename of a step, a changed option label: the translation rows
  stay current and unversioned. What changes is that their `source_hash` no longer
  matches, so they are stale. Retire and edit are deliberately different: retirement is
  a structural decision that applies to every language, while an edit is a wording
  decision that the translator has yet to act on. Because a stale translation may no
  longer ask the same question as the new base text, Author must say so at the moment
  of the edit (section 5.4) and the runtime must not serve it (below).
- **Stale translations.** When Admin versions a question the new row keeps
  `question_key`, so the current translation still attaches to it, but its
  `source_hash` no longer matches. Runtime policy: a stale translation is not served;
  the base text is. A rewritten clinical question shown with its pre-rewrite
  translation is worse than English. A config flag
  `elicit.content.serve-stale-translations` (default false) can relax this for a
  deployment that prefers continuity. Because respondents are pinned as-of, a
  respondent who started before the rewrite is unaffected either way: their question
  row and their translation row both resolve to the earlier versions.
- **Hash rule**, shared by SQL and Java: lower-case hex SHA-256 of the UTF-8 bytes of the
  exact stored base string, no trim. In PostgreSQL:
  `encode(sha256(convert_to(q.short_text, 'UTF8')), 'hex')`. In Java:
  `HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(UTF_8)))`.
  One `ContentHash` utility per module and one shared test vector.
- **Grants.** `survey_user`: SELECT on the table; the new columns on `surveys` and
  `answers` are covered by the existing table grants. `surveyadmin_user`: an Admin
  migration `V0.0.19__Add_Translation_Grants.sql` with SELECT, INSERT, UPDATE on the
  table and usage on the sequence, in the pattern of V0.0.8 and V0.0.16.
  `surveyreport_user`: nothing; reporting stays base-language.
- **Placement.** V017 in both tracks rather than folding into V001 and V010. V011 to
  V016 are the team's own pre-release precedent for append-not-edit; editing V001 changes
  its checksum on every developer and CI database; and the Author mirror and Admin
  bootstrap change as one new file each. An optional pre-release squash of V010 to V017
  can follow the feature if fewer files are wanted.
- **Hibernate.** In Author, `model/Translation.java` extends `StructuralElement` like the
  other eight (`getDurableId` on `translation_id`, `getElementKey` on `translation_key`,
  `durableSequence()` returning `survey.translations_durable_seq`), so `@PrePersist
  ensureElementKey()`, `isCurrent()`, `isRetired()` and `ElementService`'s retire and
  restore apply without special cases. In Survey it is a plain `PanacheEntityBase` with
  the same columns and an as-of finder. No `@OneToMany Map<String, Translation>` on any
  entity: that recreates the 2020 N+1 and pins detached entities to a language. Survey's entities gain read-only mappings of their element
  keys (`@Column(name = "question_key", insertable = false, updatable = false) public
  UUID questionKey`, and likewise `stepKey`, `sectionKey`, `selectItemKey`,
  `relationshipKey`, `reportKey`, `surveyKey`); Author's already map them.

### 3.3 Indexing

The table must answer five kinds of query quickly, and each has one index. `element_type`
is validation metadata and appears in no index; `value` is never included in an index
because a B-tree entry is limited to about 2.7 KB and translated question text can be
8,000 characters.

| Query | Shape | Index |
| --- | --- | --- |
| Runtime bulk load into the `ContentTranslator` cache, once per survey per language per TTL | `WHERE survey_id = ? AND language = ? AND effective_from <= ? AND effective_to > ?` | `translations_load_idx (survey_id, language, effective_from, effective_to) INCLUDE (element_key, field, source_hash)`. The equality prefix narrows to one survey and language, the range on `effective_from` walks the rest, and the INCLUDE columns let the planner answer the hash check without touching the heap; only `value` is fetched from the table. |
| Runtime point lookup and the review SQL join, as-of the respondent's first access | `WHERE element_key = ? AND field = ? AND language = ? AND effective_from <= ? AND effective_to > ?` | `translations_lookup_idx (element_key, field, language, effective_from, effective_to)`. `element_key` is a UUID unique across surveys, so `survey_id` adds nothing here. The review SQL joins on `q.question_key` and `i.select_item_key` with constants for `field` and `:language`, which is exactly this index's prefix. |
| Author's Translations grid and `TranslationService.upsert`, current rows only | `WHERE survey_id = ? AND language = ? AND effective_to = sentinel`, or the full four-column target | `translations_target_current_un (survey_id, language, element_key, field) WHERE effective_to = sentinel`. Partial, so it holds only current rows and stays small however many versions accumulate; it doubles as the integrity constraint that there is one current row per target. |
| Admin update matching the current row by key | `WHERE translation_key = ? AND effective_to = sentinel` | `translations_key_current_idx (translation_key) WHERE effective_to = sentinel`. Partial for the same reason. Historical versions are reached through the `(translation_key, version)` unique. |
| The `scd_close_predecessor` trigger closing the previous version on insert | `WHERE translation_id = ? AND effective_to = sentinel` | `translations_one_current_un (translation_id) WHERE effective_to = sentinel`, as on the other eight tables. |

Two rules keep the queries on those indexes. Every runtime query must lead with the
equality columns the index leads with and must express the as-of test as
`effective_from <= :asOf AND effective_to > :asOf` with a bound parameter, never with a
function over the column. And the stale check belongs in Java against the cached
`source_hash`, not in SQL: computing `sha256(convert_to(q.short_text, 'UTF8'))` per row
in the review query is cheap for one respondent's answers but it is work the cache has
already done; the SQL form in section 4.3 is shown for completeness and should be
replaced by the Java comparison if the review page ever shows in profiles.

The hot path in practice is not the database at all. `ContentTranslator` loads a
survey's translations once and serves every render from memory, so the runtime touches
`survey.translations` once per survey per TTL plus the review join. The Type 2 columns
add no cost to that: the cache holds every version and resolves as-of in memory, and
version rows only accumulate when Admin publishes a changed translation, which is rare
relative to reads. If a long-lived deployment does accumulate many versions, the load
query can drop rows whose `effective_to` predates the oldest unfinished respondent's
`first_access_dt`, which the same index serves.

## 4. Survey runtime

### 4.1 `ContentTranslator`

A new `@ApplicationScoped` bean in `com.elicitsoftware.i18n`, injected into
`QuestionManager`, `QuestionService`, the views and the `flow/input` widgets.

- `String language(Survey survey)`: resolves `Translations.currentLocale()` against
  the intersection of `survey.contentLanguages` and the chrome languages mounted at
  this site (`ElicitI18NProvider.getProvidedLocales()`), with the same
  exact-then-language chain as `LocaleSelection.resolve`; returns `null` when the
  effective language is the survey's `baseLanguage`, is not one of its content
  languages, or is not mounted for the chrome here (section 11.2). This is the only
  place the locale is consulted for content.
- A per-survey cache holding every version of every language of that survey
  (`SELECT element_key, field, language, value, source_hash, effective_from,
  effective_to FROM survey.translations WHERE survey_id = ?`), loaded on first use and
  expiring after `elicit.content.translations-ttl` (default five minutes) so an Admin
  publish surfaces without a restart. Lookups resolve as-of in memory. This is at most
  thousands of small rows per survey; if a long-lived survey accumulates many versions,
  the query can drop rows closed before the oldest unfinished respondent's first access.
- `Optional<String> get(String elementType, UUID elementKey, String field, String
  baseText, OffsetDateTime asOf)`: returns the value only when a language applies, a
  row is effective at `asOf` (`effective_from <= asOf < effective_to`, the same
  predicate the structural finders use), and `ContentHash.of(baseText)` equals
  `source_hash` (or the serve-stale flag is on). `QuestionManager` passes
  `resolveAsOf(respondentId)`; the views pass the as-of held in the session's
  `NavResponse`.
- Typed conveniences used by the render sites: `text(Question)`, `shortText(Question)`,
  `toolTip(Question)`, `placeholder(Question)`, `validationText(Question)`,
  `displayText(SelectItem)`, `name(Step)`, `name(Section)`, `title(Survey)`,
  `description(Survey)`, `name(ReportDefinition)`,
  `defaultUpstreamValue(Relationship)`. Each returns the base field when nothing applies.
- Off the UI thread `currentLocale()` yields English, `language()` yields `null`, and
  every accessor returns the base text. The ETL, migrator and tests see no change.

Why a service and not `@PostLoad` with `@Transient` fields: overwriting a mapped field on
a managed entity inside `QuestionService.init`'s `REQUIRES_NEW` transaction is a silent
write of translated text into `questions.text` at flush (`QuestionManager` calls
`persistAndFlush` on answers in the same transaction, `:809`). Transient projections
avoid that but still run once per entity with a lazy select each, bake the load-time
language into entities that outlive a language switch, and put UI-thread logic into
classes shared with the ETL and mirrored in Author, which must never localize. The
service keeps the entities pure, is testable without Vaadin, and is one query per
survey.

### 4.2 Where the language comes from; no column on the respondent

The session locale drives content exactly as it drives chrome. No `respondents.language`
column: nothing needs it (the nav and review SQL take `:language` from the session, and
each answer records the language it was rendered in), and pre-selecting a language on
the next visit is what UC-009 BR-007 forbids. BR-007 should be amended to say the choice
is held for the browser session and never used to pre-select a language later, while the
rendering language of each answer label is recorded with the answer for fidelity, not
preference.

### 4.3 `answers.display_text`

| | (i) Base snapshot plus local copy | (ii) Base only, resolve at render | (iii) Per-language answer text table |
| --- | --- | --- | --- |
| Storage | Two columns on `answers` | None | New table, one row per answer per language |
| Render cost | None | `getValuesMap` per answer per page draw (several queries each) | None |
| Nav and review SQL | `CASE` on the local column | Must leave SQL and be assembled in Java | `LEFT JOIN` on language |
| Language switch mid-survey | Re-render active answers once | Free | Re-render or keep all |
| Record of what the respondent saw | Yes | No | Yes |
| Reporting, ETL, Admin | Untouched | Untouched | Untouched |

(i) is chosen. A respondent is in one language at a time, so (iii)'s extra rows buy
nothing, and (ii) re-derives on every page draw what `buildDipslayText` already computed
once.

Concretely:

- `buildDipslayText` computes `values = getValuesMap(answer)` once and writes
  `answer.displayText = replaceTokens(baseTemplate, values)` exactly as today. Then
  `String lang = translator.language(survey)`. When `lang` is null, `displayTextLocal`
  and `displayLanguage` are set to null. Otherwise the local template is
  `translator.text(question)`, `translator.name(section)` or `translator.name(step)`
  (falling back to the base template when no translation applies), and
  `answer.displayTextLocal = replaceTokens(localTemplate, localizeValues(values))`,
  `displayLanguage = lang`. `localizeValues` passes each value whose source was
  `relationship.defaultUpstreamValue` through `translator.defaultUpstreamValue(...)`;
  free-text and coded values pass through unchanged. That needs `getKeyValues` to return
  the source relationship beside each value, a small record instead of a `TreeMap`.
- The presets at `:516` and `:802-806` keep writing base text; `buildDipslayText` runs
  immediately after in both paths, so nothing else changes there.
- **Language switch.** `LocaleSelection.apply` (`LocaleSelection.java:56-63`) gains a
  hook: if `UISessionDataService` holds a respondent, call
  `questionService.relocalize(respondentId)` (`REQUIRES_NEW`), which re-runs
  `buildDipslayText` and `persistAndFlush` over the respondent's active answers, the
  same loop shape as `:797-809`. One `UPDATE` per answer on a rare event.
- **Nav SQL** (`QuestionManager.java:1817`): select
  `CASE WHEN a.display_language = :language THEN a.display_text_local ELSE a.display_text END`
  with `:language` bound from `translator.language()` (empty string for base). After
  `relocalize` the two columns always agree; the `CASE` is defensive.
- **Review SQL** (`QuestionService.java:77-110`): the same `CASE` for `a.display_text`;
  `q.short_text` becomes `COALESCE(ts.value, q.short_text)` with
  `LEFT JOIN survey.translations ts ON ts.survey_id = q.survey_id AND ts.element_key =
  q.question_key AND ts.field = 'short_text' AND ts.language = :language AND
  ts.source_hash = encode(sha256(convert_to(q.short_text, 'UTF8')), 'hex')`; and
  `i.display_text` becomes `COALESCE(ti.value, i.display_text)` joined the same way on
  `i.select_item_key`. `review()` (`:164-210`) binds `:language`.
- **Widgets.** A helper `Answer.label()` returning `displayTextLocal` when non-null,
  else `displayText`, replaces `answer.displayText` at every label site (the sixteen
  `flow/input` classes, `ElicitHtml.java:35`, `SectionView.java:176`). Tooltip,
  validation message and placeholder (`ElicitComponent.java:95-104`) go through
  `translator.toolTip(q)` and friends. Option labels (`ElicitComboBox.java:64`, `:106`,
  and the radio, checkbox-group and multi-select twins) use
  `translator.displayText(item)`. `AboutView.java:54` uses `translator.title(survey)` and
  `translator.description(survey)` (and should show `title`, not `name`). `ReportView`
  card titles use `translator.name(report)`.
- **Reporting.** `fact_sections.name` keeps reading `steps.name`; `dim_step` and
  `dim_section` keep reading `dimension_name`. Nothing in the ETL changes.
- **PDF and reports.** The report body comes from an external service called with only
  the respondent id (`ReportView.java:168-174`). Extend `ReportRequest` with a nullable
  `language` so the service may localize; the contract on the service side is out of
  scope here.
- The `data-i18n-content` attribute stays on every content render site; nothing new
  is chrome, so `DisplayedStringsSweepTest` is unaffected.

## 5. Author

### 5.1 Translatable fields

A whitelist, canonical in Admin's `SurveyDefinitionFileFields` (C-015 says Author ports
Admin's logic), copied into Author as `author/survey/TranslatableFields.java` and into
Survey, with a test that the three agree and that they match the CHECK constraint.

| `element_type` / key | Fields | Not translated, and why |
| --- | --- | --- |
| `surveys` / `survey_key` | `title`, `description` | `name` is an identifier used in file names and Admin lists |
| `steps` / `step_key` | `name`, `description` | `dimension_name` is the reporting dimension |
| `sections` / `section_key` | `name`, `description` | `dimension_name` |
| `questions` / `question_key` | `text`, `short_text`, `tool_tip`, `placeholder`, `validation_text` | `default_value` is written into `answers.text_value` (`QuestionManager.java:516`) and analysed, so translating it would fork stored data by language; `mask`, `variant`, `sample` |
| `select_items` / `select_item_key` | `display_text` | `coded_value` |
| `relationships` / `relationship_key` | `default_upstream_value` | `override_upstream_value` has no runtime reader; `token`, `reference_value`, `description` |
| `reports` / `report_key` | `name`, `description` | `url` |

Nothing on `select_groups` (author-facing only), `post_survey_actions`, `ontology`,
`dimensions` or `metadata`; tags and dimension names are SQL identifiers
(`ReportingNames`).

### 5.2 Survey-level languages

`surveys.base_language` (default `en`, edited in `SurveyMetadataDialog` next to the
existing `name`/`title`/`description` bindings at `SurveyMetadataDialog.java:65-70`) and
`surveys.content_languages`, the comma-separated set the author has marked publishable.
The runtime offers content only in those that are also mounted for the chrome at the
site (section 11.2). Deriving the set from rows present was
rejected because a half-translated language must be holdable back; a normalized
`survey_languages` table was rejected as another record type and Admin case for a short
list of tags.

### 5.3 A Translations view rather than tabs in the dialogs

`author/flow/TranslationsView.java`, route `survey/:surveyId/translations`, linked from
`SurveyEditorView` and the main layout. A `Grid` over `TranslationRow` with columns:
element (step > section > question label, or list > option), field, base text
(read-only, full text in a tooltip), translation (an inline `TextArea` for the selected
language), and status: translated, missing, or stale with the `source_text` diff in a
popover. A language selector, filters for missing and stale, and counts. Rows come from
`TranslationService.rows(surveyId, language)`, built from the current structural rows
(`effective_to = SENTINEL`) left-joined to `translations`. Edits go through
`TranslationService.upsert(surveyId, elementType, elementKey, field, language, value)`,
which computes `source_hash` and `source_text` from the current base text. The grid is
for reviewing and fixing single strings; the bulk path is the hand-off file in
section 5.5.

Why not language tabs in `QuestionDialog`, `ElementDialog`, `SelectGroupDialog`,
`RelationshipDialog` and `SurveyMetadataDialog`: each is a `Binder` over a detached
working bean with a caller-supplied persist function (`QuestionDialog.java:85`,
`:170-185`; `ElementDialog.java:91-93`; `SelectGroupDialog.java:59-98` with its
hand-rolled `OptionRow`s and `OptionDraft`), so per-language fields mean a second bean
and a second persist path in five dialogs, and a translator working through a whole
survey would have to open every dialog. A one-line read-only hint in each dialog
("Translations: es-419 done, ar stale") is a cheap later addition.

### 5.4 Retire, restore, rename

`ElementService.remove` (`:169-196`) and `removeSelectGroup`/`removeSelectItem`
gain one more cascade: every current `Translation` whose `element_key` is the
retired element's key gets the same `effectiveTo = now`. `restore` (`:279-290`) reopens
the translations that share the closing instant, in the same query shape it uses for
`sections_questions`. The Translations view therefore never shows a retired element's
strings, and the exporter writes the retired translation rows with their closed
instants so Admin closes them too. Removing a single translation (an author decides a language should fall back to base
for one string) is `ElementService.remove` on the `Translation` row: `effective_to` is
closed, the file carries the closed instant, and Admin closes the deployed row. There
is no "clear" that leaves an empty current row. Editing a translation's wording in
Author overwrites the working copy in place, as every other edit does there; the
version boundary is created by Admin at publish time.

Editing base text, including the rename that `ElementDialog`'s retroactivity guard
(`:134`) already warns about, makes the hash mismatch: the row shows as stale in the
view and is not served at runtime. The author must be told at the moment they make the
change, because a translation that was faithful to the old wording may now ask a
different question. Each editor that saves a translatable field (`QuestionDialog`,
`ElementDialog`, `SelectGroupDialog`, `RelationshipDialog`, `SurveyMetadataDialog`)
compares the field's old and new values on save; when they differ and current
translations of that field exist, it shows a persistent notification: "The wording of
this question changed. Its 2 translations (es-419, ar) are now out of date and will not
be shown to respondents until they are translated again, so that every language asks
the same question." with a link to the Translations view filtered to that element. The
save itself is not blocked; the rename guard's acknowledgement pattern is reused only
for the step and section rename it already covers. The survey editor shows a stale
count beside the Translations link, and `ExportValidation.validate` (`:63`) gains
warnings, not errors: "N stale translations in <language>" and "<language> is enabled
but M strings are missing", so an author exporting with stale rows is reminded once
more before the file leaves Author. Author itself never localizes; `Translation` there is a plain entity.

### 5.5 A hand-off file for a translator or an AI, and its import

Nobody will translate a survey one grid cell at a time. Author needs to produce one
file per survey and target language that can be handed, as is, to a translator or an
AI agent, and to read the same file back. The chrome already has this shape for the
tool's own strings (`i18n/TRANSLATION_REQUEST.md`, FR-046, generated by
`TranslationRequestGenerator`): an instructions preamble, a glossary, rules, and the
strings with their context. The content file follows the same idea with one difference:
the chrome package is Markdown and the translator returns a `.properties` file, whereas
the content file must round-trip by itself, so it is JSON and the translator returns the
same document with one field filled in.

**Export.** From the Translations view, "Request translation" for a target language
writes `<survey name>_<tag>.translation.json`, either every string or only the missing
and stale ones. Its shape:

```json
{
  "format": "ELICIT_CONTENT_TRANSLATION_V1",
  "survey_key": "b3a7c2e4-5d18-4f92-8a60-1c9e3f75d2b4",
  "survey_name": "Family History Survey",
  "base_language": "en",
  "language": "es-419",
  "generated": "2026-09-20T15:04:00-04:00",
  "instructions": "…the preamble, glossary and rules below, as plain text for the agent…",
  "items": [
    {
      "id": "6f1c…:text",
      "element_type": "questions",
      "element_key": "6f1c8a2e-…",
      "field": "text",
      "context": "Step 4 Family › Section Mother › Question 2 (RADIO, options: Yes / No / I don't know)",
      "max_length": 8000,
      "flags": ["html", "tokens"],
      "tokens": ["S1", "Q#"],
      "source": "Has {S1's mother|your mother} ever been diagnosed with cancer?",
      "source_hash": "9c1e…",
      "status": "stale",
      "previous_source": "Has {S1's mother|your mother} had cancer?",
      "current_translation": "¿{La madre de S1|Su madre} ha tenido cáncer?",
      "translation": ""
    }
  ]
}
```

The exporter fills everything except `translation`. `context` is the breadcrumb the
Translations view already computes plus what a translator needs to disambiguate: the
question type, its options for a select question, the step and section names. `tokens`
lists the token keys the string uses (`Tokens.names`), `flags` marks HTML fragments and
token use, `max_length` is the base column's length, `status` is missing, stale or
translated, and for a stale item `previous_source` and `current_translation` are the
text the existing translation was made from and the translation itself, so the agent
can carry over what is still right. `source_hash` travels so the import can tell whether
the base text moved again while the file was out.

**Instructions in the file**, mirroring the chrome package and adjusted for content:

- Translate `source` into `translation`; leave every other field exactly as it is. Do
  not add, remove or reorder items.
- A placeholder is written `{phrase containing a TOKEN|default text}`. The token is an
  upper-case key such as `S1`, `Q#` or `NAME` (the `tokens` list names them). Keep the
  key exactly as written; translate the phrase around it and the default text after the
  bar, moving the key where the language needs it. Keep the braces and the bar; never
  add or drop one. A translation must use exactly the tokens the source uses.
- Where `flags` contains `html`, keep the tags and translate only the text between them.
- Respect `max_length`.
- Option labels (`select_items` / `display_text`) must stay distinct from each other
  within the same question.
- The glossary: `Elicit`, survey, step, section, question, access code (never a token),
  and the survey-specific terms the author adds in the Translations view (a per-survey
  glossary list stored with the survey, exported into this preamble).
- Return the same JSON document, UTF-8, with `translation` filled in and a top-level
  `reviewed_by` and `reviewed_on` added. An empty `translation` means "not translated";
  it is skipped on import, not stored as blank.

**Import.** The Translations view accepts the returned file (`Upload`, `.json`, a size
cap like the `.elicit` importer's). `TranslationImportService` validates the envelope
(`format`, `survey_key` equal to the open survey's, `language` a content language or one
the author confirms adding), then each item:

| Check | Outcome |
| --- | --- |
| `element_key` unknown, or its element retired | Rejected, reported by `id` |
| `field` not in the whitelist for `element_type` | Rejected |
| `translation` blank | Skipped (left missing), counted |
| `source_hash` differs from the hash of the current base text | Imported, but reported as "base text changed since export"; the row is stale by hash and is not served until re-translated |
| Token set of `translation` differs from the token set of the current base text | Rejected, reported with both sets |
| Braces or bars unbalanced (`Tokens.PLACEHOLDER` fails to consume every `{`) | Rejected |
| Length over `max_length` | Rejected |
| HTML flag set and the tag multiset differs from the source's | Warned, imported |
| Two option labels of one question translated to the same text | Rejected for both |
| `translation` equals `current_translation` | Unchanged, counted |

Accepted items go through `TranslationService.upsert`, which writes the working copy
and recomputes `source_hash` from the current base text, so an import of a translation
made from unchanged base text lands as current and served; one made from since-changed
text lands stale. The import is one transaction for the accepted set; rejected items do
not block the rest, because a translator's file of eight hundred strings should not be
refused for one broken placeholder. The result dialog lists the counts (imported,
unchanged, skipped, stale, rejected) and every rejected `id` with its reason, and the
grid refreshes. The same file, re-exported after import with `status` mostly
"translated", is the proof-reading round.

**What travels where.** This file is Author-local. It never reaches Admin or Survey;
translations reach deployed sites only inside the `.elicit` file (section 6). The
per-survey glossary the author maintains for the preamble is Author-local too and is
not exported.

**Tests.** A round trip of the designer sample: export the file for `es-419`, fill every
`translation` programmatically, import, re-export, and assert every item is
"translated" with the hash of the current base text; the rejection table above as
parameterised cases; and a `DisplayedStringsSweepTest` pass over the upload dialog and
result dialog.

## 6. The `.elicit` file, redefined in place

The header stays `# ELICIT_SURVEY_EXPORT_V1`; no file with that header exists outside
this repository, so there is nothing to distinguish from. Two records change:

- `surveys` gains two trailing fields, required, arity 12:
  `source_id|survey_key|name|display_order|title|description|initial_display_key|post_survey_url|published_by|published_comment|base_language|content_languages`.
  `content_languages` is Author's publishable set, not a site's offered set; Admin's
  `applySurveyAttributes` updates it in place as it does `title`, and a site never edits
  it (section 11.2).
- A new record type, last in `TABLES` because it references every other table's keys,
  laid out like the other versioned records (durable id, key, content, then the Type 2
  tail `version|effective_from|effective_to|published_by|published_comment`):
  `translations: translation_id|translation_key|element_type|element_key|field|language|value|source_hash|version|effective_from|effective_to|published_by|published_comment`,
  arity 13. It joins `VERSIONED_TABLES`, and `EFFECTIVE_TO_INDEX["translations"] = 10`
  so `isRetired` reads the closed instant. `translation_id` is a local link only and is
  remapped on import like every other durable id; `translation_key` and `element_key`
  are preserved verbatim. `source_text` is not exported, on the same footing as
  `questions.sample` (FR-036). The `# translations: N` header line follows
  automatically from `TABLES`.

Changes by module, made as one change set and gated by the round-trip test and Admin's
service tests:

**Author.** `ElicitFormat`: `TABLES`, `REQUIRED_FIELDS` (`surveys` 12, `translations`
6), javadoc. `SurveyDefinitionExporter`: the survey query at `:58-59` adds the two
columns; a new `tables.put("translations", ...)` selecting the six fields ordered by
element type, key, field, language. `SurveyDefinitionImporter`: `insertSurvey` writes
the two columns; `case "translations" -> insertTranslation(fields, surveyId)` with a
strict key parse (a translation without a key is malformed; do not mint), `element_type`
and `field` validated against the whitelist, `source_hash` stored verbatim, and, since
translations follow every structural record, an optional check that the key was seen.
`SurveyDefinitionExporterTest.normalised` (`:52-90`): key `translations` rows by
`fields[1] + "|" + fields[2] + "|" + fields[3]` and strip no Type 2 tail from them.
`Author/samples/generate_elicit_designer.py` and `generate_fhhs_base.py`: the two
`surveys` fields and a handful of `translations` rows in `es-419`, so the round trip
exercises the record type; regenerate both `.elicit` samples.

**Admin.** `SurveyDefinitionFileFields`: the canonical translatable-field whitelist.
`SurveyDefinitionExportService`: the survey query at `:423`, the header count, and a
`getTranslations(surveyId)` writer that mirrors `getQuestions`' "current or last
version" predicate so retired elements' translations travel as the structural rows do.
`SurveyDefinitionImportService`: the survey insert and a `case "translations"` insert
under the new survey id, skipping retired rows as it does for the other versioned
tables. `SurveyDefinitionUpdateService`: `translations` in the table list at
`:196-200`; `applySurveyAttributes` compares and updates the two survey columns;
`case "translations" -> upsertTranslation`, a copy of `upsertQuestion` (`:842-935`):
find the current row by `translation_key`; none, insert version 0 (created); identical
`element_key`, `field`, `language`, `value` and `source_hash`, unchanged; otherwise
close the current row and insert `version + 1` under the same `translation_id`
(versioned); a file row marked retired closes the current row through the existing
`retireCurrentRow` (`:1277`). Never delete.
`SurveyDefinitionApplyService` and the REST resource are unchanged. The test bootstrap
SQL gains the table, sequence and four columns; fixture files are regenerated;
`SurveyDefinitionImportServiceTest`, `UpdateServiceTest` (created, versioned, unchanged,
retired, stale hash carried verbatim), `ApplyServiceTest` and the resource tests follow.
`V0.0.19__Add_Translation_Grants.sql` as in section 3.2.

## 7. Admin semantics in one paragraph

Translations ride the same apply pipeline as the eight structural tables. They are
matched by `translation_key`, inserted at version 0 when new, versioned (current row
closed, `version + 1` inserted under the same `translation_id`) when the value or hash
differs, left alone when identical, closed when the file marks them retired, never
deleted. Respondents already in progress keep the version that was current at their
first access; new respondents get the new one. A stale hash is carried verbatim, so a
deployed site shows the base text for that string to new respondents until Author
re-translates and re-publishes. When the file retires a structural element it also
carries that element's translations as retired, and Admin closes all of them; a
structural retirement additionally closes any current translation of that
`element_key` the file failed to mention. Versioning a structural element for a
wording change never versions or closes its translations.

## 8. Tests

Will fail until updated: `ManualSchemaMigratorUpgradeTest` (V017 in both tracks),
`SchemaMirrorFreshnessTest` (sync the mirror), Author's `EntityMappingSmokeTest` (add
`Translation`), `SurveyDefinitionExporterTest` and `SurveyDefinitionImporterTest`
(arity, counts, normaliser, regenerated samples), Admin's four definition-service tests
and the resource tests (bootstrap SQL, fixtures).

To add: `ContentTranslatorTest` (fallback to base, stale not served, tag resolution
against `content_languages`, as-of picks the version effective at first access,
off-thread returns base); an SCD spec test for `translations` in the style of
`scd/DimStepSectionRekeySpecTest` (one current row per target, trigger closes the
predecessor, `translation_id` stable across versions); `QuestionManager` tests for
`display_text_local` and `display_language`, including a `defaultUpstreamValue` token;
nav and review tests bound to `:language`; a `relocalize` test; Author
`TranslationServiceTest` (hash, stale, edit leaves translations current) and
`ElementServiceTest` cases that retiring a question, section, step or select item closes
its translations at the same instant and restore reopens them; an Admin update test
that a retired structural row closes its translations even when the file omits them,
and that a versioned question leaves its translations untouched; a respondent
export/import round trip (`RespondentExportService`, `RespondentImportService`)
asserting that `display_text_local` and `display_language` travel and that answers
resolve to the destination's question rows by key (section 11.4); the validation
warnings; the shared hash test vector across the three modules; `DisplayedStringsSweepTest`
and the bundle tests must pass over `TranslationsView` and any new chrome strings.

## 9. Documents to amend

Survey: `docs/requirements.md` C-014 rewritten ("Survey content is stored in the
survey's base language; other languages come from `survey.translations`, authored in
Author, delivered in the definition file, and shown when the respondent's language is
one of the survey's content languages; a stale translation falls back to the base
language"), a new FR for localized survey content under UC-009, and an NFR on fallback.
UC-009: BR-005 becomes "content appears in the respondent's language when a current
translation exists, otherwise in the base language"; BR-007 as in section 4.2; a new
rule that stale translations are not shown. `docs/entity_model.md`: a TRANSLATION entity,
`SURVEY.baseLanguage`/`contentLanguages`, `ANSWER.displayTextLocal`/`displayLanguage`,
and the element-key mappings. `research/Kimball_type_2.md`: `translations` joins the
list of Type 2 tables, keyed to its owner by `element_key` rather than by a durable-id
companion column. The umbrella `docs/I18N_IMPLEMENTATION_GUIDE.md` and
`CLAUDE.md` sentence "Survey content in the database is not translated by this
mechanism" once the feature lands.

Author: C-021 rewritten; C-015 names the redefined record set; new FRs and use cases
"Translate survey content", "Manage content languages", "Request a content
translation" (the hand-off file) and "Import content translations", modelled on FR-046
(plus `use_cases.puml`); NFR-011 mentions the `translations` record; FR-036 cited as the
`source_text` precedent; UC-034 BR-005 rewritten; `docs/entity_model.md` and
`docs/scd-contract.md` (translations are the ninth Type 2 table; Author holds one
version-0 row per translation, retires by closing `effective_to`, and Admin creates
the versions). The
`translations.context.properties` sidecar in all three modules gains the glossary terms
(`base language`, `content language`, `stale`) so the hand-off package explains the new
chrome strings.

Admin: UC-014 and UC-017 note the new record type and the upsert semantics. UC-011
and UC-012 gain the `display_text_local` and `display_language` fields and the key
columns of section 11.4, at which point BR-058 and BR-059 describe what the code does.

## 10. Phased delivery and risks

1. **Schema.** V017 in both Survey tracks; sync the Author mirror; Admin `V0.0.19` and
   its test bootstrap; `Translation` entity in Survey and Author; read-only element-key
   mappings on Survey's entities; the two `Answer` fields. No behaviour change. Caught if
   half-done by `ManualSchemaMigratorUpgradeTest` and `SchemaMirrorFreshnessTest`.
2. **File.** `ElicitFormat`, exporter, importer, Admin's three services and file
   fields, the Python generators, regenerated samples and fixtures, the round-trip
   normaliser, all in one change set. Before the runtime, so translated test surveys can
   be seeded through the real pipeline.
3. **Survey runtime.** `ContentTranslator`, `buildDipslayText`, nav and review SQL, the
   widgets, About and Report, `relocalize` on language switch, `ReportRequest.language`.
4. **Author editing.** `TranslationService`, `TranslationsView`, language management in
   `SurveyMetadataDialog`, validation warnings, dialog hints; CSV hand-off later.
5. **Docs**, trailing each phase.

Risks: someone assigning translated text to a mapped entity field (review rule: only
`Answer.displayTextLocal` is ever written with translated text); the translator cache
lagging an Admin publish (the TTL); a lookup that forgets the as-of predicate and
serves the current translation to a pinned respondent (the `ContentTranslator` API
takes `asOf` as a required argument, not an optional one); the SQL and Java hashes drifting (the shared test
vector); `replaceTokens`' English possessive handling (`QuestionManager.java:99-101`)
applied to non-English templates, which is a known wart to document rather than fix
here; and the whitelist drifting across three modules (one canonical constant and a
comparison test).

## 11. Multi-site operation

Elicit runs the same survey at several sites, and translation raises three questions
about that: does the `.elicit` file still move cleanly when sites hold different
language sets, do a respondent's answers still import into a central instance for
review, and how does an update reach every site. The design already works this way,
provided one rule is added to the runtime (11.2) and one existing defect in the
respondent file is fixed (11.4).

### 11.1 One writer, many appliers

The definition pipeline is already a master and its replicas. Author holds one working
copy per `survey_key` and refuses a second (`SurveyDefinitionImporter.java:182`, UC-005
BR-002); Admin has no definition-editing surface, only export, import, update and apply
(UC-013, UC-014, UC-017, UC-018); a site can change nothing about a survey except by
applying a file, and the only attribute the update service treats as site-owned is
`display_order` (`SurveyDefinitionUpdateService.java:512-517`). Translations inherit
that: the instance that runs Author is the only place a translation is written, and
every other site receives it.

A site that wants its language therefore requests it rather than making it, and the
section 5.5 hand-off file is the vehicle. The master exports
`<survey>_ja.translation.json` from the Translations view and sends it; the requesting
site's native speakers fill in `translation`, with no Elicit software involved; the
file comes back; the master imports it with the per-item validation and exports the
survey. The requesting site's chrome, its buttons and labels, is a separate request:
the existing `TRANSLATION_REQUEST.md` package returns a `.properties` file that is
mounted at that site under `elicit-i18n/<app>/` and never travels through Author. One
request from a site thus produces two deliverables for two different places, content
into Author and chrome onto that site's mount, and 11.2 is what ties them together.

### 11.2 Keeping a language away from a site that has not adopted it

Suppose Japan has requested and verified `ja` and Mexico has not. Two designs were
weighed.

Per-site files: Author exports one file for Japan with the `ja` rows and one for
Mexico without them. Author's exporter dumps every row of the survey today
(`SurveyDefinitionExporter.java:58-83`), so this needs a language filter on export, and
it weakens two things the format relies on. Every export mints a fresh
`survey_revision` (`:99`) and the format's contract is that one revision is one file
distributed to every site (`SurveyDefinitionExportService.java:72-87`, Author UC-008
BR-001); per-site exports make the revision meaningless across sites, and Author
records nothing about which file went where, since only each site's `survey.survey_log`
knows what it applied. And the update service leaves rows a file does not mention alone
(UC-017 BR-068) but overwrites survey attributes in place
(`SurveyDefinitionUpdateService.java:518-551`), so the Mexico file applied at Japan by
mistake would keep Japan's `ja` rows and silently drop `ja` from `content_languages`,
turning Japanese off until the right file is re-applied.

One file and a site rule, which is adopted: every site receives the same file with
every language in it, and the runtime offers a content language only when it is both
in the survey's `content_languages` and mounted for the chrome at that site.
`ContentTranslator.language()` (section 4.1) intersects `survey.contentLanguages` with
`ElicitI18NProvider.getProvidedLocales()` (`ElicitI18NProvider.java:97`, the list the
`LanguageSwitcher` already builds from at `LanguageSwitcher.java:37`). Mexico holds the
`ja` rows and never serves them, because nothing on its mount is Japanese; Japan serves
them because its mount is. This is exactly "a site's users get what is on that site's
mount", with no export filter and no file fork, and it settles the earlier question of
whether the switcher should show the union or the intersection of the two language
sets: the intersection, so a content language without its chrome is never offered and
no respondent sees Japanese questions between English buttons. `content_languages` in
the file is Author's publishable set; the offered set is a deployment concern like
`display_order`, decided by what is mounted.

Nothing here needs a compatibility layer. Author and the V3 branches are unreleased and
no definition or respondent file exists in the field (the respondent exporter shipped
in Admin 2.x but was never used), so both formats are redefined in place under their
existing headers (section 6 and 11.4) and every site runs the same Admin build.

### 11.3 Wording changes across sites

Type 2 `version` numbers are per site: a site that joins late installs the same content
at version 0, as the export javadoc says (`SurveyDefinitionExportService.java:72-87`).
`source_hash` travels verbatim and every site's structural rows come from the same
file, so a translation that is stale at the master is stale at every site and none of
them serves it. Respondents already in progress at each site keep the version effective
at their first access under that site's own `effective_from`, so a Japanese respondent
who started before a rewrite keeps seeing the old question and its old translation.

The cost of the model is latency after a wording change. When the master rewrites a
question, its Japanese translation goes stale everywhere, and new Japanese respondents
at Japan see the base text for that question until the loop in 11.1 runs again: the
master exports the stale-only hand-off file, Japan fills it in, the master imports it
and exports the survey, Japan applies it. Two round trips per change, owned by the
master; the notification at edit time and the export warnings in section 5.4 are what
make it visible. A site cannot shorten the loop by fixing a translation locally, because
Admin cannot edit, and a local row would never reach Author and would be orphaned by
the next file. That restriction is deliberate and should stay.

### 11.4 Respondent answers across sites

Respondent data moves in a different file from the definition, `ELICIT_EXPORT_V1`, one
respondent per file, written by `RespondentExportService` (`:106-214`) and read by
`RespondentImportService` (`:300-354`; Admin UC-011 and UC-012). Its `answers:` record
carries `display_text` and `text_value`. Under this design `display_text` keeps the
base language (section 4.3) and `text_value` holds the untranslated `coded_value` for
select questions, so a file from a Japanese respondent has the same shape as one from a
US respondent, and a central instance reviewing it needs no `ja` rows at all: the review
SQL binds the reviewer's own session language and falls back to the base text
(section 4.3). Free-text answers are in whatever language the respondent typed; that is
unavoidable and reviewers should expect it. So that a reviewer can also see what the
respondent actually saw, the `answers:` record gains two required trailing fields,
`display_text_local|display_language`, which the importer writes through; the file is
redefined in place under its existing header.

What does break cross-site import is older than this design and independent of it.
UC-011 BR-058 and UC-012 BR-059 say the respondent file identifies the survey by its
stable key and that a numeric identifier from the source database is never trusted. The
code does the opposite: the header and every row carry the numeric `survey_id`
(`RespondentExportService.java:108, 122, 134`), `question_id` and
`section_question_id` are the source database's surrogate row ids (`:142-143`), and the
importer binds all three verbatim with no lookup (`RespondentImportService.java:314,
338-346`). `survey.answers` has no `question_key` column to carry
(`V001__Create_Survey_Schema.sql:660-702`). At a destination database the insert either
violates `answers_questions_fk` or attaches the answer to whichever question row
happens to own that id; the leading survey-id component of `display_key` is not rebased,
although the definition importer rebases it (`SurveyDefinitionImportService.java:523,
739`); and the ETL never runs for an imported respondent and hard-codes `survey_id = 1`
(`etl/Sql.java:277, 314`), so reports on it come back empty.

The fix is a redefinition, since no file exists in the field: the respondent file
carries `survey_key`, `question_key`, `sections_question_key` and `question_version` in
place of the numeric ids, the importer resolves the survey by `survey_key` (as BR-059
says) and each answer to the destination's question row by key, and `display_key` is
rebased as the definition importer does. Which question row that is, when the Type 2
version pin does not transfer between sites (11.3), is an open question in section 12.
The item is separate from i18n and should be scheduled on its own; the translation
design neither depends on it nor makes it worse.

## 12. Open questions

- Should `serve-stale-translations` default to false as proposed, or should a
  deployment see the stale translation with no visible marker? The proposal favours
  correctness over continuity.
- When a respondent file from one site is imported at another, which question row does
  an answer attach to, given that Type 2 version numbers are local to each site
  (section 11.4)? The candidate rule is: the row with the file's `question_version`
  where the histories agree, otherwise the row effective at the source
  `first_access_dt`, otherwise the current row.
- Should a free-text answer record the language it was typed in, so a central reviewer
  can tell Japanese free text from English without reading it? `display_language` on
  the answer row already says which language the question was shown in and is a
  reasonable proxy.
- What does the external report service need to localize its body, beyond a language
  tag on the request?
- `AboutView` shows `survey.name` where `title` is meant; fix in passing.
- Whether to squash V010 to V017 into V001 and V010 before the first V3 release.
