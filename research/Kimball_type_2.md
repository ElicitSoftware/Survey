# Kimball Type 2 Slowly Changing Dimensions for Survey Versioning

## Motivation

Researchers need to iterate on survey instruments over time — rephrasing questions for clarity,
renaming a section, adjusting conditional logic in relationships, or removing questions that
perform poorly. Today the schema has no versioning concept. Any in-place update silently
overwrites the definition that historical respondents saw.

**Goal**: Apply Kimball Type 2 SCD at the individual row level across **all structural tables**,
so that any element (question, section, step, relationship, answer option) can be independently
versioned without cloning the entire survey. A respondent's experience is pinned to a
point-in-time snapshot determined by `respondents.firstAccessDt`.

---

## Current Schema — Structural Dependency Chain

Understanding the FK chain is critical because versioning one table creates ripple effects
on every table that references it by surrogate `id`.

```
surveys
  │
  ├── steps              (survey_id)
  │     └──────────────────────────────────────────────────────┐
  ├── sections           (survey_id)                            │
  │     └────────────────────────────────────┐                  │
  ├── steps_sections     (survey_id, step_id ─┘, section_id ───┘)
  │     └── [referenced by relationships.downstream_ss_id]
  │
  ├── select_groups      (survey_id)
  │     └── select_items (survey_id, group_id)
  │
  ├── questions          (survey_id, select_group_id, type_id)
  │
  ├── sections_questions (survey_id, section_id, question_id)
  │     └── [referenced by relationships.upstream_sq_id / downstream_sq_id]
  │
  ├── relationships      (survey_id, upstream_step_id, upstream_sq_id,
  │                       downstream_step_id, downstream_ss_id, downstream_sq_id)
  │
  ├── respondents        (survey_id, token, first_access_dt, ...)   ← snapshot anchor
  │
  └── answers            (survey_id, respondent_id, question_id,    ← event record
                          section_question_id, display_key, text_value)
```

**The FK cascade problem**: if a question gets a new surrogate `id` (its new version row),
every table that referenced the old `id` — `sections_questions`, then `relationships` — must
also be updated or re-pointed. The same cascades when a section or step is versioned. This
is the core challenge in applying per-row Type 2 SCD.

---

## Core Design: Integer Version Keys + Type 2 Columns on Every Structural Table

### The Two-Key Pattern

Every structural table gets **two identifiers**:

| Column | Purpose |
|---|---|
| `id` (existing) | **Surrogate key** — unique per physical row/version; used by `answers` to pin the exact version seen at response time |
| `{entity}_id` (new, INTEGER sequence) | **Durable key** — stable across all versions of the same logical entity; used by join tables to avoid cascade updates |

And **three Type 2 columns** added to each structural table:

| Column | Type | Notes |
|---|---|---|
| `version` | `INTEGER` | Starts at 0; incremented n+1 for each new version row of the same logical entity |
| `effective_from` | `TIMESTAMPTZ` | When this version became active; `1970-01-01 00:00:00+00` for migrated rows |
| `effective_to` | `TIMESTAMPTZ` | Exclusive upper bound; `9999-12-31 23:59:59+00` for the current (open-ended) version |

All queries resolve the "current" version or a point-in-time snapshot using a half-open
interval: `effective_from <= :ts AND effective_to > :ts`. There is no `is_current` flag.

```sql
-- Pattern applied to every structural table:
-- Enforce at most one open-ended (current) row per durable id:
CREATE UNIQUE INDEX {table}_one_current_un
    ON survey.{table} ({entity}_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
```

### Version Numbering: Integer Sequence + n+1 Increment

Each structural table has a dedicated integer sequence for its durable key. When a new
logical entity is created, the sequence supplies the next durable id. When an existing
entity is versioned, the durable id stays constant and the version counter is incremented
n+1 atomically using an `UPDATE ... RETURNING` pattern — no separate `SELECT ... FOR UPDATE`
is needed:

```sql
-- 1. Close current row and retrieve its version atomically
UPDATE survey.{table}
   SET effective_to = NOW()
 WHERE {entity}_id = :{eid}
   AND effective_to = '9999-12-31 23:59:59+00'
RETURNING version;   -- use this value + 1 for the INSERT below

-- 2. Insert the new version row (same durable id, incremented version)
INSERT INTO survey.{table} ({entity}_id, version, ..., effective_from, effective_to)
VALUES (:{eid}, :returned_version + 1, ..., NOW(), '9999-12-31 23:59:59+00');
```

This lock is scoped only to version transitions — a low-frequency, human-driven event
(a researcher edits an element). Reads, respondent page loads, answer recording, and ETL
processing never contend on this lock.

A human-readable version label can be derived as a generated column or formatted in the
application:

```sql
-- Cosmetic only; do not use as a key
version_label TEXT GENERATED ALWAYS AS (question_id || '.' || version) STORED;
```

This produces labels such as `5.1`, `5.2`, `5.3` — making version history immediately
legible in admin UIs and support logs.

#### Database side

Each structural table requires a sequence for its durable key. The first Flyway migration
script creates these sequences:

```sql
CREATE SEQUENCE survey.questions_durable_seq;
CREATE SEQUENCE survey.sections_durable_seq;
CREATE SEQUENCE survey.steps_durable_seq;
-- etc. for each structural table
```

All durable key column defaults then use `nextval()`:

```sql
ADD COLUMN question_id integer NOT NULL DEFAULT nextval('survey.questions_durable_seq')
```

#### Java / Hibernate side

Each durable key field uses `@GeneratedValue` with a named sequence:

```java
@GeneratedValue(strategy = GenerationType.SEQUENCE,
                generator  = "questions_durable_seq")
@SequenceGenerator(name            = "questions_durable_seq",
                   sequenceName    = "survey.questions_durable_seq",
                   allocationSize  = 1)
@Column(name = "question_id", updatable = false, nullable = false)
public Integer questionId;
```

The `version` column has no generator — it is computed from the previous row's version
at write time and supplied explicitly in the INSERT statement.

### Resolving the FK Cascade Problem via Durable Keys

Join tables (`sections_questions`, `steps_sections`, `relationships`) reference **durable
integer ids** instead of surrogate `id` values. A durable integer never changes across row
versions, so a join-table row stays valid when the referenced entity is versioned.

Database-level FK constraints are enforced against the `(durable_id, version=0)` composite
`UNIQUE CONSTRAINT` added to each referenced table (see OQ4 in Open Questions). Each join
table carries a `_version` companion column (`DEFAULT 0`) with a `CHECK` constraint that
pins it to `0`, satisfying the FK target without needing a partial index. The correct
published version at any instant is resolved at query time by joining on the durable id
with the half-open interval `effective_from <= :snapshot AND effective_to > :snapshot`
(use `NOW()` for the live path).

`answers.question_id` stores the surrogate `questions.id` value, which uniquely identifies
the exact version row visible at response time. `answers.question_version` stores the
version number for use in reporting joins against `dim_question`. The correct
`sections_questions` row is re-derived at ETL time via the `firstAccessDt` time-range
join, the same mechanism used for all other structural tables.

---

## Snapshot Anchor: `respondents.firstAccessDt`

**Decided**: the timestamp used to resolve which version of every structural row a
respondent sees is `respondents.firstAccessDt`.

- When a respondent first opens the survey, `firstAccessDt` is recorded.
- Every subsequent page load for that respondent resolves structural rows using:

```sql
effective_from <= :firstAccessDt
  AND effective_to > :firstAccessDt
```

- If a researcher publishes a change *after* a respondent's `firstAccessDt`, that
  respondent continues to see the older version for the entirety of their session.
- `answers` already written store the surrogate `question_id`, permanently binding
  each response to the exact row version that was active at `firstAccessDt`.
- New respondents (no `firstAccessDt` yet) resolve current rows with `effective_from <= NOW()
  AND effective_to > NOW()`, and their `firstAccessDt` is set on first access, freezing their snapshot.

---

## Schema Changes Per Table

### `questions`

```sql
CREATE SEQUENCE survey.questions_durable_seq;

ALTER TABLE survey.questions
    ADD COLUMN question_id    integer NOT NULL DEFAULT nextval('survey.questions_durable_seq'),
    ADD COLUMN version        integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from timestamp with time zone NOT NULL DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to   timestamp with time zone NOT NULL DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by      text,
    ADD COLUMN published_comment text,
    ADD COLUMN is_draft          boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX questions_one_current_un
    ON survey.questions (question_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX questions_one_draft_un
    ON survey.questions (question_id)
    WHERE is_draft = true;

ALTER TABLE survey.questions ADD CONSTRAINT questions_id_version_un UNIQUE (question_id, version);

CREATE INDEX questions_durable_id_idx ON survey.questions (question_id);
```

`questions.select_group_id` already exists as a surrogate FK to `select_groups.id`. After
`select_groups` is migrated (see below), backfill and retarget the FK:

```sql
-- Run after select_groups migration has populated select_groups.select_group_id
ALTER TABLE survey.questions
    ADD COLUMN select_group_version integer NOT NULL DEFAULT 0;

ALTER TABLE survey.questions
    ADD CONSTRAINT questions_select_group_version_ck CHECK (select_group_version = 0);
 -- adjust name to match actual constraint

UPDATE survey.questions q
   SET select_group_id = sg.select_group_id
  FROM survey.select_groups sg
 WHERE sg.id = q.select_group_id;

-- version = 0: entity-existence check. Time-range join resolves current version per respondent.
ALTER TABLE survey.questions
    ADD CONSTRAINT questions_select_group_id_fkey
    FOREIGN KEY (select_group_id, select_group_version) REFERENCES survey.select_groups (select_group_id, version);
```

`answers` references `(question_id, version)` to pin the exact version row seen at response time.

---

### `select_groups`

```sql
CREATE SEQUENCE survey.select_groups_durable_seq;

ALTER TABLE survey.select_groups
    ADD COLUMN select_group_id  integer NOT NULL DEFAULT nextval('survey.select_groups_durable_seq'),
    ADD COLUMN version          integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from   timestamp with time zone NOT NULL DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to     timestamp with time zone NOT NULL DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by      text,
    ADD COLUMN published_comment text,
    ADD COLUMN is_draft          boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX select_groups_one_current_un
    ON survey.select_groups (select_group_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX select_groups_one_draft_un
    ON survey.select_groups (select_group_id)
    WHERE is_draft = true;

ALTER TABLE survey.select_groups ADD CONSTRAINT select_groups_id_version_un UNIQUE (select_group_id, version);

CREATE INDEX select_groups_durable_id_idx ON survey.select_groups (select_group_id);
```

`questions.select_group_id` (durable reference column, replacing the old surrogate `select_group_id` FK)
references `select_groups.select_group_id`.

---

### `select_items`

When a researcher changes an answer option's display text or coded value, that item is
individually versioned.

