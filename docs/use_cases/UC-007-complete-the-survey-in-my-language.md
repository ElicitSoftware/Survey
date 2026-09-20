# Use Case: Complete the Survey in My Language

## Overview

**Use Case ID:** UC-007  
**Use Case Name:** Complete the Survey in My Language  
**Primary Actor:** Respondent  
**Goal:** The respondent sees every page of the application — labels, buttons, messages, page titles and the brand name — in a language they read, laid out right-to-left when that language requires it, so that they can complete the survey without understanding English.  
**Status:** Implemented

## Preconditions

- The application ships English texts only; the deployment's translations directory supplies every other language (Latin American Spanish and Arabic in the reference deployment).
- The deployment may have mounted further language files or text overrides; if so, those languages are also available.
- The respondent has an invitation link, which may carry a language, or navigates to the login page directly.

## Main Success Scenario

1. Respondent opens the invitation link or the login page.
2. System determines the language to use: the language carried in the link if present and supported, otherwise the language chosen earlier in this browser session, otherwise the browser's preferred language, otherwise English.
3. System displays the login page with every application text in that language, and with the page laid out right-to-left when the language is written right-to-left.
4. Respondent chooses a different language from the language selector shown on every page.
5. System redisplays the current page in the chosen language, applies the matching layout direction, and remembers the choice for the rest of the browser session.
6. Respondent logs in, answers the survey, reviews and finalizes it, and views the reports.
7. System presents every application page of that journey in the chosen language while survey content (questions, answer options, section names) appears in the language it was authored in, and the respondent completes the survey in their language.

## Alternative Flows

### A1: Link carries an unsupported language

**Trigger:** The language in the invitation link is not among the available languages (step 2)  
**Flow:**

1. System ignores the language in the link.
2. Use case continues at step 2 with the next source (session choice, browser language, English).

### A2: A text is missing in the chosen language

**Trigger:** A language file lacks the translation for one of the texts on the page (step 3)  
**Flow:**

1. System shows the English text for that item and the chosen language for everything else.
2. Use case continues at step 4.

### A3: Deployment mounted an additional language

**Trigger:** The deployment operator has mounted a language file the application does not ship (step 4)  
**Flow:**

1. System lists the mounted language in the language selector alongside the shipped ones.
2. Respondent chooses the mounted language.
3. Use case continues at step 5.

### A4: Deployment overrides individual texts

**Trigger:** The deployment operator has mounted a language file containing only some texts for a shipped language (step 3)  
**Flow:**

1. System shows the mounted text for the overridden items and the shipped text for all others.
2. Use case continues at step 4.

## Postconditions

### Success Postconditions

- The chosen language is remembered for the browser session and applied to every page the respondent visits until they log out or close the browser.
- Survey answers and reports are unaffected by the language choice; only the application's own texts and layout direction differ.

### Failure Postconditions

- If no language can be determined, the application is shown in English; the respondent can still complete the survey.

## Business Rules

### BR-001: English is the fallback language

English is the default language and the fallback for any text missing from another language file. A text missing from every language file is shown as a visible marker (`!key!`) rather than blank, so the omission is noticed and fixed.

### BR-002: Available languages are shipped plus mounted

The languages offered are the union of the languages shipped with the application and the language files present in the deployment's mounted translations directory. A mounted file for a shipped language overrides only the texts it contains.

### BR-003: Language precedence

Link language, then session choice, then browser preference, then English. A language that is not available is skipped, never partially applied.

### BR-004: Layout direction follows the language

Languages written right-to-left (Arabic, Hebrew, Persian, Urdu and similar) mirror the whole page layout; all other languages are laid out left-to-right. A deployment may declare the direction of a mounted language explicitly.

### BR-005: Survey content keeps its authored language

Question text, answer options, section and step names, report content and message templates are stored with the survey and appear as authored, whatever language the application chrome uses.

### BR-006: Brand names are localized by the brand

The organization name and description supplied by the mounted brand may carry per-language variants; the application shows the variant for the current language and falls back to the brand's base text. The brand's technical identifiers are never translated.

### BR-007: The language choice never crosses sessions

The choice is held for the browser session only; nothing about the respondent's language is stored with their survey record.
