# UC-001: Enter Survey via Access Code

## Overview

- **ID:** UC-001
- **Name:** Enter Survey via Access Code
- **Primary Actor:** Respondent
- **Goal:** A respondent uses an access code supplied to them (e.g., via an invitation email from the Admin app) to authenticate and reach either the survey-taking flow or the reports flow.
- **Status:** Implemented

## Preconditions

- A survey exists in the system.
- The respondent has been issued an access code associated with a survey (out of scope — issued by the companion Admin app), OR the deployment has auto-registration enabled for testing.

## Main Success Scenario

1. Respondent navigates to the login page, optionally through an invitation link that carries the access code (`/login/<access code>`, A6).
2. System presents an access code entry field, and — if more than one survey is configured — a survey selector.
3. Respondent enters their access code (5–12 characters, case-sensitive) and selects a survey if prompted.
4. Respondent clicks Login (or presses Enter).
5. System validates the access code length and looks up a matching, existing respondent record for the selected survey and access code.
6. System records the login: increments the respondent's login count, and sets the first-access timestamp if this is the first login.
7. System routes the respondent onward: to the survey-answering flow if the respondent's survey is still active, or to the reports flow if the respondent has already finalized the survey.

## Alternative Flows

**A1: Access code not found**
- **Trigger:** No respondent record matches the entered access code for the selected survey.
- System shows an "Invalid access code" notification and the respondent remains on the login page.
- System logs the failure at WARN, naming the survey and how many attempts have failed on this page, and counts it on the `survey.login.failed` metric (BR-020). The code that was typed is in neither.
- Note: the code contains commented/documented fallback rules (search by access code across all active surveys; fall back to the lowest survey id) that are described in a Javadoc comment on `AccessCodeService.getUser` but are not actually implemented — only an exact survey + access code match is performed. This is a gap between documented intent and behavior, not a supported flow.

**A2: Auto-registration enabled**
- **Trigger:** No respondent record matches the access code, and the `accessCode.autoRegister` configuration property is `true` (off by default; on in the dev and test profiles).
- System automatically creates and activates a new respondent for the entered access code and proceeds to the survey-answering flow.
- The login page tells the respondent to enter an access code of 5 to 12 characters (BR-001). It shows no warning: auto-registration is a site's choice (BR-002).

**A3: Access code fails client-side validation**
- **Trigger:** The entered access code is empty, or shorter than 5 characters, or longer than 12 characters.
- The access code field is marked invalid with an inline error message; the Login action is blocked until corrected.

**A4: No surveys configured**
- **Trigger:** The system has zero surveys defined.
- The login page displays "There are no surveys to login" and no access code field is usable.

**A5: Returning respondent with an existing browser session**
- **Trigger:** The respondent already has an active browser session with survey/respondent state.
- System skips the login form entirely and routes directly to the survey-answering or reports flow per step 7.

**A6: The link carries the access code**
- **Trigger:** The respondent opens `/login/<access code>`, with or without a query such as `?lang=es-419`.
- System fills the access code field from the link and replaces the page address with `/login`, keeping the query, so the code is left neither in the address bar nor in the browser history (BR-021).
- The respondent still chooses a survey if asked and clicks Login; nothing is submitted for them.

## Postconditions

**Success:**
- The respondent's identity (respondent id, survey) is established for the browser session.
- The respondent is routed to the section they should see next (new or in-progress survey) or to their reports (already finalized).

**Failure:**
- No respondent session is established; the respondent remains on the login page with an error notification.

## Business Rules

- **BR-001:** An access code must be between 5 and 12 characters, inclusive, and is case-sensitive.
- **BR-002:** Auto-registration of a respondent from an unrecognized access code is only permitted when `accessCode.autoRegister` is enabled; it defaults to disabled, and a site may enable it to let people start a survey without being registered beforehand. An auto-registered respondent has no subject row: no name, contact details, External ID or department. Admin lists it as "Auto-registered" for every console user (Admin UC-002 BR-181), and a post-survey action receives no subject identity for it. Anyone who can reach the login page can start the survey, so no access code is ever a failed attempt (BR-020) and the code a respondent picks is the only thing that brings them back to their answers.
- **BR-003:** A respondent whose survey is still active (not finalized) is routed to the survey-answering flow on login; a respondent who has finalized the survey is routed to the reports flow instead.
- **BR-020:** An access code that matches no respondent is a failed attempt: it is logged at WARN and counted by survey on the `survey.login.failed` metric. The code that was typed is never logged. Restoring a session after a reconnect is not an attempt, whatever its outcome, and with auto-registration on (BR-002) no attempt fails.
- **BR-021:** Once the login page has read an access code from its address, it replaces the address with the plain login address. The access log does not record the request that carried the code (NFR-015).