```sql
CREATE SEQUENCE survey.select_items_durable_seq;

ALTER TABLE survey.select_items
    ADD COLUMN select_item_id integer NOT NULL DEFAULT nextval('survey.select_items_durable_seq'),
    ADD COLUMN version        integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from timestamp with time zone NOT NULL DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to   timestamp with time zone NOT NULL DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by      text,
    ADD COLUMN published_comment text,
    ADD COLUMN is_draft          boolean NOT NULL DEFAULT false;

-- group_id is renamed to select_group_id (column already exists; values are surrogate ids)
ALTER TABLE survey.select_items RENAME COLUMN group_id TO select_group_id;

-- Drop old FK pointing to select_groups.id (the surrogate PK)
ALTER TABLE survey.select_items DROP CONSTRAINT select_items_group_id_fkey; -- adjust name to match actual constraint

-- Backfill: replace old surrogate id values with durable select_group_id values
-- Run after select_groups migration has populated select_groups.select_group_id
UPDATE survey.select_items si
   SET select_group_id = sg.select_group_id
  FROM survey.select_groups sg
 WHERE sg.id = si.select_group_id;

-- Re-add FK to the durable key (version = 0: entity-existence check)
ALTER TABLE survey.select_items
    ADD COLUMN select_group_version integer NOT NULL DEFAULT 0;

ALTER TABLE survey.select_items
    ADD CONSTRAINT select_items_select_group_version_ck CHECK (select_group_version = 0);

ALTER TABLE survey.select_items
    ADD CONSTRAINT select_items_select_group_id_fkey
    FOREIGN KEY (select_group_id, select_group_version) REFERENCES survey.select_groups (select_group_id, version);

CREATE UNIQUE INDEX select_items_one_current_un
    ON survey.select_items (select_item_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX select_items_one_draft_un
    ON survey.select_items (select_item_id)
    WHERE is_draft = true;

ALTER TABLE survey.select_items ADD CONSTRAINT select_items_id_version_un UNIQUE (select_item_id, version);

CREATE INDEX select_items_durable_id_idx ON survey.select_items (select_item_id);
CREATE INDEX select_items_select_group_id_idx ON survey.select_items (select_group_id);
```

`answers` stores `text_value` directly at response time, so versioning answer options
does not disturb any historical responses.

---

### `sections`

```sql
CREATE SEQUENCE survey.sections_durable_seq;

ALTER TABLE survey.sections
    ADD COLUMN section_id     integer NOT NULL DEFAULT nextval('survey.sections_durable_seq'),
    ADD COLUMN version        integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from timestamp with time zone NOT NULL DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to   timestamp with time zone NOT NULL DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by      text,
    ADD COLUMN published_comment text,
    ADD COLUMN is_draft          boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX sections_one_current_un
    ON survey.sections (section_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX sections_one_draft_un
    ON survey.sections (section_id)
    WHERE is_draft = true;

ALTER TABLE survey.sections ADD CONSTRAINT sections_id_version_un UNIQUE (section_id, version);

CREATE INDEX sections_durable_id_idx ON survey.sections (section_id);

-- display_order must be NUMERIC to support decimal midpoint insertion (see Adding a new question)
ALTER TABLE survey.sections ALTER COLUMN display_order TYPE NUMERIC;
```

---

### `steps`

```sql
CREATE SEQUENCE survey.steps_durable_seq;

ALTER TABLE survey.steps
    ADD COLUMN step_id        integer NOT NULL DEFAULT nextval('survey.steps_durable_seq'),
    ADD COLUMN version        integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from timestamp with time zone NOT NULL DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to   timestamp with time zone NOT NULL DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by      text,
    ADD COLUMN published_comment text,
    ADD COLUMN is_draft          boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX steps_one_current_un
    ON survey.steps (step_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX steps_one_draft_un
    ON survey.steps (step_id)
    WHERE is_draft = true;

ALTER TABLE survey.steps ADD CONSTRAINT steps_id_version_un UNIQUE (step_id, version);

CREATE INDEX steps_durable_id_idx ON survey.steps (step_id);

-- display_order must be NUMERIC to support decimal midpoint insertion (see Adding a new question)
ALTER TABLE survey.steps ALTER COLUMN display_order TYPE NUMERIC;
```

---

### `steps_sections`

`steps_sections` is the join table between `steps` and `sections`. Its `display_key`
column (e.g. `"0001.0001"`) is derived from the step and section display orders — it
changes when either display order changes. Because changing a display order is a
versioning event (old row closed, new row inserted), `display_key` is stable within
any given version row's effective period. It is not a durable key across versions;
`steps_sections_id` is. Keep `display_key` in the table — queries that need to
resolve a row by display key use the time-range predicate to get the correct version:

```sql
JOIN survey.steps_sections ss
  ON ss.survey_id      = :survey_id
 AND ss.display_key    = :display_key
 AND ss.effective_from <= :firstAccessDt
 AND ss.effective_to   > :firstAccessDt
```

The surrogate FK columns `step_id` and `section_id` are updated in-place to reference
durable integer ids.

```sql
CREATE SEQUENCE survey.steps_sections_durable_seq;

ALTER TABLE survey.steps_sections
    ADD COLUMN steps_sections_id integer NOT NULL DEFAULT nextval('survey.steps_sections_durable_seq'),
    ADD COLUMN version           integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from    timestamp with time zone NOT NULL DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to      timestamp with time zone NOT NULL DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by      text,
    ADD COLUMN published_comment text,
    ADD COLUMN is_draft          boolean NOT NULL DEFAULT false,
    ADD COLUMN step_version      integer NOT NULL DEFAULT 0,
    ADD COLUMN section_version   integer NOT NULL DEFAULT 0;

ALTER TABLE survey.steps_sections
    ADD CONSTRAINT steps_sections_ref_versions_ck CHECK (step_version = 0 AND section_version = 0);

-- step_id and section_id already exist as surrogate FKs; drop old constraints,
-- backfill with durable ids, then re-add constraints to the durable key columns.
-- Run after steps and sections migrations have populated step_id / section_id.
ALTER TABLE survey.steps_sections DROP CONSTRAINT steps_sections_step_id_fkey;    -- adjust name to match actual constraint
ALTER TABLE survey.steps_sections DROP CONSTRAINT steps_sections_section_id_fkey; -- adjust name to match actual constraint

UPDATE survey.steps_sections ss
   SET step_id = s.step_id
  FROM survey.steps s
 WHERE s.id = ss.step_id;

UPDATE survey.steps_sections ss
   SET section_id = sec.section_id
  FROM survey.sections sec
 WHERE sec.id = ss.section_id;

-- version = 0: entity-existence check. Time-range join resolves current version per respondent.
ALTER TABLE survey.steps_sections
    ADD CONSTRAINT steps_sections_step_id_fkey
    FOREIGN KEY (step_id, step_version) REFERENCES survey.steps (step_id, version);

ALTER TABLE survey.steps_sections
    ADD CONSTRAINT steps_sections_section_id_fkey
    FOREIGN KEY (section_id, section_version) REFERENCES survey.sections (section_id, version);

CREATE UNIQUE INDEX steps_sections_one_current_un
    ON survey.steps_sections (steps_sections_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX steps_sections_one_draft_un
    ON survey.steps_sections (steps_sections_id)
    WHERE is_draft = true;

ALTER TABLE survey.steps_sections ADD CONSTRAINT steps_sections_id_version_un UNIQUE (steps_sections_id, version);

CREATE INDEX steps_sections_durable_id_idx ON survey.steps_sections (steps_sections_id);

-- display_order must be NUMERIC to support decimal midpoint insertion (see Adding a new question)
ALTER TABLE survey.steps_sections ALTER COLUMN display_order TYPE NUMERIC;
```

`relationships.downstream_ss_id` (currently a surrogate FK to `steps_sections.id`)
migrates to reference `steps_sections.steps_sections_id` (durable integer).

---

### `sections_questions`

The join table between `sections` and `questions`. Surrogate FK columns `section_id` and
`question_id` are replaced with durable key columns.

```sql
CREATE SEQUENCE survey.sections_questions_durable_seq;

ALTER TABLE survey.sections_questions
    ADD COLUMN sections_question_id integer NOT NULL DEFAULT nextval('survey.sections_questions_durable_seq'),
    ADD COLUMN version              integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from       timestamp with time zone NOT NULL DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to         timestamp with time zone NOT NULL DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by         text,
    ADD COLUMN published_comment    text,
    ADD COLUMN is_draft             boolean NOT NULL DEFAULT false,
    ADD COLUMN question_version     integer NOT NULL DEFAULT 0,
    ADD COLUMN section_version      integer NOT NULL DEFAULT 0;

ALTER TABLE survey.sections_questions
    ADD CONSTRAINT sections_questions_ref_versions_ck CHECK (question_version = 0 AND section_version = 0);

-- section_id and question_id already exist as surrogate FKs; drop old constraints,
-- backfill with durable ids, then re-add constraints to the durable key columns.
-- Run after sections and questions migrations have populated section_id / question_id.
ALTER TABLE survey.sections_questions DROP CONSTRAINT sections_questions_section_id_fkey;  -- adjust name to match actual constraint
ALTER TABLE survey.sections_questions DROP CONSTRAINT sections_questions_question_id_fkey; -- adjust name to match actual constraint

UPDATE survey.sections_questions sq
   SET section_id = sec.section_id
  FROM survey.sections sec
 WHERE sec.id = sq.section_id;

UPDATE survey.sections_questions sq
   SET question_id = q.question_id
  FROM survey.questions q
 WHERE q.id = sq.question_id;

-- version = 0: entity-existence check. Time-range join resolves current version per respondent.
ALTER TABLE survey.sections_questions
    ADD CONSTRAINT sections_questions_section_id_fkey
    FOREIGN KEY (section_id, section_version) REFERENCES survey.sections (section_id, version);

ALTER TABLE survey.sections_questions
    ADD CONSTRAINT sections_questions_question_id_fkey
    FOREIGN KEY (question_id, question_version) REFERENCES survey.questions (question_id, version);

CREATE UNIQUE INDEX sections_questions_one_current_un
    ON survey.sections_questions (sections_question_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX sections_questions_one_draft_un
    ON survey.sections_questions (sections_question_id)
    WHERE is_draft = true;

ALTER TABLE survey.sections_questions ADD CONSTRAINT sections_questions_id_version_un UNIQUE (sections_question_id, version);

CREATE INDEX sections_questions_durable_id_idx ON survey.sections_questions (sections_question_id);

-- display_order must be NUMERIC to support decimal midpoint insertion (see Adding a new question)
ALTER TABLE survey.sections_questions ALTER COLUMN display_order TYPE NUMERIC;
```

`answers.section_question_id` references the surrogate `sections_questions.id`, pinning
the exact `sections_questions` version row the respondent saw. `answers.question_id`
references the surrogate `questions.id`, pinning the exact `questions` version row.
Neither column references a composite — the surrogate already points directly to the
specific version row. The correct version is recovered at query time by joining
`answers → questions ON q.id = a.question_id` (and likewise for `sections_questions`).

---

### `relationships`

Encodes conditional display logic (skip patterns, repeating steps). All surrogate FK
columns pointing to other structural tables are replaced with durable key columns.

