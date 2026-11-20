---
-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

SET TIMEZONE TO 'America/Detroit';

-- SectionView's render switch has always matched on DATE_TIME_PICKER
-- (GlobalStrings.QUESTIION_TYPE_DATE_TIME_PICKER), but V003 seeded the row as
-- DATETIME. The switch has no default case, so a DATETIME question fell through
-- and rendered nothing at all. Rename the row rather than add a second one, so
-- existing questions keep their type_id and start rendering.
UPDATE survey.question_types
SET name = 'DATE_TIME_PICKER'
WHERE name = 'DATETIME';

-- TIME_PICKER and MODAL have render cases in SectionView and component wrappers
-- in flow/input, but were never seeded, so no survey definition could reference
-- them. Ids are explicit rather than NEXTVAL: .elicit files carry questions.type_id
-- verbatim (SurveyDefinitionImportService imports the lookup ids as-is), so a
-- definition is only portable if the same type has the same id at every site.
INSERT INTO survey.question_types (id, name, data_type, description)
VALUES (15, 'TIME_PICKER', 'Text', 'Time picker')
ON CONFLICT DO NOTHING;

INSERT INTO survey.question_types (id, name, data_type, description)
VALUES (16, 'MODAL', '', 'Modal informational dialog')
ON CONFLICT DO NOTHING;

-- Keep the sequence ahead of the explicit ids above.
SELECT setval('survey.question_types_seq', (SELECT max(id) FROM survey.question_types));
