# Test Survey Design — Full Logical Path Coverage

## Purpose

Design a survey that exercises every supported feature of the Elicit Survey engine in a single,
coherent data set. The survey must produce test scenarios for:

- Every question input type
- Every question data type
- Every relationship action type (SHOW, REPEAT, TEXT)
- Every relationship operator type (BOOLEAN, EQUAL, NOT\_EQUAL, GREATER THAN, CONTAINS, FIELD\_EXIST)
- Question-level features (required, optional, validation, tooltips, placeholders, masks, defaults, variants)
- Text replacement tokens in question text and section names
- Multi-step navigation (multiple steps, forward and backward)
- Repeated steps and repeated sections driven by upstream integer answers
- Nested/chained relationships (A shows B which shows C)
- Review screen aggregation of all answer types

---

## Checklist — Items That Must Appear in the Test Survey

### 1. Question Input Types

All types defined in `GlobalStrings` and `V003__Populate_Schema.sql` must appear at least once.

| # | Type Name       | Data Type | Notes |
|---|-----------------|-----------|-------|
| 1 | `CHECKBOX`      | Boolean   | Single boolean toggle |
| 2 | `CHECKBOX_GROUP`| Text      | Multi-select via checkboxes |
| 3 | `DATE_PICKER`   | Date      | Calendar date only |
| 4 | `DATETIME`      | Text      | Date + time picker |
| 5 | `COMBOBOX`      | Text      | Single-select dropdown |
| 6 | `MULTI_SELECT`  | Text      | Multi-select combobox |
| 7 | `RADIO`         | Number    | Single-select radio buttons |
| 8 | `TEXT`          | Text      | Single-line free text |
| 9 | `TEXTAREA`      | Text      | Multi-line free text |
| 10 | `INTEGER`      | Number    | Whole number input |
| 11 | `DOUBLE`       | Number    | Decimal number input |
| 12 | `EMAIL`        | Text      | Email address input |
| 13 | `PASSWORD`     | Text      | Password/masked input |
| 14 | `HTML`         | (none)    | Informational display block (no answer stored) |

---

### 2. Relationship Action Types

All three action types must fire at least once.

| # | Action | Trigger | Target | Scenario |
|---|--------|---------|--------|----------|
| 1 | `SHOW` | Upstream answer satisfies operator | Downstream **question** | Reveal a follow-up question |
| 2 | `SHOW` | Upstream answer satisfies operator | Downstream **section** | Reveal an entire section |
| 3 | `REPEAT` | Upstream integer value N | Downstream **section** repeated N times | Repeated per-item section |
| 4 | `REPEAT` | Upstream integer value N | Downstream **step** repeated N times | Repeated per-subject step |
| 5 | `TEXT`  | Upstream text/name answer | Downstream **question** text token | Replace `{TOKEN}` in question text |
| 6 | `TEXT`  | Upstream text/name answer | Downstream **section** name token | Replace `{TOKEN}` in section name |

---

### 3. Relationship Operator Types

Each operator must be used as the condition of at least one SHOW relationship.

| # | Operator       | Symbol | Scenario |
|---|----------------|--------|----------|
| 1 | `BOOLEAN`      | (true/false) | SHOW follow-up when CHECKBOX is checked (`TRUE`) |
| 2 | `EQUAL`        | `=`    | SHOW question when RADIO answer equals a specific coded value |
| 3 | `NOT_EQUAL`    | `!=`   | SHOW disclaimer when RADIO answer is NOT the expected value |
| 4 | `GREATER THAN` | `>=`   | SHOW section when INTEGER count >= 1 |
| 5 | `CONTAINS`     | `In`   | SHOW section when CHECKBOX\_GROUP or MULTI\_SELECT contains a value |
| 6 | `FIELD_EXIST`  | ``     | SHOW a question once a TEXT field has any non-null value |

---

### 4. Structural Elements

| # | Element | Minimum Count | Notes |
|---|---------|--------------|-------|
| 1 | Survey | 1 | Single survey entity |
| 2 | Steps | ≥ 4 | Welcome, Profile, Detail, Repeated Detail |
| 3 | Sections | ≥ 6 | At least one per step; ≥ 1 conditionally shown; ≥ 1 repeated |
| 4 | Steps-Sections mappings | 1 per step-section pair | With correct `display_key` values |
| 5 | Select Groups | ≥ 4 | YesNo, ScaleRating, CategoryList, MultiOption |
| 6 | Select Items | ≥ 2 per group | Covering both branches of each BOOLEAN-style group |

---

### 5. Question-Level Feature Coverage

Each feature must be exercised on at least one question.

| # | Feature | Field | Scenario |
|---|---------|-------|----------|
| 1 | Required validation | `required = true` | At least 3 questions are required |
| 2 | Optional field | `required = false` | At least 2 questions are optional |
| 3 | Minimum value | `min_value` | INTEGER or DOUBLE with a lower bound |
| 4 | Maximum value | `max_value` | INTEGER or DOUBLE with an upper bound |
| 5 | Validation message | `validation_text` | Custom error message shown on bad input |
| 6 | Tooltip | `tool_tip` | Helper text accessible via hover/focus |
| 7 | Placeholder | `placeholder` | Ghost text in empty input field |
| 8 | Input mask | `mask` | Formatted input (e.g., phone, ZIP) |
| 9 | Default value | `default_value` | Pre-populated answer value |
| 10 | Short text | `short_text` | Abbreviated label used in review screen |
| 11 | Variant | `variant = 'vertical'` | Layout variant applied to radio/checkbox |