```sql
CREATE SEQUENCE survey.relationships_durable_seq;

ALTER TABLE survey.relationships
    ADD COLUMN relationship_id         integer NOT NULL DEFAULT nextval('survey.relationships_durable_seq'),
    ADD COLUMN version                 integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from          timestamp with time zone NOT NULL DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to            timestamp with time zone NOT NULL DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by            text,
    ADD COLUMN published_comment       text,
    ADD COLUMN is_draft                boolean NOT NULL DEFAULT false,
    ADD COLUMN upstream_step_version   integer NOT NULL DEFAULT 0,
    ADD COLUMN upstream_sq_version     integer NOT NULL DEFAULT 0,
    ADD COLUMN downstream_step_version integer NOT NULL DEFAULT 0,
    ADD COLUMN downstream_ss_version   integer NOT NULL DEFAULT 0,
    ADD COLUMN downstream_sq_version   integer NOT NULL DEFAULT 0;

ALTER TABLE survey.relationships
    ADD CONSTRAINT relationships_ref_versions_ck CHECK (
        upstream_step_version   = 0 AND
        upstream_sq_version     = 0 AND
        downstream_step_version = 0 AND
        downstream_ss_version   = 0 AND
        downstream_sq_version   = 0
    );

-- downstream_s_id is renamed to downstream_ss_id (ss = steps_sections convention)
ALTER TABLE survey.relationships RENAME COLUMN downstream_s_id TO downstream_ss_id;

-- All five FK columns already exist as surrogate FKs; drop old constraints, backfill,
-- then re-add constraints to durable keys.
-- Run after steps, sections_questions, and steps_sections migrations are complete.
ALTER TABLE survey.relationships DROP CONSTRAINT relationships_upstream_step_id_fkey;   -- adjust names
ALTER TABLE survey.relationships DROP CONSTRAINT relationships_upstream_sq_id_fkey;
ALTER TABLE survey.relationships DROP CONSTRAINT relationships_downstream_step_id_fkey;
ALTER TABLE survey.relationships DROP CONSTRAINT relationships_downstream_ss_id_fkey;  -- after rename above
ALTER TABLE survey.relationships DROP CONSTRAINT relationships_downstream_sq_id_fkey;

UPDATE survey.relationships r
   SET upstream_step_id = s.step_id
  FROM survey.steps s
 WHERE s.id = r.upstream_step_id;

UPDATE survey.relationships r
   SET upstream_sq_id = sq.sections_question_id
  FROM survey.sections_questions sq
 WHERE sq.id = r.upstream_sq_id;

UPDATE survey.relationships r
   SET downstream_step_id = s.step_id
  FROM survey.steps s
 WHERE s.id = r.downstream_step_id;

UPDATE survey.relationships r
   SET downstream_ss_id = ss.steps_sections_id
  FROM survey.steps_sections ss
 WHERE ss.id = r.downstream_ss_id;

UPDATE survey.relationships r
   SET downstream_sq_id = sq.sections_question_id
  FROM survey.sections_questions sq
 WHERE sq.id = r.downstream_sq_id;

-- version = 0: entity-existence check. Time-range join resolves current version per respondent.
ALTER TABLE survey.relationships
    ADD CONSTRAINT relationships_upstream_step_id_fkey
    FOREIGN KEY (upstream_step_id, upstream_step_version) REFERENCES survey.steps (step_id, version);

ALTER TABLE survey.relationships
    ADD CONSTRAINT relationships_upstream_sq_id_fkey
    FOREIGN KEY (upstream_sq_id, upstream_sq_version) REFERENCES survey.sections_questions (sections_question_id, version);

ALTER TABLE survey.relationships
    ADD CONSTRAINT relationships_downstream_step_id_fkey
    FOREIGN KEY (downstream_step_id, downstream_step_version) REFERENCES survey.steps (step_id, version);

ALTER TABLE survey.relationships
    ADD CONSTRAINT relationships_downstream_ss_id_fkey
    FOREIGN KEY (downstream_ss_id, downstream_ss_version) REFERENCES survey.steps_sections (steps_sections_id, version);

ALTER TABLE survey.relationships
    ADD CONSTRAINT relationships_downstream_sq_id_fkey
    FOREIGN KEY (downstream_sq_id, downstream_sq_version) REFERENCES survey.sections_questions (sections_question_id, version);

CREATE UNIQUE INDEX relationships_one_current_un
    ON survey.relationships (relationship_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX relationships_one_draft_un
    ON survey.relationships (relationship_id)
    WHERE is_draft = true;

ALTER TABLE survey.relationships ADD CONSTRAINT relationships_id_version_un UNIQUE (relationship_id, version);

CREATE INDEX relationships_durable_id_idx ON survey.relationships (relationship_id);
```

---

### `surveys`

`surveys` is the top-level container record. Its `name` and `description` fields can
change over time (e.g. renaming a survey for clarity). These changes are tracked
**in-place (SCD Type 1)** — the existing row is updated rather than versioned — using the
same `published_by` and `published_comment` columns written at publish time.

No `effective_from` / `effective_to` / `version` / `is_draft` columns are added to
`surveys`. Renaming a survey does not affect which version of any structural element
respondents see; that is governed entirely by the structural tables and `firstAccessDt`.
Historical reports will reflect the current survey name, which is intentional (same
reasoning as SCD Type 1 on `dim_step` / `dim_section`).

```sql
ALTER TABLE survey.surveys
    ADD COLUMN published_by      text,
    ADD COLUMN published_comment text;
```

When an author changes `surveys.name` or `surveys.description`, the Author Tool issues:

```sql
UPDATE survey.surveys
   SET name             = :new_name,
       description      = :new_description,
       published_by     = :user,
       published_comment = :comment
 WHERE id = :survey_id;
```

---

## FK Reference Migration Summary

| Old column (surrogate) | New column (durable key) | References |
|---|---|---|
| `sections_questions.question_id` (surrogate FK) | `sections_questions.question_id` (durable integer) | `questions.question_id` |
| `sections_questions.section_id` (surrogate FK) | `sections_questions.section_id` (durable integer) | `sections.section_id` |
| `steps_sections.step_id` (surrogate FK) | `steps_sections.step_id` (durable integer) | `steps.step_id` |
| `steps_sections.section_id` (surrogate FK) | `steps_sections.section_id` (durable integer) | `sections.section_id` |
| `questions.select_group_id` (surrogate FK) | `questions.select_group_id` (durable integer) | `select_groups.select_group_id` |
| `select_items.group_id` | `select_items.select_group_id` | `select_groups.select_group_id` |
| `relationships.upstream_step_id` (surrogate FK) | `relationships.upstream_step_id` (durable integer) | `steps.step_id` |
| `relationships.upstream_sq_id` (surrogate FK) | `relationships.upstream_sq_id` (durable integer) | `sections_questions.sections_question_id` |
| `relationships.downstream_step_id` (surrogate FK) | `relationships.downstream_step_id` (durable integer) | `steps.step_id` |
| `relationships.downstream_s_id` | `relationships.downstream_ss_id` | `steps_sections.steps_sections_id` |
| `relationships.downstream_sq_id` (surrogate FK) | `relationships.downstream_sq_id` (durable integer) | `sections_questions.sections_question_id` |

**Tables with no Type 2 changes** (they record events, not versioned definitions):

| Table | Reason |
|---|---|
| `answers` | Snapshots surrogate `question_id` / `section_question_id` at response time — this IS the Type 2 mechanism for responses |
| `dependents` | Event record linking two `answers` rows; not a versioned entity |
| `respondents` | Event record; `firstAccessDt` is the snapshot anchor, not itself versioned |
| `surveys` | Container; the survey instrument is versioned at the element level, not the survey level |

---

## Versioning Workflows

### Rewording a question

```
Researcher saves new question text
    │
    ├─ 1. Close the current version:
    │       UPDATE survey.questions
    │          SET effective_to = NOW()
    │        WHERE question_id = :qid
    │          AND effective_to = '9999-12-31 23:59:59+00'
    │       RETURNING version;   -- use returned_version + 1 in the INSERT below
    │
    ├─ 2. Insert new version row (same question_id, incremented version):
    │       INSERT INTO survey.questions
    │           (question_id, version, survey_id, text, ..., effective_from, effective_to)
    │       VALUES (:qid, :returned_version + 1, :survey_id, :new_text, ..., NOW(), '9999-12-31 23:59:59+00');
    │
    └─ 3. sections_questions and relationships are unchanged — they reference
          question_id (durable integer). Any respondent whose firstAccessDt < NOW()
          continues to see the old version; new respondents see the new version. ✓
```

### Renaming a section or step

Same pattern using `section_id` or `step_id`. `sections_questions`, `steps_sections`,
and `relationships` reference these via durable integer ids and resolve automatically.

### Changing a select option

```
    ├─ 1. Close old select_item row: SET effective_to = NOW() WHERE select_item_id = :sid AND effective_to = '9999-12-31 23:59:59+00'.
    ├─ 2. Insert new select_item row (same select_item_id, version + 1, new text/coded_value, effective_to = '9999-12-31 23:59:59+00').
    └─ 3. answers already written store text_value directly. History is preserved. ✓
```

### Changing conditional logic (a relationship)

```
    ├─ 1. Close old relationship row: SET effective_to = NOW() WHERE relationship_id = :rid AND effective_to = '9999-12-31 23:59:59+00'.
    ├─ 2. Insert new relationship row (same relationship_id, version + 1, new operator / reference_value, effective_to = '9999-12-31 23:59:59+00').
    └─ 3. Respondents whose firstAccessDt < NOW() continue to evaluate old logic
          (they load structural data time-filtered to their firstAccessDt).
          New respondents evaluate the new logic. ✓
```

### Retiring (removing) a structural element

Retirement is part of the draft/publish workflow. The author stages the retirement as a
draft; the publish transaction sets `effective_to` to the publish timestamp. Respondents
whose `firstAccessDt` is before the publish timestamp satisfy
`effective_from <= firstAccessDt AND effective_to > firstAccessDt` on the retired row and
continue to see the element. Respondents who start after the publish timestamp do not —
the row no longer satisfies the time-range predicate for any timestamp ≥ publish time.

```
    DRAFT PHASE
    ├─ Author marks the element for retirement in the UI (no row change yet, or
    │    flag on draft set — implementation detail for the Author Tool).
    │
    PUBLISH PHASE (single transaction — same commit as any other publish)
    ├─ 1. Set effective_to = NOW() on the questions row:
    │       UPDATE survey.questions
    │          SET effective_to    = NOW(),
    │              published_by    = :user,
    │              published_comment = :comment
    │        WHERE question_id = :qid
    │          AND effective_to = '9999-12-31 23:59:59+00';
    ├─ 2. Set effective_to = NOW() on the sections_questions row referencing that question_id.
    ├─ 3. Set effective_to = NOW() on any relationships referencing that sections_question_id
    │       as upstream_sq_id or downstream_sq_id.
    └─ 4. Historical answers referencing the old question_id are untouched. ✓

    POST-PUBLISH
    └─ Respondents with firstAccessDt < publish_time continue to see the question.
       Respondents with firstAccessDt >= publish_time do not. No data is deleted.
```

### Adding a new question

New structural elements follow the same draft/publish model as edits. The element is
inserted as a draft (`is_draft = true`, `effective_from = NULL`, `effective_to = NULL`)
and becomes visible only when published.

**Display order — decimal midpoint insertion**: `display_order` columns are `NUMERIC`
(not `INTEGER`) to allow inserting between existing positions without renumbering.

| Existing positions | New position between 1 and 2 | Next insert between 1 and 1.5 |
|---|---|---|
| 1, 2 | 1.5 | 1.25 |
| 1, 1.25 | 1.125 | … |

If the midpoint precision becomes unwieldy, an `elicit_author` may renumber all
`display_order` values back to integers (1, 2, 3, …). Because changing `display_order`
is a versioning event, renumbering generates new version rows for every reordered element
and closes the old rows — this is a bulk publish and requires a single `published_comment`.

**Respondent visibility**: Respondents whose `firstAccessDt` predates the new element's
`effective_from` will never see it. The time-range predicate
`effective_from <= :firstAccessDt AND effective_to > :firstAccessDt` excludes any row
whose `effective_from` is after their snapshot anchor. This is correct and intentional —
those respondents were presented with the survey instrument as it existed when they
first accessed it.

```
    ├─ DRAFT PHASE
    │   ├─ 1. Insert a new questions row (is_draft=true, effective_from=NULL, effective_to=NULL,
    │   │       new question_id from sequence, version=0).
    │   ├─ 2. Insert a new sections_questions row (is_draft=true, effective_from=NULL,
    │   │       effective_to=NULL) linking the new question_id to the target section_id
    │   │       with a decimal midpoint display_order (e.g. 1.5 between positions 1 and 2).
    │   └─ 3. Optionally insert new relationships rows (is_draft=true) for conditional logic.
    │
    └─ PUBLISH PHASE (single transaction)
        ├─ SET is_draft = false, effective_from = NOW(), effective_to = '9999-12-31 23:59:59+00'
        │    on every draft row (questions, sections_questions, relationships).
        └─ SET published_by = :user, published_comment = :comment on every promoted row.
```

