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
-- Align survey.scd_close_predecessor() with its documented contract.
--
-- As first written (V001 greenfield / V010 brownfield) the function rewrote
-- effective_from from the epoch sentinel to now() in an unconditional IF, evaluated
-- before and independently of the predecessor lookup. That contradicted the comment
-- above it, which states:
--
--     "A no-op when no predecessor exists (a brand-new durable id from a
--      create-import) -- the row keeps its schema-default sentinel effective_from
--      in that case."
--
-- The consequence was that a brand-new durable id did NOT keep its epoch
-- effective_from: every create-import row was stamped with the import instant
-- instead of "has existed since forever". Any as-of resolution anchored earlier than
-- that instant then matched nothing. The visible symptom was survey.etl's
-- fact_sections build (Sql.java, "AND s.effective_from <= r.first_access_dt"), which
-- returned zero rows for any respondent whose first_access_dt predated the import.
--
-- The epoch sentinel only needs to give way to a real timestamp when this row is
-- superseding an existing current row -- that is what makes the two versions'
-- [effective_from, effective_to) ranges abut. With no predecessor there is nothing to
-- abut, and epoch is the correct, wider answer.
--
-- Rewritten below so the rewrite happens only when a predecessor is actually being
-- closed, and so the predecessor is still closed at the new row's start instant
-- (the ordering matters: the UPDATE must see the rewritten effective_from, not the
-- epoch it replaced).
--
-- CREATE OR REPLACE, so this corrects both migration tracks: greenfield databases
-- created by V001, and brownfield databases that created the original body in
-- migration-v3/V010 and have since converged onto db/migration. Idempotent.
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
