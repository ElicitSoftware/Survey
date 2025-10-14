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

CREATE SEQUENCE survey.excluded_xids_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.excluded_xids_seq TO ${survey_user};
CREATE TABLE survey.excluded_xids
(
    id          integer                NOT NULL,
    xid         character varying(255) NOT NULL,
    department  integer NOT NULL,
    reason      character varying(500), -- Optional: reason for exclusion
    created_dt  timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  character varying(100), -- Optional: who added the exclusion
    CONSTRAINT excluded_xids_pk PRIMARY KEY (id),
    CONSTRAINT excluded_xids_xid_dept_un UNIQUE (xid, department)
);
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.excluded_xids TO ${survey_user};
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.excluded_xids TO ${surveyadmin_user};