**Schema note — `display_order` type change required**: All tables that carry
`display_order` (`steps`, `sections`, `sections_questions`, `steps_sections`) and the
corresponding storage columns in `answers` (`step`, `section`) must be altered from
`INTEGER` to `NUMERIC` before the decimal-insertion pattern can be used.

```sql
ALTER TABLE survey.steps             ALTER COLUMN display_order TYPE NUMERIC;
ALTER TABLE survey.sections          ALTER COLUMN display_order TYPE NUMERIC;
ALTER TABLE survey.sections_questions ALTER COLUMN display_order TYPE NUMERIC;
ALTER TABLE survey.steps_sections    ALTER COLUMN display_order TYPE NUMERIC;
ALTER TABLE survey.answers           ALTER COLUMN step          TYPE NUMERIC;
ALTER TABLE survey.answers           ALTER COLUMN section       TYPE NUMERIC;
```

Existing integer values (`1`, `2`, `3`, …) are losslessly cast to `NUMERIC`.

---

## Runtime Query Patterns

### Current survey structure (new respondent, no firstAccessDt yet)

```sql
SELECT q.id, q.text, q.short_text, q.tool_tip, q.required,
       sq.display_order, sg.id AS select_group_id
FROM   survey.sections_questions sq
JOIN   survey.questions q
    ON  q.question_id    = sq.question_id
    AND q.effective_from <= NOW()
    AND q.effective_to   > NOW()
LEFT JOIN survey.select_groups sg
    ON  sg.select_group_id = q.select_group_id
    AND sg.effective_from <= NOW()
    AND sg.effective_to   > NOW()
WHERE  sq.survey_id      = :survey_id
  AND  sq.section_id     = :section_id
  AND  sq.effective_from <= NOW()
  AND  sq.effective_to   > NOW()
ORDER  BY sq.display_order;
```

### Point-in-time snapshot (respondent with a firstAccessDt)

```sql
SELECT q.id, q.text, q.short_text, q.tool_tip, q.required,
       sq.display_order, sg.id AS select_group_id
FROM   survey.sections_questions sq
JOIN   survey.questions q
    ON  q.question_id    = sq.question_id
    AND q.effective_from <= :firstAccessDt
    AND q.effective_to   >  :firstAccessDt
LEFT JOIN survey.select_groups sg
    ON  sg.select_group_id = q.select_group_id
    AND sg.effective_from <= :firstAccessDt
    AND sg.effective_to   >  :firstAccessDt
WHERE  sq.survey_id      = :survey_id
  AND  sq.section_id     = :section_id
  AND  sq.effective_from <= :firstAccessDt
  AND  sq.effective_to   >  :firstAccessDt
ORDER  BY sq.display_order;
```

### Recovering exact wording a respondent saw (from their answers)

```sql
-- answers.question_id is the surrogate — resolves directly to the versioned row
SELECT a.text_value, q.text AS question_text_at_response_time, q.version
FROM   survey.answers a
JOIN   survey.questions q ON q.id = a.question_id
WHERE  a.respondent_id = :respondent_id
  AND  a.deleted = false;
```

---

## Reporting Across Question Versions

Group by `question_id` to aggregate all responses to the same logical question regardless
of wording changes:

```sql
SELECT q.question_id,
       q.version,
       q.text              AS wording,
       q.effective_from,
       q.effective_to,
       a.text_value,
       COUNT(a.id)         AS response_count
FROM   survey.answers a
JOIN   survey.questions q
    ON  q.question_id = a.question_id
    AND q.version     = a.question_version
WHERE  q.question_id = :question_id
  AND  a.deleted = false
GROUP  BY q.question_id, q.version, q.text, q.effective_from, q.effective_to, a.text_value
ORDER  BY q.version, a.text_value;
```

---

## Metadata, Ontology, and ETL Impact

### The `metadata` Table

`metadata` is the bridge between the survey instrument and the `surveyreport` dimensional
model. It maps structural elements (questions, sections_questions, steps_sections) to
ontology tags, which drive the automatic creation and population of `dim_*` tables.

Current schema:

```
metadata (id, survey_id, question_id → questions.id,
                          section_question_id → sections_questions.id,
                          step_section_id → steps_sections.id,
                          ontology_id → ontology.id, value)
```

All three element FK columns reference **surrogate ids**. When a question is versioned its
new row has a new `id`, and the existing `metadata` row still points to the old surrogate.
The new version row has no metadata entry — its ontology tag is lost. ETL would silently
produce no dimension values for respondents whose `firstAccessDt` falls after the change.

**Decision: Option B — migrate `metadata` to durable keys.**

Replace the three surrogate FK columns with durable UUID columns. No carry-forward logic
is needed on every versioning event; ETL queries join via durable key and resolve the
correct structural row using the time-range predicate (`effective_from <= :ts AND effective_to > :ts`).

#### Schema change for `metadata`

```sql
-- Step 1: rename existing surrogate FK columns before adding new durable-key columns
-- (avoids column name collision — both old and new share the same logical name)
ALTER TABLE survey.metadata
    RENAME COLUMN question_id      TO question_id_surrogate;
ALTER TABLE survey.metadata
    RENAME COLUMN section_question_id TO section_question_id_surrogate;
ALTER TABLE survey.metadata
    RENAME COLUMN step_section_id  TO step_section_id_surrogate;

-- Step 2: add new durable integer columns
ALTER TABLE survey.metadata
    ADD COLUMN question_durable_id      integer,   -- durable key, replaces question_id_surrogate
    ADD COLUMN sections_question_id     integer,   -- durable key, replaces section_question_id_surrogate
    ADD COLUMN steps_sections_id        integer;   -- durable key, replaces step_section_id_surrogate

-- Step 3: populate from existing surrogate values
UPDATE survey.metadata m
   SET question_durable_id = q.question_id
  FROM survey.questions q
 WHERE q.id = m.question_id_surrogate
   AND m.question_id_surrogate IS NOT NULL;

UPDATE survey.metadata m
   SET sections_question_id = sq.sections_question_id
  FROM survey.sections_questions sq
 WHERE sq.id = m.section_question_id_surrogate
   AND m.section_question_id_surrogate IS NOT NULL;

UPDATE survey.metadata m
   SET steps_sections_id = ss.steps_sections_id
  FROM survey.steps_sections ss
 WHERE ss.id = m.step_section_id_surrogate
   AND m.step_section_id_surrogate IS NOT NULL;

-- Step 4: assert no nulls remain in the new durable columns
-- (each old column was mutually exclusive nullable; the new columns follow the same pattern)
-- Run this check before proceeding:
-- SELECT COUNT(*) FROM survey.metadata
--  WHERE question_durable_id IS NULL AND sections_question_id IS NULL AND steps_sections_id IS NULL;
-- Expected: 0

-- Step 5: replace the unique constraint to reference the new durable columns
ALTER TABLE survey.metadata
    DROP CONSTRAINT metadata_un,
    ADD CONSTRAINT metadata_un UNIQUE (steps_sections_id, question_durable_id, sections_question_id, ontology_id, value);

-- Step 6: drop old surrogate FK constraints
ALTER TABLE survey.metadata
    DROP CONSTRAINT metadata_question_fk,
    DROP CONSTRAINT metadata_sect_quest_fk,
    DROP CONSTRAINT metadata_section_fk;

-- Step 7: rename question_durable_id to question_id now that the surrogate column is renamed
ALTER TABLE survey.metadata RENAME COLUMN question_durable_id TO question_id;

-- Indexes on new durable columns
CREATE INDEX metadata_question_id_index         ON survey.metadata (question_id);
CREATE INDEX metadata_sections_question_id_idx  ON survey.metadata (sections_question_id);
CREATE INDEX metadata_steps_sections_id_idx     ON survey.metadata (steps_sections_id);
```

Old surrogate columns (`question_id_surrogate`, `section_question_id_surrogate`,
`step_section_id_surrogate`) are dropped in a later migration after all ETL queries
have been updated to use the new durable integer columns and verified in production.
See Open Question 1 for the drop testing checklist.

---

### `dim_step` and `dim_section` — Rekey by Durable UUID

These dimension tables are currently keyed by the **surrogate** `steps.id` /
`sections.id`:

```sql
-- current ETL
INSERT INTO surveyreport.dim_step(id, value)
SELECT s.id, s.dimension_name FROM survey.steps s
ON CONFLICT (id) DO UPDATE SET value = excluded.value;
```

With Type 2 versioning, renaming a step creates a new surrogate `id`. Running this ETL
un-modified inserts a second `dim_step` row for the renamed step, splitting historical and
new respondents across two separate dimension members for the same logical step.

**Fix**: add a `step_id integer` column to `dim_step` and `dim_section`, use it as the
business key for upserts, and filter to the current version only.

```sql
ALTER TABLE surveyreport.dim_step
    ADD COLUMN step_id integer UNIQUE;

ALTER TABLE surveyreport.dim_section
    ADD COLUMN section_id integer UNIQUE;
```

Updated ETL upserts:

```sql
-- UPDATE_STEPS_DIMENSION_TABLE_SQL (revised)
INSERT INTO surveyreport.dim_step (step_id, value)
SELECT s.step_id, s.dimension_name
  FROM survey.steps s
 WHERE s.effective_from <= NOW() AND s.effective_to > NOW()
ON CONFLICT (step_id) DO UPDATE
    SET value = excluded.value;

-- UPDATE_SECTIONS_DIMENSION_TABLE_SQL (revised)
INSERT INTO surveyreport.dim_section (section_id, value)
SELECT s.section_id, s.dimension_name
  FROM survey.sections s
 WHERE s.effective_from <= NOW() AND s.effective_to > NOW()
ON CONFLICT (section_id) DO UPDATE
    SET value = excluded.value;
```

The integer `id` column on `dim_step` / `dim_section` remains as the FK target for
`fact_sections.step_key` / `fact_sections.section_key` — no change to `fact_sections`
is required. The mapping from `step_id` integer → integer dim id is resolved at ETL load
time via the `dim_step.step_id` lookup.

---

### `surveyreport.fact_sections` — Schema Definition

`fact_sections` is the central fact table in the `surveyreport` schema. Each row
represents one answer contributed by one finalized respondent, placed in its structural
context (step, section, question dimension).

```sql
CREATE TABLE surveyreport.fact_sections (
    id              bigserial    PRIMARY KEY,

    -- Respondent and survey
    respondent_id   integer      NOT NULL,   -- FK → survey.respondents.id
    survey_id       integer      NOT NULL,   -- FK → survey.surveys.id

    -- Dimension foreign keys (reference surrogate PKs on dim tables)
    step_key        integer      NOT NULL REFERENCES surveyreport.dim_step    (id),
    section_key     integer      NOT NULL REFERENCES surveyreport.dim_section (id),

    -- Dynamic dim_* columns (added automatically by FIND_NEW_DIMENSION_TABLES_SQL
    -- when new ontology tags are discovered; column names match the ontology tag/value)
    -- Example: diagnosis_value TEXT, age_group_value TEXT, ...

    -- Answer payload
    question_id     integer      NOT NULL,   -- survey.questions.id (surrogate — exact version row)
    question_version integer     NOT NULL DEFAULT 0,  -- survey.questions.version at response time
    text_value      text,                    -- respondent's answer text
    display_key     text,                    -- sections_questions.display_key at response time

    -- Processing metadata
    finalized_dt    timestamptz,             -- copy of respondents.finalized_dt at ETL time
    etl_processed_dt timestamptz NOT NULL DEFAULT NOW()
);

CREATE INDEX fact_sections_respondent_id_idx ON surveyreport.fact_sections (respondent_id);
CREATE INDEX fact_sections_survey_id_idx     ON surveyreport.fact_sections (survey_id);
CREATE INDEX fact_sections_question_id_idx   ON surveyreport.fact_sections (question_id);
CREATE INDEX fact_sections_step_key_idx      ON surveyreport.fact_sections (step_key);
CREATE INDEX fact_sections_section_key_idx   ON surveyreport.fact_sections (section_key);
```

