--
-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
--

--------------------------------
-- Mirrors db/migration's V013 at the same version number (see that file for the full
-- explanation of the defect and why the rewrite is ordered the way it is).
--
-- This track needs the same correction on its own merits, not just for version alignment:
-- this track's V010 created the identical original function body, with the same
-- unconditional epoch -> now() rewrite of effective_from. The difference is that a
-- brownfield database is where it matters most -- its pre-existing rows were backfilled to
-- the epoch by V010's ADD COLUMN default, and its respondents carry first_access_dt values
-- from before the upgrade, so any row inserted after the upgrade with the epoch default
-- would be stamped "created now" and become invisible to exactly those respondents.
--
-- CREATE OR REPLACE, and the body is byte-identical to the greenfield V013, so a database
-- that converges onto db/migration after repair() ends up with the same function either
-- way. Idempotent.
--------------------------------
CREATE OR REPLACE FUNCTION survey.scd_close_predecessor() RETURNS trigger AS $$
DECLARE
    durable_col     text := TG_ARGV[0];
    durable_val     integer;
    has_predecessor boolean := false;
BEGIN
    -- Only a row claiming to be current can supersede anything.
    IF NEW.effective_to = '9999-12-31 23:59:59+00' THEN
        EXECUTE format('SELECT ($1).%I', durable_col) INTO durable_val USING NEW;
        EXECUTE format(
            'SELECT EXISTS (SELECT 1 FROM %I.%I WHERE %I = $1 AND effective_to = $2 AND id <> $3)',
            TG_TABLE_SCHEMA, TG_TABLE_NAME, durable_col
        ) INTO has_predecessor
        USING durable_val, '9999-12-31 23:59:59+00'::timestamptz, NEW.id;
    END IF;

    -- Epoch means "has existed since forever", which is right for the first version of
    -- a durable id. It is only wrong when superseding a predecessor, where the two
    -- versions' validity ranges have to meet at a real instant.
    IF has_predecessor AND NEW.effective_from = '1970-01-01 00:00:00+00' THEN
        NEW.effective_from := now();
    END IF;

    -- Close the predecessor at this row's start instant, so the ranges abut with no
    -- gap and no overlap. Runs after the rewrite above for exactly that reason.
    IF has_predecessor THEN
        EXECUTE format(
            'UPDATE %I.%I SET effective_to = $1 WHERE %I = $2 AND effective_to = $3 AND id <> $4',
            TG_TABLE_SCHEMA, TG_TABLE_NAME, durable_col
        ) USING NEW.effective_from, durable_val, '9999-12-31 23:59:59+00'::timestamptz, NEW.id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