---

### 6. Text Replacement Token Coverage

All token patterns used by `buildDisplayText` must appear.

| # | Token Pattern | Replacement Source | Example |
|---|---------------|--------------------|---------|
| 1 | `{TOKEN\|default}` | Upstream answer value or literal default | `{NAME\|you}` |
| 2 | `{TOKEN's\|your}` | Possessive form of upstream answer or pronoun | `{NAME's\|your}` |
| 3 | `{Q#}` | Question instance number within repeated section | `{Q#}` in repeated section question |
| 4 | `{S#}` | Section instance number (repetition counter) | `{S#}` in section name |
| 5 | `{S1}` | Value of first question in the section (for section title) | `{S1}` in section name |
| 6 | Token in section `name` | Upstream name answer | `{TOKEN's\|Your} Item {S#} - {S1}` |
| 7 | Token in question `text` | Upstream name answer | `What is {NAME's\|your} age?` |

---

### 7. Navigation and Flow Paths

| # | Path | Description |
|---|------|-------------|
| 1 | Linear forward navigation | All steps visited in order |
| 2 | Back navigation | Previous button returns to prior step |
| 3 | Conditional skip | Step/section hidden until upstream condition met |
| 4 | Conditional reveal | Step/section shown after upstream condition met |
| 5 | Repeated section N=0 | Entering 0 creates no repeated sections |
| 6 | Repeated section N=1 | Single repeated section instance |
| 7 | Repeated section N>1 | Multiple repeated section instances |
| 8 | Repeated section increase | Increasing count adds new instances |
| 9 | Nested SHOW chain | Q1 SHOW → Q2 SHOW → Q3 visible only when both conditions are true |
| 10 | Review screen | All answered questions appear correctly on review |

---

### 8. Proposed Survey Structure (Narrative Outline)

> **Note:** The structure below is a placeholder scaffold only. It assigns each feature to a step/section
> slot to ensure nothing is missed. The actual survey topic — the real-world subject matter, question wording,
> and select-group values — must be decided by the team before implementation. Replace every `[TBD]` with
> content appropriate for the chosen domain.

**Survey Name:** `[TBD]`

#### Step 1 — Introduction (1 section)
- **HTML** question: intro/informational block `[TBD wording]`
- **CHECKBOX** question (required): acknowledgement / consent toggle
  - Relationship: BOOLEAN SHOW → follow-up detail question (exercises BOOLEAN operator + SHOW on question)

#### Step 2 — Core Identification (1 section)
- **TEXT** question (required, min\_value, max\_value, validation\_text, placeholder): a name or identifier used for token replacement downstream
  - Relationship: FIELD\_EXIST SHOW → a follow-up question visible only once the field is filled (exercises FIELD\_EXIST operator)
  - Relationship: TEXT → replaces token `{T1}` in all downstream question text and section names
- **DATE\_PICKER** question (required): a key date
- **INTEGER** question (required, min\_value, max\_value, variant, validation\_text): a count or numeric value
  - Relationship: GREATER THAN SHOW → a conditionally visible section in Step 3 (exercises GREATER THAN operator)
- **EMAIL** question (optional, placeholder, tool\_tip): an email address field
- **PASSWORD** question (optional): a masked/password field
- **RADIO** question (required): a binary or small-set choice (select group A)
  - Relationship: EQUAL SHOW → a conditionally visible section (exercises EQUAL operator)

#### Step 3 — Detail (2 sections)
- **Section 1** — always visible
  - **CHECKBOX\_GROUP** question (optional, variant): multi-value selection from a group (select group B)
    - Relationship: CONTAINS SHOW → follow-up question when value B1 is selected (exercises CONTAINS operator)
    - Relationship: CONTAINS SHOW → separate follow-up question when value B2 is selected
  - **INTEGER** question (required, min\_value): count that drives REPEAT of Step 4
    - Relationship: REPEAT → Step 4 (repeated step)
  - **INTEGER** question (required, min\_value): count that drives REPEAT of a section within Step 4
    - Relationship: REPEAT → repeated section within Step 4
  - **MULTI\_SELECT** question (optional): multi-select combobox (select group C)
    - Relationship: CONTAINS SHOW → topic/detail question (exercises CONTAINS on MULTI\_SELECT)
  - **DOUBLE** question (optional, min\_value, max\_value, validation\_text, placeholder): decimal numeric field
  - **TEXTAREA** question (optional, placeholder, tool\_tip): free-text notes field
- **Section 2** — conditionally shown by GREATER THAN relationship from Step 2 INTEGER
  - **COMBOBOX** question (required): single-select dropdown (select group D)
  - **DATE\_PICKER** question (optional): a secondary date
  - **DATETIME** question (optional): date + time combined picker