**ETL deduplication guard**: `INSERT_MISSING_FACT_SECTION_SQL` uses a `NOT EXISTS`
subquery on `(respondent_id, question_id)` to skip rows already present. This makes the
ETL idempotent — re-running after a skip-and-log failure inserts only the missing rows.

**Dynamic dim columns**: `FIND_NEW_DIMENSION_TABLES_SQL` and
`NEW_FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL` add columns to `fact_sections` at runtime
when new ontology dimensions are discovered. These columns are always `TEXT` and named
after the ontology tag. Reporting queries join or filter on them directly.

---

### `INSERT_MISSING_FACT_SECTION_SQL` — Display-Order Join Fix

The current query joins answers to steps by surrogate id:

```sql
JOIN survey.steps s ON a.step = s.id
```

`answers.step` stores a **display-order integer** (1, 2, 3 …), not a surrogate `id`. This
join coincidentally works today because steps were inserted in display-order sequence, but
it breaks under Type 2 versioning (a renamed step at display-order 1 gets a new id ≠ 1).

**Fix**:

```sql
-- INSERT_MISSING_FACT_SECTION_SQL (revised join)
-- For finalized respondents, use r.first_access_dt as the time anchor so the
-- version of the step with the matching display_order at response time is returned,
-- even if display orders were subsequently changed.
JOIN survey.steps s
  ON s.survey_id     = a.survey_id
 AND s.display_order = a.step
 AND s.effective_from <= r.first_access_dt
 AND s.effective_to   > r.first_access_dt
```

**Note on display_key and display_order versioning**: changing a step's `display_order`
(and therefore its `display_key`) is a versioning event — the old row is closed
(`effective_to = NOW()`) and a new row is inserted with the updated `display_order` and
`effective_to = '9999-12-31 23:59:59+00'`. This means `answers.step` (which stores the
display_order at answer time) always matches the `display_order` of the version row that
was active at `firstAccessDt`. Using `r.first_access_dt` as the time anchor in the ETL
join above resolves the correct historical version for every finalized respondent.
See Workflow Analysis gap ETL-3.

---

### ETL Dimension-Discovery and Value Queries — Time-Range Guards

Every SQL constant in `Sql.java` that traverses `questions`, `sections_questions`, or
`steps_sections` to discover or populate dimension values must now reference **durable
keys** and restrict to the current version using the half-open interval
`effective_from <= NOW() AND effective_to > NOW()`. There is no `is_current` column.
(`Sql.java` holds global SQL string constants; the classes that execute them carry the
actual ETL logic.)

The three surrogate joins that recur across `FIND_NEW_DIMENSION_TABLES_SQL`,
`FIND_DIMENSTION_VALUES_SQL`, `NEW_FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL`, and
`FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL`:

| Old join | Revised join |
|---|---|
| `JOIN survey.metadata m ON a.question_id = m.question_id` (surrogate) | `JOIN survey.metadata m ON q.question_id = m.question_id` (durable integer) |
| `JOIN survey.sections_questions sq ON a.section_question_id = sq.id` then `JOIN metadata m ON sq.id = m.section_question_id` | `JOIN survey.sections_questions sq ON a.section_question_id = sq.id` then `JOIN metadata m ON sq.sections_question_id = m.sections_question_id` |
| `JOIN survey.steps_sections ss ON a.step = ss.step_display_order AND a.section = ss.section_display_order` then `JOIN metadata m ON ss.id = m.step_section_id` | same display-order join on `ss`, then `JOIN metadata m ON ss.steps_sections_id = m.steps_sections_id` |

In addition, wherever `questions`, `sections_questions`, or `steps_sections` are joined
for dimension discovery (not via `answers.question_id`), add time-range guards:

```sql
AND q.effective_from <= NOW() AND q.effective_to > NOW()   -- on questions joins
AND sq.effective_from <= NOW() AND sq.effective_to > NOW() -- on sections_questions joins
AND ss.effective_from <= NOW() AND ss.effective_to > NOW() -- on steps_sections joins
```

For the finalized-respondent ETL path (`survey.answers a JOIN ... WHERE r.finalized_dt IS NOT NULL`),
the current-version time-range guard (`effective_from <= NOW() AND effective_to > NOW()`)
is the correct filter — a finalized respondent's data is already written and the current
version of each structural element is what should be reflected in the dimension tables.
The `firstAccessDt` time-filter path is only needed for the live/in-progress respondent
query path in the application layer.

---

### `ontology` and `dimensions` Tables

No structural changes required. These are stable vocabulary tables. The *association*
between a structural element and an ontology tag is captured in `metadata`, which is now
being migrated to durable keys. If a researcher changes which ontology tag is assigned to
a question (rather than just rewording the question), that is modelled by updating (or
replacing) the `metadata` row — `metadata` itself does not need Type 2 versioning because
the durable integer id on the structural element (`question_id`) persists across question
versions, and the ontology assignment travels with it.

---

## Open Questions / Design Decisions

1. **Old surrogate FK columns**: After migrating join tables to durable keys, the old
   surrogate FK columns (`step_id`, `section_id`, `question_id`, `select_group_id`, and
   the five in `relationships`) will be **dropped** once the migration is verified.
   Before dropping any column, the following tests must all pass:

   | Test | How to verify |
   |---|---|
   | All ETL SQL constants reference only durable keys and time-range guards | Code review of every string in `Sql.java` |
   | All application queries join via durable key (`{entity}_id`) not surrogate (`id`) | Code review / grep for old surrogate column names in query strings |
   | No surrogate column name appears in any Hibernate `@Column` mapping on join-table entities | Compile-time check after column removal |
   | A full ETL run against a production snapshot (anonymised) completes without error | Staging environment regression test |
   | `fact_sections` row counts match pre-migration baseline within acceptable variance | Automated count comparison in staging |
   | Spot-check: 10 random finalized respondents' `fact_sections` rows match expected dimension values | Manual QA |

   Once all six checks pass, drop the surrogate columns in a dedicated Flyway migration.
   **Resolved — drop, with the testing checklist above as the gate.**

2. **`steps_sections.display_key` stability**: `display_key` (e.g. `"0001.0001"`) is
   derived from display orders and changes when a step or section is reordered.
   Because a display_order change is a versioning event, `display_key` is stable within
   a version row's effective period. `steps_sections_id` is the durable key across
   versions. `display_key` is retained; queries use the time-range predicate with
   `firstAccessDt` to resolve the correct version. **Resolved.**

3. **Relationship `token` column**: `relationships.token` may be unique per survey, but
   the integer sequence `relationship_id` is kept as the durable key. Integer ids are
   simpler to join on, consistent with the durable-key pattern used across all other
   structural tables, and unambiguous in logs and ad-hoc SQL. **Resolved.**

4. **Application layer enforcement**: The `(durable_id, version=0)` composite `UNIQUE`
   constraint on every structural table — backed by `CHECK (_version = 0)` on every join
   table — provides database-level referential integrity for all durable-key FK references.
   The `UNIQUE` constraint (not a partial index) is required so PostgreSQL can use it as
   a FK target. No additional DB triggers or service-layer orphan checks are needed for
   entity-existence enforcement. The service layer is still responsible for business-rule
   validation (e.g. not linking a question to a section in a different survey), but
   low-level orphan prevention is handled at the database level. **Resolved.**

5. **Draft/publish workflow**: Edits are staged as drafts (effective_from = NULL,
   effective_to = NULL) and published as a deliberate step. Draft rows are invisible
   to respondent queries (the time-range predicate naturally excludes NULLs). Publishing
   atomically retires the current row and sets effective_from/effective_to on the draft.
   This is the safest model for surveys with live in-progress respondents. **Resolved.**

6. **Who can publish changes?** — The `elicit_author` role may create and edit draft rows.
   Only a user holding `elicit_author` (or a higher privilege) may publish (i.e. execute
   the atomic retire + activate transaction). Finer-grained separation (author drafts,
   separate admin role publishes) can be layered on top without schema changes. **Resolved.**

7. **Audit trail**: No separate audit table needed. Each versioned row carries two
   nullable columns: `published_by text` (username who published the change) and
   `published_comment text` (reason for the change). The same values are written to
   all rows updated in a single publish transaction — whether the change is a content
   edit or a display reorder. Draft rows and pre-migration rows carry NULL in both
   columns. **Resolved.**

---

## Migration Strategy for Existing Data

0. Create all durable key sequences: one per structural table
   (`survey.questions_durable_seq`, `survey.sections_durable_seq`, etc.).
1. Add all new `*_id` integer durable key columns with `DEFAULT nextval('survey.{table}_durable_seq')` —
   each existing row gets a unique, monotonically increasing integer automatically.
2. Set `version = 0`, `effective_from = '1970-01-01 00:00:00+00'`,
   `effective_to = '9999-12-31 23:59:59+00'` for all existing rows in all structural tables.
   `published_by` and `published_comment` remain NULL for all pre-migration rows.
   Using epoch as `effective_from` ensures that any pre-migration respondent's `firstAccessDt`
   will always satisfy `effective_from <= firstAccessDt`, eliminating the pre-versioning
   respondent gap without requiring per-survey backfill calculations.
3. Populate durable integer reference columns in join tables from their current surrogate FKs:
   - `sections_questions.question_id` (durable) ← `questions.question_id` via current surrogate `question_id`
   - `sections_questions.section_id` (durable) ← `sections.section_id` via current surrogate `section_id`
   - `steps_sections.step_id` (durable) / `section_id` (durable) ← similarly
   - `steps_sections.steps_sections_id` already generated in step 1
   - `questions.select_group_id` (durable) ← `select_groups.select_group_id` via current `select_group_id`
   - `select_items.select_group_id` (durable) ← `select_groups.select_group_id` via current `group_id`
   - `relationships.upstream_step_id` (durable) ← `steps.step_id` via current surrogate `upstream_step_id`
   - `relationships.upstream_sq_id` (durable) ← `sections_questions.sections_question_id` via current `upstream_sq_id`
   - `relationships.downstream_step_id`, `downstream_ss_id`, `downstream_sq_id` ← similarly
4. Populate durable integer columns on `metadata`:
   - `metadata.question_id` (durable) ← `questions.question_id` via `metadata.question_id` (surrogate)
   - `metadata.sections_question_id` ← `sections_questions.sections_question_id` via `metadata.section_question_id`
   - `metadata.steps_sections_id` ← `steps_sections.steps_sections_id` via `metadata.step_section_id`
   - Replace `metadata_un` unique constraint to cover the three new durable integer columns.
   - Drop `metadata_question_fk`, `metadata_sect_quest_fk`, `metadata_section_fk` constraints.
5. Populate durable integer column on `dim_step` and `dim_section`:
   - `dim_step.step_id` ← `steps.step_id` via current `dim_step.id = steps.id` (surrogate, one-time backfill)
   - `dim_section.section_id` ← `sections.section_id` similarly
6. Assert that no durable integer reference columns are NULL after all migrations above.
7. Update all SQL constants in `Sql.java` to use durable integer keys and time-range guards
   (`effective_from <= NOW() AND effective_to > NOW()`) in place of any `is_current`
   references (see Metadata/ETL section above). The executing classes pick up the updated
   constants automatically.
8. Drop old surrogate FK columns from all structural tables and `metadata` once all six
   checks in Open Question 1 pass in a staging environment.

---

## Summary of Changes

