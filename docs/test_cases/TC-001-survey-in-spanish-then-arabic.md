# Test Case: Survey in Spanish then Arabic

## Overview

**ID:** TC-001  
**Goal:** A respondent opens the survey from a Spanish invitation link, switches the application to Arabic, and completes the login and first section with every application text in the chosen language and the page mirrored right-to-left — verifying language selection, switching, layout direction and session persistence end-to-end.  
**Priority:** High  
**Status:** Draft

## Roles

- Respondent (opens the invitation link, chooses the language, answers the survey)

## Preconditions

- A survey exists (test seed `src/main/resources/db/test` loaded by the `%test` profile).
- Auto-registration is enabled so any 5–12 character access code creates a respondent (`accessCode.autoRegister=true` in the `%test` profile).
- Spanish (`es-419`) and Arabic (`ar`) language files are mounted from the Elicit umbrella repository (`elicit-i18n/survey/`, reached as `../elicit-i18n` in the test and dev profiles); the application itself ships English only.

## Flow

| Step | Name                      | Description                                                                                                   | Test Data        | Use Case                                                          |
|------|---------------------------|---------------------------------------------------------------------------------------------------------------|------------------|-------------------------------------------------------------------|
| 1    | Open Spanish link         | The respondent opens the login page from a link that carries the Spanish language                             | es-419           | [UC-009](../use_cases/UC-009-complete-the-survey-in-my-language.md) |
| 2    | Verify Spanish login page | The access code label, login button and page title are shown in Spanish and the page is laid out left-to-right | -                | -                                                                 |
| 3    | Switch to Arabic          | The respondent chooses Arabic in the language selector                                                        | ar               | [UC-009](../use_cases/UC-009-complete-the-survey-in-my-language.md) |
| 4    | Verify Arabic login page  | The same texts are shown in Arabic, the page direction is right-to-left and the document language is Arabic   | -                | -                                                                 |
| 5    | Log in                    | The respondent enters an access code and logs in                                                              | TESTCODE1        | [UC-001](../use_cases/UC-001-enter-survey-via-access-code.md)     |
| 6    | Verify Arabic section     | The first section shows its navigation buttons in Arabic while the question text appears as authored          | -                | -                                                                 |
| 7    | Answer first section      | The respondent answers the first section and moves to the next one                                            | -                | [UC-002](../use_cases/UC-002-answer-survey-questions.md)          |
| 8    | Verify language kept      | The next section is still in Arabic and right-to-left without the respondent choosing again                   | -                | -                                                                 |
| 9    | Log out                   | The respondent logs out                                                                                       | -                | [UC-006](../use_cases/UC-006-log-out.md)                          |

## Validation

1. **Session language persists**: After step 9 the login page is still shown in Arabic in the same browser session; a new browser session without a link language falls back to the browser preference.
2. **No untranslated text**: No application text on any visited page is shown as an English fallback or a `!key!` marker.
3. **Content untouched**: The question text shown in step 6 is identical to the authored text regardless of the chosen language.

## Postconditions

- One auto-registered respondent with access code `TESTCODE1` exists for the seeded survey, with the answers given in step 7.
- No language information is stored with the respondent; the seeded survey remains untouched.
