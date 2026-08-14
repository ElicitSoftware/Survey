# UC-006: Log Out

## Overview

- **ID:** UC-006
- **Name:** Log Out
- **Primary Actor:** Respondent
- **Goal:** The respondent ends their session, clearing any locally-held survey/respondent state so a subsequent visit starts fresh (or re-authenticates via UC-001).
- **Status:** Implemented

## Preconditions

- The respondent has an active browser session (may or may not have finalized the survey).

## Main Success Scenario

1. Respondent navigates to Logout (e.g., a link/route in the application).
2. System clears all session-held survey and respondent state.
3. System redirects the respondent's browser to the home/login page.

## Alternative Flows

None identified — this is a simple, single-path action.

## Postconditions

**Success:**
- The browser session no longer references any respondent or survey; visiting the application again starts at UC-001.

**Failure:**
- Not applicable; clearing local session state is not expected to fail.

## Business Rules

- None specific to this use case beyond the general session-handling rules captured under UC-001.

## Notes / Known Gaps

- The redirect deliberately uses a raw browser navigation rather than the UI framework's normal in-app navigation, specifically to avoid a "session expired" message from appearing during the same operation that intentionally invalidates the session. This is a considered workaround, not a defect.