| Table | New durable key | Type 2 columns added | Surrogate FKs replaced |
|---|---|---|---|
| `questions` | `question_id` (INTEGER) | `version`, `effective_from`, `effective_to`, `published_by`, `published_comment`, `is_draft` | — (leaf table) |
| `select_groups` | `select_group_id` (INTEGER) | `version`, `effective_from`, `effective_to`, `published_by`, `published_comment`, `is_draft` | — |
| `select_items` | `select_item_id` (INTEGER) | `version`, `effective_from`, `effective_to`, `published_by`, `published_comment`, `is_draft` | `group_id` → `select_group_id` |
| `sections` | `section_id` (INTEGER) | `version`, `effective_from`, `effective_to`, `published_by`, `published_comment`, `is_draft` | — |
| `steps` | `step_id` (INTEGER) | `version`, `effective_from`, `effective_to`, `published_by`, `published_comment`, `is_draft` | — |
| `sections_questions` | `sections_question_id` (INTEGER) | `version`, `effective_from`, `effective_to`, `published_by`, `published_comment`, `is_draft` | `question_id` → durable integer, `section_id` → durable integer |
| `steps_sections` | `steps_sections_id` (INTEGER) | `version`, `effective_from`, `effective_to`, `published_by`, `published_comment`, `is_draft` | `step_id` → durable integer, `section_id` → durable integer |
| `relationships` | `relationship_id` (INTEGER) | `version`, `effective_from`, `effective_to`, `published_by`, `published_comment`, `is_draft` | 5 surrogate FK columns → durable integer columns |
| `answers` | **none** | **none** | — add `question_version` column (default 0) to pin exact question version seen; the correct `sections_questions` row is resolved at ETL time via `firstAccessDt` time-range |
| `dependents` | **none** | **none** | — event record |
| `respondents` | **none** | **none** | — `firstAccessDt` is the snapshot anchor |
| `surveys` | **none** | `published_by`, `published_comment` | — name and description changes recorded in-place (SCD Type 1); no time-range versioning |
| `metadata` | **none** | **none** | `question_id` → durable integer, `section_question_id` → `sections_question_id`, `step_section_id` → `steps_sections_id` |
| `ontology` | **none** | **none** | — stable vocabulary, no changes |
| `dimensions` | **none** | **none** | — stable vocabulary, no changes |
| `surveyreport.dim_step` | add `step_id integer UNIQUE` | **none** | ETL upsert rekey from `steps.id` → `steps.step_id` with current time-range filter |
| `surveyreport.dim_section` | add `section_id integer UNIQUE` | **none** | ETL upsert rekey from `sections.id` → `sections.section_id` with current time-range filter |
| `Sql.java` SQL constants | — | — | All structural join SQL strings updated to durable integer keys + `effective_from`/`effective_to` time-range guards; execution logic lives in the ETL service classes |

---

## Per-Table Ontology and Reporting Impact

This section answers: when a structural entity is **versioned** or **newly added**, what
changes are required in `metadata`, `ontology`, `dimensions`, and the `surveyreport` schema?

### Rule 1: Versioning an existing row — no ontology changes needed

Once durable integer keys are in place, the `metadata` row references the durable integer id (e.g.
`question_id`) rather than a surrogate `id`. The durable integer does not change across
version rows, so the existing `metadata` entry automatically covers every version of
that entity. No `metadata`, `ontology`, or `dimensions` row needs to be touched.

### Rule 2: Adding a new entity — depends on the table

| Table | `metadata` row needed? | `ontology` row needed? | `dimensions` row needed? | Reporting impact |
|---|---|---|---|---|
| `questions` | ✅ Always — link `question_id` to an ontology tag | Only if genuinely new concept | Only if new reporting dimension grouping | ETL auto-creates/populates `dim_<tag>` at next run |
| `sections_questions` | ✅ If the pairing needs an ontology tag | Only if new concept | Only if new dimension | ETL auto-discovers |
| `steps_sections` | ✅ If the pairing needs an ontology tag | Only if new concept | Only if new dimension | ETL auto-discovers |
| `steps` | ❌ No — uses `dimension_name` column directly | ❌ | ❌ | ETL upserts `dim_step` automatically via `UPDATE_STEPS_DIMENSION_TABLE_SQL` |
| `sections` | ❌ No — uses `dimension_name` column directly | ❌ | ❌ | ETL upserts `dim_section` automatically via `UPDATE_SECTIONS_DIMENSION_TABLE_SQL` |
| `relationships` | ❌ No — purely navigational; no ontology connection | ❌ | ❌ | Not represented in `surveyreport` at all |
| `dependents` | ❌ No — event record linking two `answers` rows | ❌ | ❌ | Not represented in `surveyreport` |

### Detailed notes by table

#### `steps` and `sections`

These tables bypass `metadata` and `ontology` entirely. Their `dimension_name` column
is the sole input to the reporting layer. A new step or section only requires
`dimension_name` to be set; the ETL handles `dim_step` / `dim_section` upserts
automatically on the next run.

#### `questions` — three scenarios

| Scenario | `metadata` | `ontology` | `dimensions` | Reporting schema |
|---|---|---|---|---|
| New question, existing concept | ✅ new row pointing to existing `ontology_id` | ❌ | ❌ | No change; ETL populates existing `dim_<tag>` |
| New question, new tag/concept | ✅ new row | ✅ new `ontology` row | ❌ (if no dimension grouping needed) | ETL auto-creates `dim_<tag>` table |
| New question, new reporting dimension | ✅ new row | ✅ new `ontology` row | ✅ new `dimensions` row | ETL auto-creates `dim_<name>` table and adds column to `fact_sections` |

#### `relationships`

Relationships encode conditional display logic (skip patterns, repeating steps). They
carry no ontology association and produce no dimension values. Versioning a relationship
or adding a new one affects navigational logic only and has zero impact on `metadata`,
`ontology`, `dimensions`, or the `surveyreport` schema.

#### `dependents`

An event record that links two `answers` rows through a `relationship_id`. Like `answers`
itself, it is never versioned and has no ontology concern.

### ETL self-healing

`FIND_NEW_DIMENSION_TABLES_SQL` inspects the `metadata → ontology → dimensions` chain
at runtime and identifies any `dim_*` tables that are missing from `surveyreport`.
This means the reporting schema catches up automatically on the next ETL run after
`metadata` and `ontology` rows are inserted — no manual `ALTER TABLE` is required for
the common case.

---

## Workflow Analysis

This section examines three operational workflows in chronological sequence, identifies
gaps or assumptions in the current design, and surfaces open questions that require
explicit decisions before implementation.

---

### Workflow 1: Survey Alteration — Draft and Publish Phases

#### Background

The Versioning Workflows section above treats every researcher edit as an immediate
atomic operation: close the current row, insert a new row. There is no staging concept.
Open Questions 5 and 6 ask whether a draft/publish model is needed. This section
treats it as required for safety and works out what the schema must look like to support
it.

#### Chronological sequence

```
1. RESEARCHER CREATES DRAFT
   │
   ├─ Researcher edits a question's text in the admin UI.
   ├─ System records which question_id and its current version (e.g. version=1).
   ├─ System checks for an existing draft: if one already exists for this question_id,
   │    abort and surface a conflict message.
   ├─ System inserts a new row:
   │       INSERT INTO survey.questions
   │           (question_id, version, ..., effective_from, effective_to, is_draft)
   │       VALUES (:qid, :current_version + 1, ..., NULL, NULL, true);
   │    Note: effective_from = NULL, effective_to = NULL — a draft row is not
   │    time-active. The time-range predicate `effective_from <= :ts AND effective_to > :ts`
   │    naturally evaluates to NULL (falsy) for any timestamp, so draft rows are
   │    transparently excluded from all structural queries.
   └─ All structural query paths (respondent page loads, ETL) are unaffected. ✓

2. RESEARCHER PREVIEWS DRAFT
   │
   └─ Admin preview query: SELECT ... WHERE question_id = :qid AND is_draft = true;
        This is a separate query path from both the respondent time-range path
        and ETL. No respondent sees this row.

3. RESEARCHER PUBLISHES DRAFT
   │
   ├─ BEGIN TRANSACTION
   ├─ Close current row:
   │       UPDATE survey.questions
   │          SET effective_to = NOW()
   │        WHERE question_id = :qid
   │          AND effective_to = '9999-12-31 23:59:59+00';
   ├─ Promote draft row:
   │       UPDATE survey.questions
   │          SET is_draft = false, effective_from = NOW(),
   │              effective_to = '9999-12-31 23:59:59+00'
   │        WHERE question_id = :qid AND is_draft = true;
   └─ COMMIT
        The partial unique index (question_id) WHERE effective_to = '9999-12-31 23:59:59+00'
        now covers the former draft row. The previous current row is retired with
        effective_to = NOW().

4. EFFECT ON IN-PROGRESS RESPONDENTS
   │
   └─ Any respondent with firstAccessDt < publish_time queries structural rows via
        the time-range: effective_from <= firstAccessDt AND effective_to > firstAccessDt.
        The retired version still satisfies this filter because
        effective_to = publish_time > firstAccessDt. ✓
        New respondents see the new current row via the same predicate with NOW(). ✓

5. RESEARCHER DISCARDS DRAFT (optional, before publish)
   │
   └─ DELETE FROM survey.questions WHERE question_id = :qid AND is_draft = true;
        Safe: no respondent has ever seen this row, no answers reference it, and no
        join table rows reference its surrogate id.
```

#### Schema addition required

All eight structural tables need an `is_draft` column:

These columns and indexes are part of the Author Tool's migration scripts (not the Survey
application). They are included in the per-table DDL blocks in the Schema Changes section
above. Summary:

```sql
-- is_draft column — same statement for all 8 versioned structural tables;
-- already included in each table's ALTER TABLE block above.
ADD COLUMN is_draft boolean NOT NULL DEFAULT false

-- One draft row per durable id — one index per table:
CREATE UNIQUE INDEX questions_one_draft_un        ON survey.questions        (question_id)        WHERE is_draft = true;
CREATE UNIQUE INDEX select_groups_one_draft_un    ON survey.select_groups    (select_group_id)    WHERE is_draft = true;
CREATE UNIQUE INDEX select_items_one_draft_un     ON survey.select_items     (select_item_id)     WHERE is_draft = true;
CREATE UNIQUE INDEX sections_one_draft_un         ON survey.sections         (section_id)         WHERE is_draft = true;
CREATE UNIQUE INDEX steps_one_draft_un            ON survey.steps            (step_id)            WHERE is_draft = true;
CREATE UNIQUE INDEX sections_questions_one_draft_un ON survey.sections_questions (sections_question_id) WHERE is_draft = true;
CREATE UNIQUE INDEX steps_sections_one_draft_un   ON survey.steps_sections   (steps_sections_id)  WHERE is_draft = true;
CREATE UNIQUE INDEX relationships_one_draft_un    ON survey.relationships    (relationship_id)    WHERE is_draft = true;
```

A draft row has: `is_draft = true`, `effective_from = NULL`, `effective_to = NULL`.
A published (current) row has: `is_draft = false`, `effective_from = <timestamp>`, `effective_to = '9999-12-31 23:59:59+00'`.
A retired row has: `is_draft = false`, `effective_from = <timestamp>`, `effective_to = <timestamp that is not the max sentinel>`.

All respondent queries and ETL queries use only the time-range predicate
`effective_from <= :ts AND effective_to > :ts`. Draft rows (both NULL) are automatically
excluded because `NULL <= :ts` evaluates to `NULL` (falsy). No additional `AND is_draft = false`
filter clause is required in respondent queries.

#### Cascade question: does drafting one entity require drafting its dependents?

