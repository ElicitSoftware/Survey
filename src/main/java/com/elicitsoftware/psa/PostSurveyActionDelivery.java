package com.elicitsoftware.psa;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.PostSurveyAction;
import com.elicitsoftware.model.RespondentPSA;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

/**
 * Delivers a survey's post-survey actions for a respondent who has finished, and retries the calls
 * that failed (UC-015). The delivery record, {@code survey.respondent_psa}, moves through Survey's
 * own states (BR-004); a status a service writes on the same record is never overwritten.
 * <p>
 * Every database write commits on its own before the next step, so a service writing the same
 * record during the call finds it (UC-015 step 2), and no call is ever made inside a transaction.
 */
@ApplicationScoped
public class PostSurveyActionDelivery {

    public static final String PENDING = "PENDING";
    public static final String RESENDING = "RESENDING";
    public static final String DELIVERED = "DELIVERED";
    public static final String CALL_FAILED = "CALL_FAILED";
    public static final String CALL_REFUSED = "CALL_REFUSED";

    /** The width of {@code respondent_psa.error_msg}. */
    static final int ERROR_WIDTH = 255;

    @Inject
    EntityManager em;

    @Inject
    PostSurveyActionCredentials credentials;

    /** UC-015 BR-005: whether the scheduled retry runs at all. */
    @ConfigProperty(name = "elicit.post-survey-action.retry.enabled", defaultValue = "true")
    boolean retryEnabled;

    /** UC-015 BR-005: how often the scheduled retry runs (read by {@link #scheduledRetry}). */
    @ConfigProperty(name = "elicit.post-survey-action.retry.interval", defaultValue = "15m")
    String retryInterval;

    /** UC-015 BR-005: the attempts a delivery gets, the first call included. */
    @ConfigProperty(name = "elicit.post-survey-action.retry.max-attempts", defaultValue = "24")
    int maxAttempts;

    /** UC-015 BR-005: the attempts a delivery gets; a method, so a caller through the proxy reads it. */
    public int maxAttempts() {
        return maxAttempts;
    }

    // Package-private so a test can point it at a short timeout.
    PostSurveyActionCaller caller = new PostSurveyActionCaller();

    /** UC-015 main scenario: every action of the respondent's survey, in execution order (BR-006). */
    public void deliverAll(int respondentId) {
        List<Integer> actionIds = QuarkusTransaction.requiringNew().call(() -> ids(em.createNativeQuery("""
                        SELECT a.id FROM survey.post_survey_actions a
                        JOIN survey.respondents r ON r.survey_id = a.survey_id
                        WHERE r.id = ?1
                        ORDER BY a.execution_order, a.id""")
                .setParameter(1, respondentId).getResultList()));
        for (Integer actionId : actionIds) {
            try {
                Integer rowId = QuarkusTransaction.requiringNew().call(() -> prepare(respondentId, actionId));
                attempt(rowId, respondentId, actionId);
            } catch (RuntimeException e) {
                // BR-006: one action's trouble never stops the next.
                Log.errorf(e, "Post survey action %d could not be delivered for respondent %d", actionId, respondentId);
            }
        }
    }

    /** UC-015 step 2 and A5: the delivery record, pending or being resent, committed before the call. */
    Integer prepare(int respondentId, int actionId) {
        RespondentPSA row = RespondentPSA.find("respondentId = ?1 and psaId = ?2", (long) respondentId, (long) actionId).firstResult();
        if (row == null) {
            row = new RespondentPSA();
            row.respondentId = respondentId;
            row.psaId = actionId;
            row.status = PENDING;
            row.persistAndFlush();
        } else {
            row.status = RESENDING;
            row.error = null;
            row.uploadedDt = null;
        }
        return row.id;
    }