#### Step 4 — Repeated Item Detail (repeated step; 1 section per instance)
- Section name uses tokens: `{T1's|Your} Item {S#} - {S1}`
  - **TEXT** question (required, min\_value, max\_value): item identifier — populates `{S1}` in section name and token `{T2}` downstream
    - Relationship: TEXT → replaces token `{T2}` in downstream questions within this step
  - **RADIO** question (required): categorical choice (select group E)
    - Relationship: NOT\_EQUAL SHOW → "other / specify" TEXT question (exercises NOT\_EQUAL operator)
  - **DATE\_PICKER** question (required): date associated with this item
  - **CHECKBOX** question (optional, tool\_tip): boolean flag for this item
    - Relationship: BOOLEAN SHOW → TEXTAREA detail question (second exercise of BOOLEAN + SHOW on question, creates nested chain with Step 2 consent SHOW)

#### Step 5 — Secondary Repeated Section (repeated section within Step 4)
- Section name uses tokens: `{T1's|Your} Sub-Item {S#} - {S1}`
  - **TEXT** question (required, min\_value): sub-item label — populates `{S1}`
  - **INTEGER** question (required, min\_value, max\_value): numeric attribute
  - **COMBOBOX** question (optional): single-select (select group F)
  - **CHECKBOX\_GROUP** question (optional): multi-select flags (select group G)

---

### 9. Select Groups Required

The specific coded values and display labels are `[TBD]` pending domain selection. The table below
defines the minimum structural requirements; each group needs at least two items.

| # | Group Role | Used By | Min Items | Notes |
|---|------------|---------|-----------|-------|
| A | Binary / YesNo choice | Step 2 RADIO | 2 | One coded `TRUE`, one `FALSE`; drives EQUAL relationship |
| B | Multi-value category list | Step 3 CHECKBOX\_GROUP | ≥ 3 | At least two values each trigger a CONTAINS SHOW |
| C | Topic / multi-select list | Step 3 MULTI\_SELECT | ≥ 3 | At least one value triggers a CONTAINS SHOW |
| D | Single-select dropdown | Step 3 COMBOBOX | ≥ 3 | Used in conditionally shown section |
| E | Categorical choice | Step 4 RADIO | ≥ 3 | One value triggers NOT\_EQUAL SHOW for "other" path |
| F | Single-select dropdown | Step 5 COMBOBOX | ≥ 2 | — |
| G | Multi-value flags | Step 5 CHECKBOX\_GROUP | ≥ 2 | — |

---

### 10. Relationships Summary Table

| # | Description | Upstream SQ | Operator | Action | Downstream |
|---|-------------|-------------|----------|--------|------------|
| 1 | Show follow-up on consent | Step 1 CHECKBOX | BOOLEAN | SHOW | Follow-up question (Step 1) |
| 2 | Show question once identifier entered | Step 2 TEXT | FIELD\_EXIST | SHOW | Secondary question (Step 2) |
| 3 | Replace token T1 everywhere | Step 2 TEXT | FIELD\_EXIST | TEXT | All downstream question text and section names |
| 4 | Show conditional section | Step 2 INTEGER | GREATER THAN | SHOW | Step 3 Section 2 |
| 5 | Show detail section | Step 2 RADIO = group-A-true-value | EQUAL | SHOW | Step 3 Section 1 |
| 6 | Show follow-up for value B1 | Step 3 CHECKBOX\_GROUP | CONTAINS | SHOW | Follow-up question B1 |
| 7 | Show follow-up for value B2 | Step 3 CHECKBOX\_GROUP | CONTAINS | SHOW | Follow-up question B2 |
| 8 | Show topic detail | Step 3 MULTI\_SELECT | CONTAINS | SHOW | Topic detail question |
| 9 | Repeat Step 4 | Step 3 INTEGER (count 1) | REPEAT | REPEAT | Step 4 (repeated step) |
| 10 | Repeat section within Step 4 | Step 3 INTEGER (count 2) | REPEAT | REPEAT | Step 5 section |
| 11 | Replace token T1 in Step 4 sections | Step 2 TEXT | FIELD\_EXIST | TEXT | Step 4 section names and questions |
| 12 | Replace token T2 in Step 4 questions | Step 4 TEXT (item identifier) | FIELD\_EXIST | TEXT | Downstream questions within same Step 4 instance |
| 13 | Show "other" specify question | Step 4 RADIO ≠ expected value | NOT\_EQUAL | SHOW | "Other / specify" TEXT (Step 4) |
| 14 | Show boolean detail | Step 4 CHECKBOX | BOOLEAN | SHOW | Detail TEXTAREA (Step 4) — chains with #1 for nested test |

---

### 11. Display Key Strategy

Display keys follow the pattern `SSSS-TTTT-IIII-CCCC-IIII-QQQQ-IIII` where:

- `SSSS` = survey display order (always `0001` for survey 1)
- `TTTT` = step display order
- `IIII` = step instance (0000 for non-repeated, 0001+ for repeated)
- `CCCC` = section display order
- next `IIII` = section instance
- `QQQQ` = question display order
- last `IIII` = question instance

Static sections use instance `0000`; repeated sections use `0001`, `0002`, etc.

---

### 12. Domain Application — Elicit Design and Administration Conference Registration

