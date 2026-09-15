# TEMPORARY — this directory is upgrade-path scaffolding, not permanent history

This directory exists only so `com.elicitsoftware.flyway.ManualSchemaMigrator` can upgrade an
existing, already-deployed pre-Kimball ("V2.x") Survey database to V3.0.0 (Kimball Type 2 SCD).

- **`V001`–`V009` are frozen, byte-for-byte copies** of the original pre-Kimball migrations —
  real deployments already have these exact files' checksums recorded in `flyway_history`.
  **Never edit them.**
- **`V010`–`V012` are the real ALTER-based Kimball upgrade** (durable keys, `metadata_element_ck`
  fix, `dimensions_seq` grant) — the only place this logic lives. Bug fixes here are legitimate
  (see `V011`'s and `V012`'s own history) as long as unupgraded V2.x databases still exist.

**Delete this entire directory once every real Survey deployment has upgraded to V3** (i.e.
once every environment's `flyway_history` has converged onto `db/migration` —
`ManualSchemaMigrator` logs this on the boot it happens). At that point, also delete:

- `src/main/java/com/elicitsoftware/flyway/ManualSchemaMigrator.java`
- `src/test/java/com/elicitsoftware/flyway/ManualSchemaMigratorUpgradeTest.java`
- `src/test/java/com/elicitsoftware/flyway/ManualSchemaMigratorScaleTest.java`

and revert `quarkus.flyway.owner.migrate-at-start` in `application.properties` back to
Quarkus-managed auto-migration.

Tracked in `research/Kimball_type_2.md` and the repo-root `DeploymentScript.md` — update both
when this directory is actually removed. (The sibling FHHS app has the identical pattern, at
`FHHS/src/main/resources/db/migration-v3/`, on the same removal timeline.)
