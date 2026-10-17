<!--
  One pull request per issue, from a branch named <type>/<issue>-<slug> off V3.
  Title: what the change does, in the imperative, without the issue number.
  Tick every line that applies; strike one through (~~like this~~) with a reason when it does not.
  An unticked, unstruck line is a question the reviewer will ask.
-->

## Issue

Closes #

## What changed and why

<!-- Two to five sentences. Behavior first, implementation second. -->

## AIUP

<!-- docs/ is the source of truth; the docs commit comes before the code commit. -->

- [ ] `docs/requirements.md` updated (IDs: ) or no requirement changed
- [ ] `docs/use_cases.puml` and the use case spec(s) updated (IDs: ) or no behavior changed
- [ ] `docs/entity_model.md` updated or no entity changed
- [ ] Tests cite the UC and BR IDs they cover

## Checks

- [ ] Flyway migration added to **both** `db/migration` and `db/migration-v3`, and the brownfield track verified with `resetDatabase.sh V2` from the umbrella
- [ ] A reporting (star schema) change keeps `report_<slug>` building from the apply, and FHHS still reads it
- [ ] New user-facing text has its key in `translations.properties`, `i18n/translations_es_419.properties` and `i18n/translations_ar.properties`, a context note, and `i18n/TRANSLATION_REQUEST.md` regenerated
- [ ] Respondent credential is called the **access code**, never a token
- [ ] Cross-module change: the tracking issue in `ElicitSoftware/Elicit` and the sibling PRs are linked above
- [ ] No AI co-author trailer in any commit message