This section maps the abstract scaffold onto an **Elicit Design and Administration Conference Registration** domain —
a conference for researchers, analysts, and practitioners who design and administer surveys.
The domain fits naturally: attendees register with an account, list co-registrants from their organization
(repeated step), and select sessions they wish to attend (repeated section).

---

#### Survey Name: `Elicit Design and Administration Conference Registration`

**Initial display key:** `0001-0001-0000-0001-0000-0000-0000`

---

#### Step 1 — Welcome (Section: Welcome)

| Q# | Type | Required | Question / Content | Feature Exercised |
|----|------|----------|--------------------|-------------------|
| 1 | **HTML** | — | `<h1>Welcome to the Elicit Design and Administration Conference</h1><p>Complete this form to register. You will create a login, provide contact details, select the sessions you plan to attend, and choose your preferred time slot for each session.</p>` | HTML display block |
| 2 | **CHECKBOX** | Yes | "I agree to the conference code of conduct and cancellation policy." | CHECKBOX type, BOOLEAN operator |
|   | → SHOW (BOOLEAN) | — | Q3 becomes visible when checked | BOOLEAN SHOW on question |
| 3 | **HTML** | — | `<p>Thank you. Your agreement has been recorded. Please continue to create your account.</p>` | BOOLEAN SHOW target |

---

#### Step 2 — Account & Contact (Section: Account)

| Q# | Type | Required | Question / Content | Notes / Feature Exercised |
|----|------|----------|--------------------|---------------------------|
| 4 | **TEXT** | Yes | "First name" | min=2, max=50, placeholder="First name", validation\_text="First name must be at least 2 characters"; drives token `{FNAME}` |
|   | → TEXT (FIELD\_EXIST) | — | Replaces token `{FNAME}` in all downstream questions and section names | TEXT action, token replacement |
| 5 | **TEXT** | Yes | "{FNAME's\|Your} last name" | Uses `{FNAME}` token; short\_text="Last name" |
| 6 | **EMAIL** | Yes | "Work email address" | EMAIL type; placeholder="you@organization.org"; tool\_tip="This will be your login username"; short\_text="Email" |
| 7 | **PASSWORD** | Yes | "Create a password" | PASSWORD type; tool\_tip="Minimum 8 characters"; validation\_text="Password is required" |
| 8 | **TEXT** | Yes | "Work phone number" | mask="(999) 999-9999"; placeholder="(555) 867-5309"; validation\_text="A valid 10-digit phone number is required"; short\_text="Phone" |
| 9 | **DATE\_PICKER** | Yes | "What is {FNAME's\|your} date of birth?" | DATE\_PICKER type; used for badge / age verification |
| 10 | **RADIO** | Yes | "Will you be attending from outside the United States?" (YesNo group) | RADIO type; drives paired EQUAL operators |
|    | → EQUAL SHOW (= TRUE) | — | Q11 (country) visible for international respondents | EQUAL SHOW on question |
|    | → EQUAL SHOW (= FALSE) | — | Q35 (state/province) visible for domestic respondents | Second EQUAL on same upstream (R17) |
| 11 | **TEXT** | No | "Country of residence" | placeholder="e.g., Canada, Germany, Brazil"; EQUAL 'TRUE' SHOW target; short\_text="Country"; reports to `dim_location` |
| 35 | **TEXT** | No | "State or province of residence" | placeholder="e.g., California, Ontario"; EQUAL 'FALSE' SHOW target; short\_text="State"; reports to `dim_location` (same ontology as Q11) |
| 12 | **RADIO** | Yes | "Is {FNAME\|your} registration being paid by an organization?" (YesNo group) | RADIO type, EQUAL operator |
|    | → EQUAL SHOW (= TRUE) | — | Section 2 of Step 2 becomes visible | EQUAL SHOW on section |

**Section 2 (Step 2) — Organization Billing** *(conditionally shown when Q12 = Yes)*

| Q# | Type | Required | Question / Content | Feature Exercised |
|----|------|----------|--------------------|-------------------|
| 13 | **TEXT** | Yes | "Organization name" | EQUAL SHOW target; short\_text="Organization" |
| 14 | **COMBOBOX** | Yes | "Organization type" (OrgType group) | COMBOBOX type |

---

#### Step 3 — Attendance Details (2 Sections)

**Section 1 — Attendance** *(always visible)*