    /** UC-015 steps 3-6, A1, A2: one call and its outcome. */
    void attempt(Integer rowId, int respondentId, int actionId) {
        PostSurveyAction psa = QuarkusTransaction.requiringNew().call(() -> {
            PostSurveyAction found = PostSurveyAction.findById(actionId);
            if (found != null) {
                em.detach(found);
            }
            return found;
        });
        if (psa == null) {
            return; // the action was removed meanwhile (Admin UC-017 BR-113)
        }
        try {
            caller.refuseUnsendable(psa);
            PostSurveyActionCaller.Identity identity = psa.sendsExternalId ? identity(respondentId) : PostSurveyActionCaller.Identity.NONE;
            Optional<String> authorization = credentials.authorization(psa.postSurveyActionKey);
            caller.call(psa, respondentId, identity, authorization);
            record(rowId, DELIVERED, null, true);
            Log.debugf("Post survey action %s delivered for respondent %d", psa.name, respondentId);
        } catch (PostSurveyActionCaller.CallRefused refused) {
            record(rowId, CALL_REFUSED, refused.getMessage(), false);
            Log.warnf("Post survey action %s refused for respondent %d: %s", psa.name, respondentId, refused.getMessage());
        } catch (Exception e) {
            record(rowId, CALL_FAILED, e.getMessage(), true);
            Log.errorf("Post survey action %s failed for respondent %d: %s", psa.name, respondentId, e.getMessage());
        }
    }

    /**
     * UC-015 BR-004: writes over Survey's own states only. A status the service wrote during the
     * call means the call reached it, and that record is left as the service left it.
     */
    void record(Integer rowId, String status, String error, boolean countAttempt) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                        UPDATE survey.respondent_psa
                           SET status = ?1, error_msg = ?2, tries = tries + ?3
                         WHERE id = ?4 AND status IN ('PENDING', 'RESENDING')""")
                .setParameter(1, status)
                .setParameter(2, truncate(error))
                .setParameter(3, countAttempt ? 1 : 0)
                .setParameter(4, rowId)
                .executeUpdate());
    }

    /** UC-015 BR-001: the subject's External ID and the department's code, through {@code survey.status}. */
    PostSurveyActionCaller.Identity identity(int respondentId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(
                            "SELECT xid, department_code FROM survey.status WHERE respondent_id = ?1")
                    .setParameter(1, respondentId).setMaxResults(1).getResultList();
            if (rows.isEmpty()) {
                return PostSurveyActionCaller.Identity.NONE;
            }
            Object[] row = rows.get(0);
            return new PostSurveyActionCaller.Identity(
                    row[0] == null ? null : row[0].toString(), row[1] == null ? null : row[1].toString());
        });
    }

    /** UC-015 A3, A4: the schedule; the retry itself is {@link #retryFailedCalls()}. */
    @Scheduled(identity = "post-survey-action-retry", every = "${elicit.post-survey-action.retry.interval:15m}",
            delayed = "1m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledRetry() {
        if (!retryEnabled) {
            return;
        }
        int retried = retryFailedCalls();
        if (retried > 0) {
            Log.infof("Retried %d failed post-survey action call(s); next run in %s", retried, retryInterval);
        }
    }

    /**
     * UC-015 A3, BR-005: calls again every delivery that failed by the call, whose action still
     * exists and that has had fewer than {@code max-attempts} attempts. Each is claimed first, so
     * instances sharing the database never call it twice. Returns how many were called.
     */
    public int retryFailedCalls() {
        @SuppressWarnings("unchecked")
        List<Object[]> due = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery("""
                        SELECT p.id, p.respondent_id, p.post_survey_action_id
                          FROM survey.respondent_psa p
                          JOIN survey.post_survey_actions a ON a.id = p.post_survey_action_id
                         WHERE p.status = 'CALL_FAILED' AND p.tries < ?1
                         ORDER BY p.id""")
                .setParameter(1, maxAttempts).getResultList());
        int retried = 0;
        for (Object[] row : due) {
            Integer rowId = ((Number) row[0]).intValue();
            int respondentId = ((Number) row[1]).intValue();
            int actionId = ((Number) row[2]).intValue();
            int claimed = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                            "UPDATE survey.respondent_psa SET status = 'RESENDING' WHERE id = ?1 AND status = 'CALL_FAILED'")
                    .setParameter(1, rowId).executeUpdate());
            if (claimed == 1) {
                try {
                    attempt(rowId, respondentId, actionId);
                    retried++;
                } catch (RuntimeException e) {
                    Log.errorf(e, "Retrying post survey action %d for respondent %d failed", actionId, respondentId);
                }
            }
        }
        return retried;
    }

    private static List<Integer> ids(List<?> rows) {
        return rows.stream().map(o -> ((Number) o).intValue()).toList();
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= ERROR_WIDTH ? error : error.substring(0, ERROR_WIDTH - 1) + "…";
    }
}