In most versioning scenarios the answer is no, because join tables (`sections_questions`,
`steps_sections`, `relationships`) reference durable integer ids that do not change when
the referenced entity is versioned. A question reword creates a new `questions` row but
the `sections_questions` row is unchanged; it resolves the question via `question_id`
(durable). No draft of `sections_questions` is needed for a question reword.

An exception arises when the researcher changes the *structure* — for example, moving a
question to a different section (which requires closing the old `sections_questions` row
and creating a new one) or adding conditional logic (a new `relationships` row). In those
cases the affected join-table rows should be included in the same draft set and published
atomically with the question row.

#### Effects on other system parts when a draft is published

| System part | Effect |
|---|---|
| Respondents with `firstAccessDt` before publish time | None — time-filter continues to resolve old version ✓ |
| Respondents whose `firstAccessDt` is set after publish | See new version immediately ✓ |
| `answers` already written | Unaffected — surrogate `question_id` pins the exact version row ✓ |
| `metadata` | Only affected if the ontology assignment changes; see Workflow 3 |
| ETL (finalized respondents already in `fact_sections`) | Unaffected — already processed |
| ETL (future runs) | Joins via current time-range (`effective_from <= NOW() AND effective_to > NOW()`) now resolve the new version |
| `dim_step` / `dim_section` | Updated on next ETL run if step or section was the drafted entity |

#### Open questions — Survey Alteration

- **Q-A1**: A draft row may be UPDATEd in-place (content columns only; `question_id`,
  `version`, `effective_from`, `effective_to`, and `is_draft` must not change) before
  publishing. No respondent has seen the row; no answers reference it. The service layer
  must restrict UPDATE to rows where `is_draft = true`. **Resolved.**
- **Q-A2**: All `is_draft = true` rows across all structural tables for a given survey
  are published atomically in a single `BEGIN`/`COMMIT` transaction. There is no separate
  "changeset" object — the draft set is simply every row with `is_draft = true` at publish
  time. The `published_by` and `published_comment` values are identical for every row
  promoted in that transaction. The `_one_draft_un` partial unique index already enforces
  at most one draft per durable id, preventing two open drafts for the same entity.
  **Resolved.**
- **Q-A3**: A `metadata` UPDATE must be inside the publish transaction when the author
  changes a question's ontology classification alongside a content change. Concrete
  example:
  - Current state: `questions` row for `question_id = 42`, version 1, wording
    "Do you feel hopeless?"; `metadata` row links `question_id = 42` to ontology tag
    `depression_score`.
  - Author creates a draft: new `questions` row (version 2, revised wording) **and**
    updates the `metadata` row to reclassify to `anxiety_score`.
  - The publish transaction must include all three statements in one `BEGIN`/`COMMIT`:
    1. `UPDATE questions SET effective_to = NOW() WHERE question_id = 42 AND effective_to = '9999-12-31 23:59:59+00'`
    2. `UPDATE questions SET is_draft = false, effective_from = NOW(), effective_to = '9999-12-31 23:59:59+00' WHERE question_id = 42 AND is_draft = true`
    3. `UPDATE metadata SET ontology_id = :new_id WHERE question_id = 42`
  - If step 3 is committed separately, there is a window where new respondents see the
    new question wording still classified under `depression_score`. **Resolved —
    metadata UPDATE must always be inside the publish transaction.**
- **Q-A4**: No rollbacks. If a published change must be reversed, the `elicit_author`
  creates a new draft restoring the prior content and publishes it as the next version.
  Respondents whose `firstAccessDt` falls within the now-superseded version's active
  period remain correctly pinned to that version via the time-range predicate — their
  answers are unaffected. The sequence of retired rows (`effective_from`/`effective_to`)
  provides a complete audit trail of the round-trip change. **Resolved.**
- **Q-A5**: One draft set at a time. See Q-A2 resolution. The `_one_draft_un` unique
  index rejects a second draft insert for the same durable id; the service layer surfaces
  this as "a draft already exists — publish or discard it before editing again."
  **Resolved.**

---

### Workflow 2: Respondent Survey Workflow

#### Chronological sequence

```
1. RESPONDENT OPENS SURVEY FOR THE FIRST TIME
   │
   ├─ System identifies the respondent by token (URL parameter or session cookie).
   ├─ No respondent row exists yet (first login via token):
   │       UPDATE survey.respondents
   │          SET first_access_dt = NOW()
   │        WHERE token = :token AND first_access_dt IS NULL;
   └─ firstAccessDt is now frozen. All subsequent structural queries use this timestamp.
      Note: firstAccessDt is set on the very first token login. A returning respondent
      always has a non-null firstAccessDt — there is no null case on return.

2. STRUCTURAL QUERIES — NEW RESPONDENT (firstAccessDt just set)
   │
   ├─ Query uses the time-range predicate with firstAccessDt:
   │       effective_from <= :firstAccessDt AND effective_to > :firstAccessDt
   │    Because firstAccessDt was just set to NOW(), this is equivalent to a NOW()
   │    query at this instant, but using firstAccessDt consistently means the same
   │    code path handles both first-access and returning respondents.
   ├─ Draft rows are excluded because both effective_from and effective_to are NULL.
   └─ The question, section, step, and relationship rows returned are all current. ✓

3. RESPONDENT ANSWERS QUESTIONS
   │
   ├─ Each answer is written to survey.answers with:
   │       question_id          = questions.id  (SURROGATE — exact version row)
   │       question_version     = questions.version  (version number seen)
   │       section_question_id  = sections_questions.id  (SURROGATE — exact version row)
   │   These two surrogate ids permanently pin which version row the respondent saw.
   └─ answers.step and answers.section store display_order integers, not surrogate ids.

4. RESPONDENT RETURNS (in a later session)
   │
   ├─ System retrieves firstAccessDt from respondents where token = :token.
   │    firstAccessDt is always non-null at this point — it is set on the respondent's
   │    very first token login and never cleared.
   ├─ Structural queries now use the time-range with firstAccessDt:
   │       effective_from <= :firstAccessDt
   │       AND effective_to > :firstAccessDt
   ├─ If the researcher published a change (including a new required question) between
   │    the respondent's firstAccessDt and now, the new version has
   │    effective_from > firstAccessDt and is excluded. The respondent sees the survey
   │    exactly as it was at firstAccessDt — new required questions are not shown.
   └─ The respondent continues to see exactly the same questions as on first access. ✓

5. RESPONDENT SUBMITS / FINALIZES
   │
   ├─ System sets respondents.finalized_dt = NOW().
   └─ No structural changes are required. The answers rows already contain the exact
        version-pinned surrogate ids.
```

#### Pre-versioning respondents: RESOLVED

**Decision**: Migration step 2 sets `effective_from = '1970-01-01 00:00:00+00'` (epoch)
and `effective_to = '9999-12-31 23:59:59+00'` (max) for all existing structural rows.
Epoch precedes any real `firstAccessDt` by definition, so the time-range predicate
`effective_from <= :firstAccessDt AND effective_to > :firstAccessDt` always returns
pre-migration rows for any respondent. No per-survey backfill calculation is needed.
This closes the gap for all pre-migration in-progress respondents.

#### Draft items and respondent queries

If a draft for question X exists at the time a new respondent starts, the draft row has
`is_draft = true`, `effective_from = NULL`, `effective_to = NULL`. The time-range predicate
`effective_from <= :ts AND effective_to > :ts` evaluates to NULL (falsy) for any timestamp.
Drafts are transparently invisible to all respondents at all times without any additional
filter clause.

#### Edge cases

**Edge case R-1: A structural element is retired entirely (no replacement) after a
respondent's firstAccessDt.**

The retirement sets `effective_to = NOW()` on the final version row.
For a respondent with `firstAccessDt < NOW()`, the query returns that retired row
because `effective_to = NOW() > firstAccessDt`. The respondent still sees the question
for the duration of their session. When they finalize, the answer is written with the
correct surrogate. ✓ Consistent behavior.

**Edge case R-2: The display_order of a step changes between versions.**

**Decision**: Changing a step's `display_order` is a versioning event — the old row is
closed and a new row is inserted with the updated display_order. `answers.step` stores
the display_order that was in effect when the respondent answered. ETL for finalized
respondents must resolve the step using `r.first_access_dt` (not `NOW()`) so that the
correct historical version of the step — with the matching `display_order` — is returned:

```sql
JOIN survey.steps s
  ON s.survey_id     = a.survey_id
 AND s.display_order = a.step
 AND s.effective_from <= r.first_access_dt
 AND s.effective_to   > r.first_access_dt
```

The respondent's survey experience is consistent throughout their session because the
time-range query with `firstAccessDt` returns the same display_order every page load.

**Edge case R-3: `firstAccessDt` is set but the respondent's record is administratively
reset.**

If an admin operation clears `firstAccessDt` (or deletes and re-creates the respondent
row), the respondent will be treated as a first-time visitor on next access and will
receive the current version of all structural elements. Their previously written answers
still reference old surrogate ids pointing to old version rows — a structural inconsistency.
The admin reset operation must either: (a) preserve `firstAccessDt`, or (b) also delete
all existing answers for that respondent.

**Edge case R-4: The application layer caches the structural snapshot for a session.**

If the application caches structural query results in a user session (rather than
re-querying on each page load), a publish event during the session has no effect on the
cached snapshot. This is correct behavior — it's equivalent to the time-filter approach.

**Cache invalidation (Q-R3 resolved)**: The Author Tool clears all application caches
as part of the publish transaction commit. Respondents whose sessions are active at the
moment of publish will continue to use their cached snapshot (which is safe — it was
valid at `firstAccessDt`). Any structural re-query after the cache clear will use
`firstAccessDt` from the respondents table, not `NOW()`, so the correct historical
version is returned regardless of when the cache miss occurs.

#### Open questions — Respondent Workflow

- **Q-R1** ✅ **Resolved — publish timestamp boundary**
  The publish transaction sets `effective_to = :publish_ts` on the expiring version row
  and `effective_from = :publish_ts` on the new version row — the **same timestamp** in
  the same `BEGIN`/`COMMIT` block. The half-open interval
  `effective_from <= :ts AND effective_to > :ts` guarantees exactly one row is visible
  at any point in time:
  - `ts < publish_ts` → old row returned; new row excluded (`effective_from > ts`)
  - `ts = publish_ts` → new row returned; old row excluded (`effective_to = ts`, not `> ts`)
  - `ts > publish_ts` → new row returned only

  No increment between the two timestamps is needed. Using the same value avoids both
  gaps (a window where no row matches) and overlaps (two rows matching simultaneously).
  PostgreSQL `TIMESTAMPTZ` has microsecond precision, which is more than sufficient.

- **Q-R2** ⏸️ **Deferred** — Token timeout duration will be determined by asking Survey
  Authors what an appropriate expiry period is for in-progress respondents. No schema
  change is required until that decision is made.

- **Q-R3** ✅ **Resolved** — The Author Tool clears all application caches as part of
  the publish operation (see Edge case R-4 above). Any post-publish structural query
  always uses `firstAccessDt` from the respondents table, not `NOW()`.

---

### Workflow 3: Post-Completion ETL Workflow

#### Chronological sequence