| Q# | Type | Required | Question / Content | Feature Exercised |
|----|------|----------|--------------------|-------------------|
| 15 | **INTEGER** | Yes | "How many conference sessions do you plan to attend?" | min=0, max=20, variant="vertical", validation\_text="Must be between 0 and 20"; drives REPEAT of Step 4 |
|    | → REPEAT | — | Repeats Step 4 (Session) N times | REPEAT action on step |
| 16 | **CHECKBOX\_GROUP** | No | "Which conference tracks interest you?" (Tracks group: Design, Analysis, Technology, Ethics) | CHECKBOX\_GROUP type, CONTAINS operator; variant="vertical" |
|    | → CONTAINS SHOW (= 'technology') | — | Q17 becomes visible | CONTAINS SHOW on question |
|    | → CONTAINS SHOW (= 'ethics') | — | Q19 becomes visible | Second CONTAINS SHOW |
| 17 | **TEXT** | No | "List any survey software tools you currently use" | placeholder="e.g., Qualtrics, REDCap, Elicit"; CONTAINS SHOW target |
|    | → FIELD\_EXIST SHOW | — | Q18 becomes visible once any tools are listed | FIELD\_EXIST SHOW on question; 3-level chain: Q16→Q17→Q18 |
| 18 | **TEXT** | No | "Which of those tools is your primary platform?" | placeholder="e.g., Qualtrics"; FIELD\_EXIST SHOW target; short\_text="Primary tool"; 3rd level of nested SHOW chain |
| 19 | **TEXTAREA** | No | "Describe any ethical considerations you'd like addressed at the conference" | placeholder="e.g., informed consent practices, data anonymization…"; tool\_tip="Responses may be used to shape panel topics"; CONTAINS SHOW target |
| 20 | **MULTI\_SELECT** | No | "Select any prior conferences you have attended" (PriorConferences group) | MULTI\_SELECT type |
|    | → CONTAINS SHOW (= 'international') | — | Q21 becomes visible | CONTAINS on MULTI\_SELECT |
| 21 | **TEXT** | No | "List the countries where you have previously presented" | MULTI\_SELECT CONTAINS SHOW target; short\_text="Countries presented" |
| 22 | **DOUBLE** | No | "How many years of survey research experience do you have?" | min=0.0, max=50.0, placeholder="e.g., 7.5", validation\_text="Must be between 0 and 50"; short\_text="Years experience"; default\_value="1.0" |

**Section 2 — Dietary & Arrival** *(conditionally shown when Q15 INTEGER > 1)*

| Q# | Type | Required | Question / Content | Feature Exercised |
|----|------|----------|--------------------|-------------------|
| 23 | **COMBOBOX** | Yes | "Primary dietary requirement for catering" (DietaryNeeds group) | COMBOBOX; GREATER THAN SHOW target |
| 24 | **DATE\_PICKER** | No | "Arrival date" | Secondary date in conditional section |
| 25 | **DATETIME** | No | "Preferred check-in date and time" | DATETIME type |

---

#### Step 4 — Session (Repeated Step; 1 section per instance)

Section name: `{FNAME's|Your} Session {S#} - {S1}`

Each instance captures one conference session the registrant plans to attend.

| Q# | Type | Required | Question / Content | Feature Exercised |
|----|------|----------|--------------------|-------------------|
| 26 | **TEXT** | Yes | "Session title or topic" | min=2, max=200, placeholder="e.g., Survey Design Best Practices"; populates `{S1}` in section name via R15 |
| 27 | **INTEGER** | Yes | "How many time slots does this session offer?" | min=1, max=5, variant="vertical", validation\_text="Must be between 1 and 5"; drives REPEAT of Step 5 section; min=1 means at least one time slot always exists |
|    | → REPEAT | — | Repeats Step 5 (Time Slot) N times for this session | REPEAT action on section |
| 28 | **RADIO** | Yes | "What is your role in this session?" (SessionRole group: Attending, Presenting, Moderating, Other) | RADIO type; NOT\_EQUAL operator |
|    | → NOT\_EQUAL SHOW (≠ 'attending') | — | Q29 becomes visible for presenting, moderating, or other roles | NOT\_EQUAL SHOW on question |
| 29 | **TEXT** | No | "Please describe your involvement in this session" | NOT\_EQUAL SHOW target; placeholder="e.g., co-presenter, panel discussant…"; short\_text="Role description" |
| 30 | **CHECKBOX** | No | "Do you require accessibility accommodations for this session?" | tool\_tip="We will contact you to arrange accommodations"; BOOLEAN operator |
|    | → BOOLEAN SHOW | — | Q31 becomes visible when checked | Second BOOLEAN SHOW; nested chain with Q2 consent SHOW |
| 31 | **TEXTAREA** | No | "Describe your accommodation needs for this session" | BOOLEAN SHOW target; placeholder="e.g., wheelchair access, sign language interpreter…" |

---

#### Step 5 — Time Slot (Repeated Section; per Session)

Section name: `Time Slot {S#} - {S1}`

Each instance captures one available time slot for the session in the current Step 4 instance.
Driven by Q27 (time slot count) inside that session's Step 4 instance.

| Q# | Type | Required | Question / Content | Feature Exercised |
|----|------|----------|--------------------|-------------------|
| 32 | **DATE\_PICKER** | Yes | "Date for time slot {Q#}" | DATE\_PICKER type; populates `{S1}` in section name via R16; uses `{Q#}` token for instance number within repeated section |
| 33 | **COMBOBOX** | Yes | "Session format for this time slot" (SessionFormat group) | COMBOBOX in repeated section |
| 34 | **TEXT** | No | "Room or venue for this time slot" | optional; placeholder="e.g., Main Hall, Room 204"; short\_text="Venue" |

---

#### Select Groups — Elicit Design and Administration Conference

