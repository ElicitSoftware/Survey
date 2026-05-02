---
-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

-- Create post_survey_actions table in Survey service.
-- Keep idempotent so upgrades succeed if FHHS already created these objects.
CREATE SEQUENCE IF NOT EXISTS survey.post_survey_actions_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.post_survey_actions_seq TO ${survey_user};

CREATE TABLE IF NOT EXISTS survey.post_survey_actions
(
    id              integer                 NOT NULL,
    survey_id       integer                 NOT NULL,
    name            character varying(255)  NOT NULL,
    description     character varying(1000),
    url             character varying(500)  NOT NULL,
    execution_order integer                 NOT NULL DEFAULT 1,
    CONSTRAINT post_survey_actions_pk PRIMARY KEY (id),
    CONSTRAINT post_survey_actions_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id)
);

CREATE INDEX IF NOT EXISTS post_survey_actions_survey_index
ON survey.post_survey_actions USING btree (survey_id ASC NULLS LAST);

GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.post_survey_actions TO ${survey_user};
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.post_survey_actions TO ${surveyadmin_user};
