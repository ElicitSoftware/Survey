# Use Case: Complete the Survey in My Language

## Overview

**Use Case ID:** UC-009  
**Use Case Name:** Complete the Survey in My Language  
**Primary Actor:** Respondent  
**Goal:** The respondent sees every page of the application — labels, buttons, messages, page titles and the brand name — in a language they read, and the survey's own questions and answer options in that language wherever the survey publishes a translation of them, laid out right-to-left when that language requires it, so that they can complete the survey without understanding English.  
**Status:** Implemented

## Preconditions

- The application ships its own texts in every language it supports (English, Latin American Spanish and Arabic), packaged inside the release.
- The site may offer fewer than the application ships; the languages it offers are the ones in the selector.
- The respondent has an invitation link, which may carry a language, or navigates to the login page directly.
- The survey declares the language its content was authored in and the set of languages its content has been published in; either set may be empty of anything but the base language.

## Main Success Scenario

1. Respondent opens the invitation link or the login page.
2. System determines the language to use: the language carried in the link if present and supported, otherwise the language chosen earlier in this browser session, otherwise the browser's preferred language, otherwise English.
3. System displays the login page with every application text in that language, and with the page laid out right-to-left when the language is written right-to-left.
4. Respondent chooses a different language from the language selector shown on every page.
5. System redisplays the current page in the chosen language, applies the matching layout direction, and remembers the choice for the rest of the browser session.
6. Respondent logs in, answers the survey, reviews and finalizes it, and views the reports.
7. System presents every application page of that journey in the chosen language, and presents each piece of survey content (question text, answer options, step and section names, tooltips, validation messages, report titles) in that language where the survey publishes a current translation of it and in the survey's base language where it does not, and the respondent completes the survey in their language.

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

### A3: A piece of survey content is untranslated or out of date

**Trigger:** The respondent's language is one of the survey's content languages, but a question, option or name has no translation in it, or its translation was made from base text that has since been edited (step 7)  
**Flow:**

1. System shows that one string in the survey's base language and every other string in the respondent's language.
2. Use case continues at step 7.

### A4: The survey publishes nothing in the respondent's language

**Trigger:** The chosen language is not one of the survey's content languages (step 7)  
**Flow:**

1. System shows the application's own texts in the chosen language and all survey content in the survey's base language.
2. Use case continues at step 7.

## Postconditions

### Success Postconditions

- The chosen language is remembered for the browser session and applied to every page the respondent visits until they log out or close the browser.
- Survey answers and reports are unaffected by the language choice; what differs is the application's own texts, the layout direction, and which language each piece of survey content is shown in.
- Each answer records the language its label was rendered in, so what the respondent was shown can be reconstructed later.

### Failure Postconditions

- If no language can be determined, the application is shown in English; the respondent can still complete the survey.

## Business Rules

### BR-001: English is the fallback language

English is the default language and the fallback for any text missing from another language file. A text missing from every language file is shown as a visible marker (`!key!`) rather than blank, so the omission is noticed and fixed.

### BR-002: Available languages are the ones the release carries, less any the site withholds

Every language the application offers is packaged inside the release, so the wording a respondent reads is the wording that version was built and tested with. A site cannot add a language or change one; it can only offer fewer than the release carries, and a language it withholds is unreachable — absent from the selector, refused in a link, and not served for survey content either.

Languages are curated and arrive in a release, which is what keeps a translation and the code that renders it at the same version.

### BR-003: Language precedence

Link language, then session choice, then browser preference, then English. A language that is not available is skipped, never partially applied.

### BR-004: Layout direction follows the language

Languages written right-to-left (Arabic, Hebrew, Persian, Urdu and similar) mirror the whole page layout; all other languages are laid out left-to-right. The release declares the direction of any language whose script does not follow from its language alone, and a site may override that for one language by configuration.

### BR-005: Survey content follows the respondent's language where it is published

Question text, answer options, step and section names, tooltips, validation messages and report titles appear in the respondent's language when the survey publishes a current translation of them, and in the survey's base language when it does not. The fallback is per string, not per page: one untranslated question sits among translated ones rather than returning the whole survey to the base language. Message templates and the body of an external report service are outside this mechanism and keep the language they were authored in.

### BR-006: Brand names are localized by the brand

The organization name and description supplied by the mounted brand may carry per-language variants; the application shows the variant for the current language and falls back to the brand's base text. The brand's technical identifiers are never translated.

### BR-007: The language choice never crosses sessions

The choice is held for the browser session only and is never used to pre-select a language on a later visit; no language preference is stored against the respondent. The language each answer's label was rendered in is recorded with that answer, as a record of what the respondent saw rather than as a preference.

### BR-008: An out-of-date translation is not shown

A translation is made from one particular wording of the base text. When that wording is edited the translation is out of date, and the base text is shown in its place until the string is translated again, so that every language asks the same question. A deployment may choose to show out-of-date translations instead.

### BR-009: A content language is served only where the site offers that language

Survey content reaches a respondent in a language only when that language is both published for the survey and offered by the site for the application's own texts. A site that withholds a language therefore holds that survey's translations without ever serving them, and no respondent sees translated questions between base-language buttons.

### BR-010: A respondent in progress keeps the wording they started with

Content translations are versioned like the survey's structure. A respondent sees the translation that was current when they first accessed the survey; a correction published later reaches only respondents who start after it.

### BR-011: Text size may follow the language

A script whose letters look smaller than Latin at the same size reads smaller, however faithful the translation — Arabic is the case this rule exists for. The release may therefore declare a font scale for a language, and a site may override it by configuration; every page shown in that language is rendered at that multiple of the reader's own text size. A language with no declared scale is rendered exactly as it was before any scale existed, and a scale outside what a layout can absorb is refused rather than applied.
