# V005 Library Card Registration Survey — Flow Diagram

```mermaid
flowchart TD
    subgraph STEP1["① Step: Welcome"]
        subgraph SEC_W["Section: Welcome"]
            Q36["Q36 · HTML\nWelcome Block"]
            Q37["Q37 · CHECKBOX\nTerms of Use ✱"]
            Q38["Q38 · HTML\nTerms Confirmed"]
        end
    end

    subgraph STEP2["② Step: Patron"]
        subgraph SEC_P["Section: Patron Information"]
            Q39["Q39 · TEXT · First Name ✱"]
            Q40["Q40 · TEXT · Last Name ✱"]
            Q41["Q41 · TEXT · Email ✱"]
            Q42["Q42 · PASSWORD · PIN ✱"]
            Q43["Q43 · TEXT · Phone"]
            Q44["Q44 · DATE_PICKER · Date of Birth ✱"]
            Q45["Q45 · RADIO · Want Digital Access? ✱"]
        end
        subgraph SEC_D["Section: Digital Access  〔conditional〕"]
            Q46["Q46 · COMBOBOX · Ebook Format ✱"]
            Q47["Q47 · CHECKBOX · Arrival Notifications"]
            Q48["Q48 · CHECKBOX · Notify via Email {EMAIL}"]
            Q48b["Q48b · CHECKBOX · Notify via SMS {PHONE}"]
        end
    end

    subgraph STEP3["③ Step: Preferences"]
        subgraph SEC_CP["Section: Collection Preferences"]
            Q49["Q49 · INTEGER · Checkout Count 1–10 ✱"]
            Q50["Q50 · CHECKBOX_GROUP · Media Types ✱"]
            Q51["Q51 · TEXT · Fav Audiobook Narrator"]
            Q52["Q52 · TEXT · Streaming Service"]
            Q53["Q53 · MULTI_SELECT · Literary Genres"]
            Q54["Q54 · TEXTAREA · Mystery Authors"]
            Q55["Q55 · DOUBLE · Weekly Reading Hours"]
            Q56["Q56 · RADIO · Visit Frequency ✱"]
            Q57["Q57 · TEXT · Preferred Branch"]
        end
        subgraph SEC_MP["Section: Media Preferences  〔conditional〕"]
            Q58["Q58 · TEXT · DVD Genres"]
            Q59["Q59 · DATE_PICKER · Availability Date"]
            Q60["Q60 · DATETIME · Preferred Pickup Time"]
        end
    end

    subgraph STEP4["④ Step: CheckoutRequest  🔁 ×Q49 repeats"]
        subgraph SEC_C["Section: Checkout {S#}  🔁 repeated per item"]
            Q61["Q61 · TEXT · Item Title ✱"]
            Q62["Q62 · INTEGER · Renewals Needed 0–3 ✱"]
            Q63["Q63 · RADIO · Item Type ✱"]
            Q64["Q64 · TEXT · Format Notes"]
            Q65["Q65 · CHECKBOX · Place on Hold"]
            Q66["Q66 · TEXTAREA · Hold Instructions"]
        end
        subgraph SEC_R["Section: Renewal {S#}  🔁 ×Q62 repeats"]
            Q67["Q67 · DATE_PICKER · Renewal Date {Q#} ✱"]
            Q68["Q68 · COMBOBOX · Pickup Branch ✱"]
            Q69["Q69 · TEXT · Alternate Contact"]
        end
    end

    STEP1 --> STEP2 --> STEP3 --> STEP4

    Q36 --> Q37
    Q37 -->|"R18: BOOLEAN SHOW"| Q38

    Q39 --> Q40 --> Q41 --> Q42 --> Q43 --> Q44 --> Q45
    Q45 -->|"R21: EQUAL 'TRUE'\nSHOW section"| SEC_D
    Q41 -.->|"R_em: FIELD_EXIST TEXT\n{EMAIL} token"| Q48
    Q43 -.->|"R_phone: FIELD_EXIST TEXT\n{PHONE} token"| Q48b
    Q47 -->|"R22: BOOLEAN SHOW"| Q48
    Q47 -->|"R22b: BOOLEAN SHOW"| Q48b

    Q49 -->|"R23: GREATER_THAN 0\nREPEAT section"| SEC_C
    Q50 -->|"R24: CONTAINS 'dvd'\nSHOW section"| SEC_MP
    Q50 -->|"R25: CONTAINS 'audiobook'\nSHOW ← chain L1"| Q51
    Q51 -->|"R26: FIELD_EXIST SHOW\n← chain L2"| Q52
    Q53 -->|"R27: CONTAINS 'mystery'\nSHOW"| Q54
    Q56 -->|"R28: NOT_EQUAL 'never'\nSHOW"| Q57

    Q62 -->|"R29: GREATER_THAN 0\nREPEAT section"| SEC_R
    Q63 -->|"R30: NOT_EQUAL 'book'\nSHOW"| Q64
    Q65 -->|"R31: BOOLEAN SHOW"| Q66
```

## Legend

| Symbol | Meaning |
|--------|---------|
| ✱ | Required question |
| 🔁 | Repeated section (REPEAT action) |
| `〔conditional〕` | Section shown only when condition is met |
| Solid arrow `-->` | Conditional SHOW relationship |
| Dashed arrow `-.->` | TEXT token substitution (R_em / R_phone) |

## Relationship Summary

| ID | Upstream | Operator | Value | Action | Downstream |
|----|----------|----------|-------|--------|------------|
| R18 | Q37 Terms | BOOLEAN | — | SHOW | Q38 Confirmation |
| R_em | Q41 Email | FIELD_EXIST | — | TEXT `{EMAIL}` | Q48 |
| R_phone | Q43 Phone | FIELD_EXIST | — | TEXT `{PHONE}` | Q48b |
| R21 | Q45 Digital? | EQUAL | `TRUE` | SHOW section | Digital Access |
| R22 | Q47 Notifications | BOOLEAN | — | SHOW | Q48 Email notify |
| R22b | Q47 Notifications | BOOLEAN | — | SHOW | Q48b SMS notify |
| R23 | Q49 Checkout count | GREATER_THAN | `0` | REPEAT section | Checkout {S#} |
| R24 | Q50 Media types | CONTAINS | `dvd` | SHOW section | Media Preferences |
| R25 | Q50 Media types | CONTAINS | `audiobook` | SHOW | Q51 Narrator (chain L1→L2) |
| R26 | Q51 Narrator | FIELD_EXIST | — | SHOW | Q52 Streaming (chain L2→L3) |
| R27 | Q53 Genres | CONTAINS | `mystery` | SHOW | Q54 Mystery authors |
| R28 | Q56 Visit freq | NOT_EQUAL | `never` | SHOW | Q57 Preferred branch |
| R29 | Q62 Renewals | GREATER_THAN | `0` | REPEAT section | Renewal {S#} |
| R30 | Q63 Item type | NOT_EQUAL | `book` | SHOW | Q64 Format notes |
| R31 | Q65 Hold? | BOOLEAN | — | SHOW | Q66 Hold instructions |
