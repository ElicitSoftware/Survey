# Kimball Type 2 Greenfield/Brownfield Convergence Gaps

## Context

Survey ships two parallel Flyway tracks for the same end-state schema: `db/migration/`
(greenfield — a brand-new database creates the Kimball Type 2 schema directly via `CREATE
TABLE`) and `db/migration-v3/` (brownfield — an existing v2.x database gets there via `ALTER
TABLE` in `V010__Kimball_Type2_SCD.sql`). Both tracks are supposed to converge on an identical
resulting schema; `V010`'s own comments assert this. While validating Admin's Kimball Type 2
implementation against the real shipped schema (reading both migration files, then confirming
against a live, already-brownfield-migrated Postgres instance in the shared
`docker-compose.yml` stack), two places where that convergence claim didn't actually hold were
found.

## Findings

### Finding #1 — `metadata_element_ck` checked the wrong columns on brownfield-upgraded databases

Greenfield's `db/migration/V001__Create_Survey_Schema.sql` creates `survey.metadata` with only
the durable columns (`steps_sections_id`, `question_id`, `sections_question_id`) and its
`metadata_element_ck` CHECK is `(steps_sections_id + question_id + sections_question_id) > 0`.

Brownfield's `db/migration-v3/V010__Kimball_Type2_SCD.sql` instead renames the three old
surrogate columns to `*_surrogate` suffixes (`question_id_surrogate`,
`section_question_id_surrogate`, `step_section_id_surrogate` — never dropped) and adds the
three durable columns alongside. Postgres automatically rewrites a CHECK constraint's column
references when the underlying column is renamed, so after the three `RENAME COLUMN`
statements, `metadata_element_ck` kept enforcing "at least one element set" against the now
permanently-`NULL` `*_surrogate` columns — never against the durable columns that rows are
actually populated with going forward. Net effect on an upgraded database: `NULL + NULL + NULL
> 0` evaluates to `NULL`, which a CHECK constraint treats as satisfied (not `FALSE`), so a
metadata row with no durable element actually set would silently pass. Confirmed live via `\d
survey.metadata` against the docker-compose stack's DB (which took the brownfield path):
`metadata_element_ck CHECK ((step_section_id_surrogate + question_id_surrogate +
section_question_id_surrogate) > 0)`.

**Status: Fixed and verified.** `db/migration/V011__Fix_Metadata_Element_Check.sql` (an
idempotent no-op re-assertion of the already-correct constraint, letting
`ManualSchemaMigrator`'s repair-boot path pick it up) and
`db/migration-v3/V011__Fix_Metadata_Element_Check.sql` (the real fix — drops and re-adds
`metadata_element_ck` against the durable columns) both exist. Verified by building a second,
genuinely fresh database in the same Postgres cluster, applying only `db/migration`'s files to
it directly via `psql` (bypassing dev-mode live-reload), and diffing the result against the
live upgraded database: schema, columns, constraints, indexes, and sequences are now identical
between the two tracks — `metadata_element_ck` included — except for two already-accepted,
intentionally-deferred differences: the brownfield database's three leftover `*_surrogate`
columns and their indexes (gated behind a documented 6-check staging verification before they
can be dropped — a separate, later effort, not part of this fix), and physical column ordering
on the ALTER-based tables (confirmed harmless — no `SELECT *` or positional column access
anywhere in the codebase).

### Finding #2 — `survey.dimensions_seq` never granted `USAGE` to `survey_user` on a fresh greenfield install

Every other sequence created in `db/migration/V001__Create_Survey_Schema.sql` gets `GRANT
USAGE ON SEQUENCE ... TO ${survey_user}` immediately after its `CREATE SEQUENCE` statement.
`survey.dimensions_seq` is the one exception — that grant line was simply never written for
it. `db/migration-v3/V010__Kimball_Type2_SCD.sql` already patches this for upgraded databases,
with a comment acknowledging the gap explicitly: *"Pre-existing gap in
V001__Create_Survey_Schema.sql (frozen history, cannot be fixed there): every other sequence
gets GRANT USAGE except this one. Harmless to add here since a new migration can't
retroactively edit an already-applied one."* But the greenfield file itself was never
corrected, so a brand-new greenfield install still lacks this grant today — the inverse
situation of Finding #1 (there, brownfield was the buggy side; here, greenfield is).

**Status: Fixed.** `db/migration/V012__Grant_Dimensions_Seq_Usage.sql` adds the real grant on
the greenfield track. `db/migration-v3/V012__Grant_Dimensions_Seq_Usage.sql` mirrors it at the
same version number for the two tracks' numbering to stay aligned — since `V010` already
granted this there, it's a harmless, idempotent re-grant (Postgres `GRANT` doesn't error on
re-granting an already-held privilege).

## Open question (not verified, flag for follow-up)

Whether `surveyreport.dim_step`/`dim_section`'s durable rekey columns (added via `ALTER` in
`db/migration-v3/V010`'s step 13) are already present natively in greenfield's
`db/migration/V002__Create_Reporting_Schema.sql` `CREATE TABLE` statements. Not read or
confirmed as part of this pass — worth a quick check before assuming full convergence extends
to the `surveyreport` schema as well as `survey`.
