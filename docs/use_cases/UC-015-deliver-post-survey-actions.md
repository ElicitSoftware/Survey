# Use Case: Deliver Post-Survey Actions

## Overview

**Use Case ID:** UC-015  
**Use Case Name:** Deliver Post-Survey Actions  
**Primary Actor:** Survey (the system, on a respondent finishing, and on its retry schedule)  
**Secondary Actors:** External Post-Survey-Action Service; Respondent (whose finishing starts it, UC-004)  
**Goal:** Tell every post-survey action of a survey that a respondent has finished, calling each one as its author configured it and with the credential the site configured for it, and keep calling an action whose call failed until it is delivered or a limit is reached, without ever repeating a call that reached the service.  
**Status:** Implemented  
**Requirements:** [FR-011, FR-037, FR-038](../requirements.md)

## Preconditions

- A respondent of the survey has just finished (UC-004 step 4), or the retry schedule has come round.
- The survey lists one or more post-survey actions, each with a method, an address and whether it sends the subject's External ID (Author UC-052, applied by Admin UC-017).

## Main Success Scenario

1. The system takes the survey's actions in execution order.
2. For each action, the system records the delivery as pending for this respondent, and makes that record visible before calling, so that a service which records its own progress on the same delivery finds it.
3. The system builds the call: the action's method; its address with the respondent's id, and the subject's External ID where the action sends it, filled into the address's placeholders; for a method that carries a body, the respondent's id, and the External ID with the department's code where the action sends them.
4. The system adds the credential the site configured for this action, if any (BR-003).
5. The system calls the action and waits a bounded time for the answer.
6. The service answers with success, and the system records the delivery as delivered, unless the service has already recorded progress of its own on it, which the system then leaves as it is.
7. The system moves on to the next action; one action's outcome never affects another's.
8. Every action of the survey has been called once for this respondent, and each delivery record says how that call went.

## Alternative Flows

### A1: The call fails

**Trigger:** The service does not answer in time, cannot be reached, or answers with a status other than success (step 6)  
**Flow:**

1. The system records the delivery as failed by the call, with the reason and one more attempt counted, unless the service has already recorded progress of its own, in which case the call reached it and the system leaves the record as it is.
2. Use case continues at step 7.

### A2: The call is refused before it is made

**Trigger:** The action sends the External ID to an address that is not `https`, or its address needs the External ID and the respondent's subject has none (step 3)  
**Flow:**

1. The system does not call the action, and records the delivery as refused, with the reason; a refused delivery is never retried, because nothing a retry does can change the outcome.
2. Use case continues at step 7.

### A3: The retry schedule comes round

**Trigger:** The configured retry interval has passed (step 1)  
**Flow:**

1. The system finds the deliveries that failed by the call, that can be retried, whose action still exists and that have had fewer attempts than the configured limit.
2. For each, the system claims the delivery, so that no other instance of the system retries it at the same time, and continues from step 3 for that one action.
3. A delivery that reaches the limit stays failed by the call and is not retried again.
4. Use case ends.

### A4: Retrying is turned off

**Trigger:** The site has turned retries off (step 1 of A3)  
**Flow:**

1. The schedule does nothing; failed deliveries stay as they are.
2. Use case ends.

### A5: The respondent finishes again

**Trigger:** The respondent already has a delivery record for the action (step 2)  
**Flow:**

1. The system records the delivery as being resent, clears its error, and calls the action again.
2. Use case continues at step 3.

## Postconditions

### Success Postconditions

- Each action of the survey has a delivery record for the respondent, saying delivered, a service's own progress, failed by the call (with the reason and the number of attempts), or refused (with the reason).

### Failure Postconditions

- The respondent's finishing is never undone or delayed by an action: a failure is recorded on that action's delivery and, where it can be, retried later.

## Business Rules

### BR-001: The call is the action's own

The method is the action's (`POST`, `GET` or `PUT`; `POST` when a definition file says nothing). The address is called as written after its placeholders are filled. `{respondentId}` is the respondent's id; `{externalId}` is the subject's External ID, and is allowed only on an action that sends it. Each is URL-encoded. A `POST` or `PUT` carries the JSON body `{"id": <respondent id>}`, to which an action that sends the External ID adds `"externalId"` (or `null` when the subject has none) and `"departmentCode"`. A `GET` carries no body.

### BR-002: The External ID travels only over https

An External ID usually identifies a person in another system. An action that sends it is called only at an `https` address; otherwise the call is refused before it is made (A2). Author and Admin refuse such an action too (Author UC-052, Admin UC-017), so this rule only ever meets a definition edited by hand.

### BR-003: Credentials are the site's, keyed by the action

A credential is never stored with the action or in a definition file. A site configures it in Survey under the action's key: `elicit.post-survey-action."<key>".bearer-token`, sent as `Authorization: Bearer <token>`, or `elicit.post-survey-action."<key>".oidc-client`, naming a configured OIDC client whose token is sent the same way. An action with neither is called without a credential. No credential is ever logged, recorded or shown; startup diagnostics say only whether each action has one (UC-007).

### BR-004: Survey owns the call, not what the service does next

A delivery record moves through Survey's own states: `PENDING` (or `RESENDING`), then `DELIVERED`, `CALL_FAILED` (retried) or `CALL_REFUSED` (refused before the call, A2, never retried). Survey writes over its own states only. A status a service has written on the record (FHHS writes `STARTED`, `COMPLETED` and `FAILED`) means the call reached the service, and Survey never overwrites it, never counts it as a call failure and never retries it; the service retries its own work if it wants to.

### BR-005: Retries are bounded, scheduled and claimed

`elicit.post-survey-action.retry.enabled` (default `true`) turns the schedule on, `elicit.post-survey-action.retry.interval` (default `15m`) sets how often it runs, and `elicit.post-survey-action.retry.max-attempts` (default `24`) caps the attempts a delivery gets, the first call included. Each retried delivery is claimed with a conditional update before it is called, so instances of Survey sharing a database never call it twice. A refused call (`CALL_REFUSED`, A2) is never retried. A retry moves a claimed `CALL_FAILED` delivery to `RESENDING` before it is called.

### BR-006: Each action is independent and ordered

Actions are called in execution order, each on its own; a failure, a refusal or a timeout of one never stops the next (formerly UC-004 BR-010).

### BR-007: The answer's body is not read

Any 2xx status is success. The body of the answer is never read, except to word the error recorded for a failure.

### BR-008: The call is bounded

Survey waits 5 seconds to connect and 60 seconds for the answer, on the first call and on every retry.
