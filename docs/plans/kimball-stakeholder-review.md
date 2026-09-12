# Two behaviors that need research/compliance sign-off before go-live

**Audience:** research and compliance stakeholders reviewing the survey-versioning
upgrade (internally called "Kimball Type 2"), not engineers. No technical background is
assumed.

**Why you're being asked:** the two behaviors below are already built and tested. They
are not in question technically. What's being asked is whether they are *acceptable*
for a cancer family history data-collection tool — that's a research/compliance
judgment call, not an engineering one, and the engineering team should not be the ones
deciding it alone.

**What prompted this document:** the survey system is being upgraded so that when a
question, section, or category is edited after respondents have already started
answering, those respondents keep seeing the version of the survey they started with,
and the system keeps a full history of what changed and when. Two side effects of how
that history is used in *reports* need an explicit decision.

---

## Behavior 1 — Relabeling a category updates all past reports, not just future ones

**In plain terms:** if a step or section of the survey is renamed later (for example,
"Cancer History" is renamed to "Personal Cancer History" for clarity), every report ever
generated from that survey — including reports for respondents who answered *before* the
rename — will show the new name. The system does not keep a separate "as it was named
when this person answered" label for step/section names in reports.

**Example:** Respondent A completes the survey in January under a section named "Family
Cancer History." In March, a researcher renames that section to "Family History of
Cancer" for clarity. When Respondent A's report is viewed or re-generated in April, it
shows "Family History of Cancer" — not the name that was actually on screen when they
answered.

**What does NOT change:** the respondent's actual answers are never altered or lost.
Only the display label on the report changes.

**Why it was built this way:** the assumption made by the engineering team was that
renaming a step or section is normally just a wording clarification, not a substantive
change to what's being asked — so showing the current, clearer name everywhere (rather
than a historical snapshot of old wording) was treated as the more useful default for
anyone reading a report.

**Decision needed:** is it acceptable for a report to always show the *current* section
name, even for a respondent who answered under a different name? Or does compliance
require reports to preserve the exact label that was shown to that respondent at the
time?

---

## Behavior 2 — Reclassifying a question's category applies retroactively

**In plain terms:** every question is tagged with a category used to group answers in
reports (for example, a question might be tagged under "Depression Screening"). If a
researcher later changes which category a question is tagged under, **every existing
answer to that question — from every respondent, past and present — is automatically
reclassified under the new category** the next time reports are generated. There is no
option to reclassify only new respondents going forward.

**Example:** Respondent B answers "Do you feel hopeless most days?" in January, when it
is tagged under "Depression Screening." In June, a researcher decides that question
actually belongs under "Anxiety Screening" instead. The next report run will show
Respondent B's January answer classified under "Anxiety Screening" — as if it had always
been asked in that context — even though the question was framed and understood as a
depression-screening question at the time they answered.

**What does NOT change:** the respondent's actual answer text is never altered.
Only which category/label that answer is grouped under in reports changes.

**Why it was built this way:** the assumption made by the engineering team was that
recategorizing a question is normally correcting a mistake or improving classification,
not a retroactive change to the meaning of the data — so applying the correction
everywhere at once (rather than leaving old data under what was assumed to be a wrong
label) was treated as the more useful default.

**Decision needed:** is automatic, full retroactive reclassification acceptable? Or does
compliance require that recategorizing a question only affect respondents who answer
*after* the change, leaving historical answers under their original category unless
someone deliberately re-runs an analysis with the new mapping?

---

## Outcome

Both behaviors were reviewed and accepted as described — see the sign-off table below.
No further engineering work is required for either one. If that decision is ever
revisited, changing either behavior would require new work (the system would need to
start keeping a dated history of category assignments, similar to what it already does
for question wording).

**Reference for engineers:** this reflects Gap ETL-1 and Gap ETL-5 in
`Survey/research/Kimball_type_2.md`, called out in that document's "Implementation
Readiness Notes" as needing this sign-off before being treated as fully closed.

| Reviewer | Role | Decision | Date |
|---|---|---|---|
| Matthew Demerath | | ☑ Both acceptable as-is  ☐ Behavior 1 needs to change  ☐ Behavior 2 needs to change | 2026-09-12 |