| Group Name | Coded Values | Display Labels |
|------------|-------------|----------------|
| `YesNo` | TRUE / FALSE | Yes / No |
| `OrgType` | academic / government / nonprofit / commercial / other | Academic / Government / Non-profit / Commercial / Other |
| `Tracks` | design / analysis / technology / ethics | Survey Design / Data Analysis / Technology & Tools / Ethics & Policy |
| `PriorConferences` | regional / national / international | Regional / National / International |
| `DietaryNeeds` | none / vegetarian / vegan / glutenfree / kosher / halal | No Restriction / Vegetarian / Vegan / Gluten-Free / Kosher / Halal |
| `SessionRole` | attending / presenting / moderating / other | Attending / Presenting / Moderating / Other |
| `SessionFormat` | workshop / panel / keynote / poster | Workshop / Panel / Keynote / Poster Session |

---

#### Relationships — Elicit Design and Administration Conference

| # | Description | Upstream Q | Operator | Action | Downstream |
|---|-------------|------------|----------|--------|------------|
| 1 | Show confirmation message after consent | Q2 CHECKBOX | BOOLEAN | SHOW | Q3 HTML (Step 1) |
| 2 | Show country field for international respondents | Q10 RADIO = TRUE | EQUAL | SHOW | Q11 TEXT (Step 2) |
| 17 | Show state/province field for domestic respondents | Q10 RADIO = FALSE | EQUAL | SHOW | Q35 TEXT (Step 2) |
| 3 | Replace `{FNAME}` in Account section question text | Q4 TEXT | FIELD\_EXIST | TEXT | Account section (Step 2) |
| 4 | Replace `{FNAME}` in Session section name (Step 4) | Q4 TEXT | FIELD\_EXIST | TEXT | Step 4 section name |
| 5 | Show organization billing section | Q12 RADIO = TRUE | EQUAL | SHOW | Step 2 Section 2 |
| 6 | Show software tools question | Q16 CHECKBOX\_GROUP contains 'technology' | CONTAINS | SHOW | Q17 (Step 3) |
| 7 | Show primary platform question (nested chain level 3) | Q17 TEXT | FIELD\_EXIST | SHOW | Q18 (Step 3); visible only when Q16 contains 'technology' AND Q17 is filled |
| 8 | Show ethics textarea | Q16 CHECKBOX\_GROUP contains 'ethics' | CONTAINS | SHOW | Q19 (Step 3) |
| 9 | Show countries presented question | Q20 MULTI\_SELECT contains 'international' | CONTAINS | SHOW | Q21 (Step 3) |
| 10 | Show dietary & arrival section | Q15 INTEGER > 1 (attending 2+ sessions) | GREATER THAN | SHOW | Step 3 Section 2 |
| 11 | Repeat session step | Q15 INTEGER > 0 | GREATER THAN | REPEAT | Step 4 (repeated step) |
| 12 | Repeat time slot section per session | Q27 INTEGER (inside Step 4 instance) | GREATER THAN | REPEAT | Step 5 section (within that session's instance) |
| 13 | Show role description for non-attending roles | Q28 RADIO ≠ 'attending' | NOT\_EQUAL | SHOW | Q29 TEXT (Step 4) |
| 14 | Show accommodation needs textarea | Q30 CHECKBOX | BOOLEAN | SHOW | Q31 TEXTAREA (Step 4) |
| 15 | Populate `{S1}` in Session section name with session title | Q26 TEXT | FIELD\_EXIST | SHOW (S1) | Step 4 section name |
| 16 | Populate `{S1}` in Time Slot section name with date | Q32 DATE\_PICKER | FIELD\_EXIST | SHOW (S1) | Step 5 section name |

---

### 13. Out-of-Scope for This Survey

Items intentionally excluded to keep the survey focused:

- `MODAL` question type (not in the V003 migration; may be application-specific)
- `MULTI_SELECT_COMBOBOX` (listed in GlobalStrings but not in schema migration)
- `TIME_PICKER` (listed in GlobalStrings but not in schema migration)
- Post-survey redirect URL logic
- Multi-survey respondent management

---

### 14. Sample Ontologies and Metadata

This section proposes the `dimensions`, `ontology`, and `metadata` rows for the conference
registration survey. These drive the `surveyreport` dimensional model via the ETL process.

#### How the model works

- **`dimensions`** — named reporting dimensions. When an `ontology` row references a
  `dimensions` row, the ETL creates `dim_<dimensions.name>` in `surveyreport` and adds a
  `<ontology.tag>_key` foreign-key column to `fact_sections`.
- **`ontology`** — one row per taggable concept. `tag` is the machine identifier;
  `name` is the human label. If `dimension` is NULL, the ETL creates a standalone
  `dim_<tag>` lookup table but adds no column to `fact_sections`.
- **`metadata`** — joins a survey structural element (question, sections\_question, or
  steps\_section) to an ontology row. `value` is an optional static override; if empty,
  the ETL uses the respondent's raw answer value.

Section-level dimension tables (`dim_welcome`, `dim_registrant`, etc.) are created
automatically from `sections.dimension_name` — no `ontology` or `metadata` rows are
needed for sections.

---

#### 14.1 Dimensions Table

Seven named dimensions that will appear as FK columns in `fact_sections`.
Names are chosen to avoid collision with the seven section `dimension_name` values
(Welcome, Registrant, OrgBilling, Attendance, Dietary, Session, TimeSlot).

| # | `name` | Resulting table | Drives fact\_sections column |
|---|--------|----------------|------------------------------|
| 1 | `Consent` | `dim_consent` | `consent_key` |
| 2 | `Geography` | `dim_geography` | `international_key` |
| 3 | `Billing` | `dim_billing` | `org_billing_key` |
| 4 | `Organization` | `dim_organization` | `org_type_key` |
| 5 | `Diet` | `dim_diet` | `dietary_key` |
| 6 | `Role` | `dim_role` | `session_role_key` |
| 7 | `Format` | `dim_format` | `session_format_key` |

---

#### 14.2 Ontology Table

**Core ontologies** — dimensioned (add FK columns to `fact_sections`)

| # | `name` | `tag` | `dimension` | Source question | Coded values |
|---|--------|-------|-------------|-----------------|--------------|
| 1 | Code of Conduct Consent | `consent` | Consent | Q2 CHECKBOX | `true` / `false` |
| 2 | International Attendee | `international` | Geography | Q10 RADIO (YesNo) | `true` / `false` |
| 3 | Organization Billing | `org_billing` | Billing | Q12 RADIO (YesNo) | `true` / `false` |
| 4 | Organization Type | `org_type` | Organization | Q14 COMBOBOX | academic / government / nonprofit / commercial / other |
| 5 | Dietary Requirement | `dietary` | Diet | Q23 COMBOBOX | none / vegetarian / vegan / glutenfree / kosher / halal |
| 6 | Session Role | `session_role` | Role | Q28 RADIO | attending / presenting / moderating / other |
| 7 | Session Format | `session_format` | Format | Q33 COMBOBOX | workshop / panel / keynote / poster |

**Core ontologies** — standalone (create `dim_<tag>` table; no FK in `fact_sections`)

Multi-value questions cannot be a single FK column, so they use standalone dim tables.
The ETL stores one row per distinct value encountered across all respondents.

| # | `name` | `tag` | `dimension` | Source question | Notes |
|---|--------|-------|-------------|-----------------|-------|
| 8 | Conference Track | `track` | NULL | Q16 CHECKBOX\_GROUP | Multi-value; ETL produces one dim row per track code |
| 9 | Prior Conference Type | `prior_conference` | NULL | Q20 MULTI\_SELECT | Multi-value; one dim row per prior-conference code |

**Extended ontologies** — standalone, optional

These add richer lookup data but are not required for the core star schema.

| # | `name` | `tag` | `dimension` | Source question | Notes |
|---|--------|-------|-------------|-----------------|-------|
| 10 | Location | `location` | NULL | Q11 TEXT *or* Q35 TEXT | Country (Q11) and state/province (Q35) both report here; "Mexico" and "California" are peer rows in `dim_location` |
| 11 | Session Title | `session_title` | NULL | Q26 TEXT | Labels session instances in reports; free text |
| 12 | Years of Experience | `experience` | NULL | Q22 DOUBLE | Raw numeric value; range bucketing done at view layer |
| 13 | Accessibility Required | `accessibility` | NULL | Q30 CHECKBOX | `true` / `false`; enables accommodation count queries |
| 14 | Primary Survey Platform | `primary_platform` | NULL | Q18 TEXT | Free text; surfaces from nested SHOW chain |
| 15 | Time Slot Venue | `venue` | NULL | Q34 TEXT | Free text; room/hall for each time slot |

---

#### 14.3 Metadata Bindings

Each row binds a `sections_questions` record (sq\_id) to an ontology row.
`value` is blank for all rows below — the ETL uses the respondent's raw answer.
PII questions (Q4–Q9 name, email, password, phone, DOB) are intentionally excluded.

| # | `section_question_id` | Question | `ontology` | Metadata `value` |
|---|-----------------------|----------|------------|-----------------|
| 1 | sq2 | Q2 CHECKBOX — consent | consent (#1) | *(empty — use answer: true/false)* |
| 2 | sq10 | Q10 RADIO — international | international (#2) | *(empty — use coded value)* |
| 3 | sq11 | Q11 TEXT — country | location (#10) | *(empty — use answer text; Q11 and sq35 share this ontology)* |
| 3b | sq35 | Q35 TEXT — state/province | location (#10) | *(empty — use answer text; peer row in dim\_location alongside country values)* |
| 4 | sq12 | Q12 RADIO — org-paid | org\_billing (#3) | *(empty — use coded value)* |
| 5 | sq14 | Q14 COMBOBOX — org type | org\_type (#4) | *(empty — use coded value)* |
| 6 | sq16 | Q16 CHECKBOX\_GROUP — tracks | track (#8) | *(empty — use coded value)* |
| 7 | sq18 | Q18 TEXT — primary platform | primary\_platform (#14) | *(empty — use answer text)* |
| 8 | sq20 | Q20 MULTI\_SELECT — prior conferences | prior\_conference (#9) | *(empty — use coded value)* |
| 9 | sq22 | Q22 DOUBLE — years experience | experience (#12) | *(empty — use answer value)* |
| 10 | sq23 | Q23 COMBOBOX — dietary requirement | dietary (#5) | *(empty — use coded value)* |
| 11 | sq26 | Q26 TEXT — session title | session\_title (#11) | *(empty — use answer text)* |
| 12 | sq28 | Q28 RADIO — session role | session\_role (#6) | *(empty — use coded value)* |
| 13 | sq30 | Q30 CHECKBOX — accessibility | accessibility (#13) | *(empty — use answer: true/false)* |
| 14 | sq33 | Q33 COMBOBOX — session format | session\_format (#7) | *(empty — use coded value)* |
| 15 | sq34 | Q34 TEXT — venue | venue (#15) | *(empty — use answer text)* |

---

#### 14.4 Reporting Schema Impact

**`fact_sections` — FK columns added by dimensioned ontologies**

| Column added | References | Populated from |
|---|---|---|
| `consent_key` | `dim_consent` | Q2 answer (true/false) |
| `international_key` | `dim_geography` | Q10 coded value (true/false) |
| `org_billing_key` | `dim_billing` | Q12 coded value (true/false) |
| `org_type_key` | `dim_organization` | Q14 coded value |
| `dietary_key` | `dim_diet` | Q23 coded value |
| `session_role_key` | `dim_role` | Q28 coded value |
| `session_format_key` | `dim_format` | Q33 coded value |

**Complete `surveyreport` dimension table inventory**

| Table | Created by | Grain |
|---|---|---|
| `dim_welcome` | Section `dimension_name` | One row per Welcome section instance (always 1) |
| `dim_registrant` | Section `dimension_name` | One row per Registrant section instance (always 1) |
| `dim_orgbilling` | Section `dimension_name` | One row per OrgBilling instance (0–1 per response) |
| `dim_attendance` | Section `dimension_name` | One row per Attendance instance (always 1) |
| `dim_dietary` | Section `dimension_name` | One row per Dietary instance (0–1 per response) |
| `dim_session` | Section `dimension_name` | One row per Session instance (0–20 per response) |
| `dim_timeslot` | Section `dimension_name` | One row per TimeSlot instance (1–5 per Session) |
| `dim_consent` | Ontology `Consent` | Distinct consent values (true, false) |
| `dim_geography` | Ontology `Geography` | Distinct international values (true, false) |
| `dim_billing` | Ontology `Billing` | Distinct billing values (true, false) |
| `dim_organization` | Ontology `Organization` | Distinct org type coded values (5 values) |
| `dim_diet` | Ontology `Diet` | Distinct dietary coded values (6 values) |
| `dim_role` | Ontology `Role` | Distinct session role coded values (4 values) |
| `dim_format` | Ontology `Format` | Distinct session format coded values (4 values) |
| `dim_track` | Ontology standalone | Distinct track codes (design, analysis, technology, ethics) |
| `dim_prior_conference` | Ontology standalone | Distinct prior-conference codes (regional, national, international) |
| `dim_location` | Ontology standalone | Distinct location values — country names (Q11) and state/province names (Q35) as peer rows |
| `dim_session_title` | Ontology standalone | Distinct session title text values |
| `dim_experience` | Ontology standalone | Distinct experience numeric values |
| `dim_accessibility` | Ontology standalone | Distinct accessibility values (true, false) |
| `dim_primary_platform` | Ontology standalone | Distinct platform text values |
| `dim_venue` | Ontology standalone | Distinct venue text values |

**Fact grain and query examples**

The finest grain of `fact_sections` is one row per section instance per respondent.
With the FK columns above, useful queries become straightforward:

```sql
-- Dietary requirements by session role
SELECT dr.value AS dietary, sr.value AS role, COUNT(*) AS n
FROM surveyreport.fact_sections f
JOIN surveyreport.dim_diet      dr ON dr.id = f.dietary_key
JOIN surveyreport.dim_role      sr ON sr.id = f.session_role_key
WHERE f.name = 'Session'
GROUP BY dr.value, sr.value;

-- Count of sessions requiring accessibility accommodations by format
SELECT fmt.value AS format, COUNT(*) AS sessions
FROM surveyreport.fact_sections f
JOIN surveyreport.dim_format       fmt ON fmt.id = f.session_format_key
JOIN surveyreport.dim_accessibility acc ON acc.id = f.accessibility_key  -- via standalone
WHERE f.name = 'Session'
  AND acc.value = 'true'
GROUP BY fmt.value;

-- Time slots per session, broken down by format
SELECT st.value AS session_title, COUNT(*) AS time_slots
FROM surveyreport.fact_sections f
JOIN surveyreport.dim_session_title st ON st.id = f.session_title_key  -- via standalone
WHERE f.name = 'TimeSlot'
GROUP BY st.value
ORDER BY time_slots DESC;
```

**Design notes**

- **Multi-value questions (Q16, Q20)**: `CHECKBOX_GROUP` and `MULTI_SELECT` answers may
  store comma-separated coded values in a single `text_value`. The ETL processes these
  as-is; the standalone `dim_track` and `dim_prior_conference` tables will contain one row
  per distinct value seen. Queries that need per-track counts will require a string-split
  or a normalized bridge table, which is out of scope for the initial ETL run.
- **PII exclusion**: Q4–Q9 (name, email, password, phone, DOB) are excluded from ontology
  tagging. First name feeds the `{FNAME}` token via a relationship TEXT action only and
  must not appear in the reporting schema.
- **Extended ontologies** (rows 10–15) are optional and can be omitted from the initial
  seed without affecting the core star schema or the `fact_sections` FK columns.