```
1. ETL TRIGGER
   │
   └─ Triggered on schedule or by finalization event.

2. DIMENSION TABLE MAINTENANCE (must run before fact inserts)
   │
   ├─ UPDATE_STEPS_DIMENSION_TABLE_SQL:
   │       INSERT INTO surveyreport.dim_step (step_id, value)
   │       SELECT s.step_id, s.dimension_name FROM survey.steps s
   │        WHERE s.effective_from <= NOW() AND s.effective_to > NOW()
   │       ON CONFLICT (step_id) DO UPDATE SET value = excluded.value;
   │
   ├─ UPDATE_SECTIONS_DIMENSION_TABLE_SQL: same pattern for sections.
   │
   └─ This is SCD Type 1 on dim_step / dim_section — the dimension label is updated
        in place when a step or section is renamed. All historical fact rows will now
        report under the new label. See gap ETL-5 below.

3. DISCOVER NEW DIMENSION TABLES (must run before fact population)
   │
   └─ FIND_NEW_DIMENSION_TABLES_SQL: inspects metadata → ontology → dimensions,
        creates any missing dim_* tables in surveyreport.

4. FIND FINALIZED RESPONDENTS WITH UNPROCESSED ANSWERS
   │
   └─ SELECT r.id, r.first_access_dt, a.*
        FROM survey.respondents r
        JOIN survey.answers a ON a.respondent_id = r.id
       WHERE r.finalized_dt IS NOT NULL
         AND NOT EXISTS (SELECT 1 FROM surveyreport.fact_sections f
                          WHERE f.respondent_id = r.id AND f.question_id = a.question_id);

5. RESOLVE STRUCTURAL CONTEXT FOR EACH ANSWER
   │
   ├─ Join answers → questions via SURROGATE:
   │       JOIN survey.questions q ON q.id = a.question_id
   │    This resolves to the exact version row the respondent saw, regardless of
   │    whether that version is still current. ✓
   │
   ├─ Join questions → metadata via DURABLE key:
   │       JOIN survey.metadata m ON m.question_id = q.question_id
   │    This resolves to the current metadata row — see gap ETL-1.
   │
   ├─ Join steps via display_order using respondent’s firstAccessDt:
   │       JOIN survey.steps s
   │           ON s.survey_id     = a.survey_id
   │          AND s.display_order = a.step
   │          AND s.effective_from <= r.first_access_dt
   │          AND s.effective_to   > r.first_access_dt
   │    Using firstAccessDt (not NOW()) guarantees the step version with the matching
   │    display_order is returned even if the step was reordered after finalization.
   │
   └─ Populate surveyreport.fact_sections and dim_* columns.

6. MARK RESPONDENT AS PROCESSED
   │
   └─ Update fact_sections or a processing log to prevent re-processing.
```

#### Gap ETL-1: Metadata ontology reclassification — policy decision required

The finalized-respondent ETL resolves metadata via the current metadata row (no version
on `metadata`). This is correct when the ontology assignment is stable. However:
- Respondent R finalizes at T1. They answered question Q version 0, which is tagged with
  ontology "depression_score".
- At T2 > T1, a researcher changes the metadata row so that question Q now maps to
  ontology "anxiety_score".
- ETL runs at T3 > T2.
- The ETL joins `answers → questions` via surrogate (gets version 0 row ✓).
- The ETL joins `questions → metadata` via durable `question_id` (gets the updated
  metadata row pointing to "anxiety_score").
- Respondent R's finalized answer is classified under "anxiety_score" in `fact_sections`
  even though R answered with "depression_score" framing. ❌

**This is a retroactive ontology reclassification**, not a bug in the query logic —
it is a consequence of the design decision (documented in the Metadata/Ontology section)
that "`metadata` itself does not need Type 2 versioning."

**Decision — Option A (accept retroactive reclassification). RESOLVED.**

A metadata change almost always represents a clarification of language rather than a
truly new data point. Reclassifying all historical responses when the ontology mapping
is updated is intentional and correct behavior. Researchers must understand that editing
a `metadata` row retroactively reclassifies all finalized respondents for that question.
No schema change to `metadata` is required. The ETL join remains:

```sql
JOIN survey.metadata m ON m.question_id = q.question_id
```

This always resolves to the current (updated) metadata row, which is the desired result.

#### Gap ETL-2: `answers.question_version` DEFAULT and backfill — RESOLVED

`section_question_version` is not needed. The correct `sections_questions` row is always
resolvable via the `firstAccessDt` time-range join — the same mechanism used for every
other structural table. Storing an explicit pin for a placement join table adds no
information beyond what `firstAccessDt` already provides.

`question_version` IS worth storing explicitly: it pins the exact question wording the
respondent saw without requiring a time-range join on `questions`, and it is the primary
answered entity.

`question_version` is also the FK companion column in `sections_questions` — the
`(question_id, question_version)` FK constraint (where `question_version` is always `0`)
serves as the DB-level entity-existence check. The same pattern applies in `relationships`
for all five reference columns.

**Required DDL:**

```sql
ALTER TABLE survey.answers
    ADD COLUMN question_version integer NOT NULL DEFAULT 0;

-- Backfill is a no-op because DEFAULT 0 already applies to all existing rows,
-- but an explicit UPDATE makes the intent clear in the migration:
UPDATE survey.answers SET question_version = 0 WHERE question_version IS NULL;
```

The `DEFAULT 0` ensures that if any application path forgets to explicitly write the
version, the row is still valid (defaulting to version 0 rather than breaking the
reporting query). Going forward, the application layer must write `question_version` at
answer time by reading the `version` field from the structural query result.

**Resolved.** DDL is defined above; `DEFAULT 0` + backfill handles all pre-migration rows.

#### Gap ETL-3: display_order step join — RESOLVED

**Decision**: Changing `display_order` on a step or section is a versioning event (close
old row with `effective_to = NOW()`, insert new row with updated `display_order` and
`effective_to = '9999-12-31 23:59:59+00'`). This preserves the invariant that each
distinct `(survey_id, display_order)` pair is served by exactly one version row at any
given point in time.

For ETL of finalized respondents, the step join must use `r.first_access_dt` (not `NOW()`)
so that the correct historical display_order is resolved:

```sql
JOIN survey.steps s
  ON s.survey_id     = a.survey_id
 AND s.display_order = a.step
 AND s.effective_from <= r.first_access_dt
 AND s.effective_to   > r.first_access_dt
```

This join is exact regardless of subsequent display_order changes. No `answers.step_id`
column is needed. `answers.step` (display_order integer) remains a valid and sufficient
pin to the exact step version the respondent experienced.

#### Gap ETL-4: ETL execution order — RESOLVED

The SQL constants in `Sql.java` (a global constants holder) are executed by the ETL
service classes in a required dependency order. The order below must be enforced by the
orchestrating ETL service class and must not change:

| Order | SQL constant | Dependency |
|---|---|---|
| 1 | `UPDATE_STEPS_DIMENSION_TABLE_SQL` | Must run before any step fact inserts |
| 2 | `UPDATE_SECTIONS_DIMENSION_TABLE_SQL` | Must run before any section fact inserts |
| 3 | `FIND_NEW_DIMENSION_TABLES_SQL` | Creates missing `dim_*` tables |
| 4 | `FIND_DIMENSTION_VALUES_SQL` | Discovers current dimension values |
| 5 | `NEW_FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL` | Depends on dim_* tables existing |
| 6 | `FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL` | Same |
| 7 | `INSERT_MISSING_FACT_SECTION_SQL` | Depends on dim rows existing for FK resolution |

This ordering is now documented here as the ETL contract. The executing service class
is responsible for calling these constants in this sequence.

**Resolved.**

#### Gap ETL-5: SCD Type 1 on `dim_step` / `dim_section` — RESOLVED (intentional)

When a step or section is renamed, the `UPDATE_STEPS_DIMENSION_TABLE_SQL` upsert updates
the `value` column in-place. This is SCD Type 1: all historical `fact_sections` rows
that reference `dim_step.id` will display the current step name in any report query.

**Decision**: SCD Type 1 on `dim_step` / `dim_section` is intentional. Renaming a step
or section is considered a simple clarification edit. All respondents (historical and
future) should see the current step/section name in reports. There is no requirement to
preserve the label that was displayed to any individual respondent at the time they
responded. If that requirement arises in future, `dim_step` would need to become SCD
Type 2 with date columns and `fact_sections` would need to store the dim surrogate active
at `firstAccessDt`.

#### Open questions — ETL Workflow

- **Q-E1** ✅ **Resolved — Option A (accept retroactive reclassification)**
  Metadata changes are almost always language clarifications, not new data points.
  All historical responses are intentionally reclassified when a `metadata` row changes.
  The ETL join `metadata m ON m.question_id = q.question_id` is correct as-is.
- **Q-E2** ✅ **Resolved** — The step-join time anchor in all ETL paths is always
  `r.first_access_dt`, even for steps where no reordering has occurred. This is logically
  correct, adds no meaningful overhead (the column is already in the result set), and
  ensures the anchor is consistently in place for the cases where reordering has occurred.
  Using `NOW()` as a fallback is never appropriate in the finalized-respondent ETL path.
- **Q-E3** ✅ **Resolved** — SCD Type 1 on `dim_step` / `dim_section` is intentional.
  See Gap ETL-5.
- **Q-E4** ✅ **Resolved** — ETL error handling policy: skip-and-log. If
  `INSERT_MISSING_FACT_SECTION_SQL` fails for a specific respondent (e.g. no matching
  step row due to an unexpected data gap), that respondent is logged with the error
  reason and skipped for the current run. The `NOT EXISTS` deduplication guard in the
  insert query means the next scheduled ETL run will automatically retry all previously
  skipped respondents. This avoids blocking an entire batch over one bad row while still
  guaranteeing eventual processing.
- **Q-E5** ✅ **Resolved** — Pre-migration respondents whose `firstAccessDt` predates the
  migration: migration step 2 sets `effective_from = '1970-01-01 00:00:00+00'` which
  precedes all real `firstAccessDt` values. The surrogate join from `answers → questions`
  resolves the exact version row. The firstAccessDt-anchored step join also resolves
  correctly since epoch < firstAccessDt < effective_to for all migrated rows.

---

### Cross-Workflow Summary of Gaps and Decisions Required

| ID | Workflow | Gap / Decision | Severity |
|---|---|---|
---|
| A-1 | Alteration | `is_draft` column and `_one_draft_un` partial unique indexes added to all 8 structural tables (see Schema Changes DDL blocks above) | ✅ Resolved |
| A-2 | Alteration | Atomic publish of multi-entity changesets — all `is_draft = true` rows published in one transaction with shared comment | ✅ Resolved |
| A-3 | Alteration | Metadata ontology change must be inside the publish transaction (see Q-A3 example) | ✅ Resolved |
| A-4 | Alteration | No rollbacks — reverse a change by publishing a new version restoring prior content | ✅ Resolved |
| R-1 | Respondent | Pre-versioning respondents: epoch `effective_from` in migration step 2 closes the gap | ✅ Resolved |
| R-2 | Respondent | `answers.question_version` column added with `DEFAULT 0` and backfill; written at answer time (see Gap ETL-2) | ✅ Resolved |
| R-3 | Respondent | `display_order` change = new version event; ETL uses `firstAccessDt` anchor; no `answers.step_id` needed | ✅ Resolved |
| R-4 | Respondent | Snapshot consistency guaranteed by atomic publish transaction: all `is_draft = true` rows promoted in one `BEGIN`/`COMMIT`; `firstAccessDt` either precedes or follows commit — no partial-publish state is visible | ✅ Resolved |
| E-1 | ETL | Metadata ontology reclassification — Option A accepted: metadata changes retroactively reclassify all respondents; no versioning of `metadata` required | ✅ Resolved |
| E-2 | ETL | `answers.question_version`: add with `DEFAULT 0`, backfill, write at answer time; `section_question_version` dropped as redundant — `firstAccessDt` covers it | ✅ Resolved |
| E-3 | ETL | ETL step join uses `firstAccessDt` anchor; display_order change = new version; exact resolution guaranteed | ✅ Resolved |
| E-4 | ETL | ETL execution order documented above (steps 1–7); enforced by the orchestrating service class | ✅ Resolved |
| E-5 | ETL | SCD Type 1 on `dim_step`/`dim_section` — intentional; all respondents see current step/section names | ✅ Resolved |
