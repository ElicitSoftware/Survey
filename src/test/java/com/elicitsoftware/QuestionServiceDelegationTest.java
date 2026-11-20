package com.elicitsoftware;

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

import com.elicitsoftware.etl.ETLRespondentService;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.response.NavResponse;
import com.elicitsoftware.response.NavigationItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions (page render, UC-012 BR-004 activity stamp), UC-004: Finalize
 * Survey and UC-009 BR-005 (relocalize) — QuestionService's orchestration, which decides what
 * runs, in which order and against which respondent, while the work itself is QuestionManager's
 * and the ETL's. Its collaborators are hand-written subclasses that record their calls, and the
 * {@code self} proxy the transaction boundaries go through is one of them too, so the two
 * transactions of {@code init} are visible as two separate calls.
 * <p>
 * {@code review}, {@code saveAnswer} and {@code PostSurveyActions} start with a Panache finder
 * ({@code Respondent.findById}, {@code Answer.findById}) and are left to the Quarkus tests.
 */
class QuestionServiceDelegationTest {

    private final List<String> calls = new ArrayList<>();
    private final NavResponse page = new NavResponse(null, null, List.of(), new NavigationItem[0]);

    /** Records every QuestionManager entry point the service uses. */
    private QuestionManager recordingManager() {
        return new QuestionManager() {
            @Override
            public void recordActivity(int respondentId) {
                calls.add("manager.recordActivity " + respondentId);
            }

            @Override
            public void init(int respondentId, String key) {
                calls.add("manager.init " + respondentId + " " + key);
            }

            @Override
            public NavResponse navigate(Integer respondentId, String key) {
                calls.add("manager.navigate " + respondentId + " " + key);
                return page;
            }

            @Override
            public void relocalize(int respondentId) {
                calls.add("manager.relocalize " + respondentId);
            }

            @Override
            public void removeDeleted(Integer respondentId) {
                calls.add("manager.removeDeleted " + respondentId);
            }
        };
    }

    /** Stands in for the CDI proxy {@code self}: records the transactional entry points. */
    private QuestionService recordingSelf() {
        return new QuestionService() {
            @Override
            public void recordActivity(int respondentId) {
                calls.add("self.recordActivity " + respondentId);
            }

            @Override
            public NavResponse renderPage(int respondentId, String displaykey) {
                calls.add("self.renderPage " + respondentId + " " + displaykey);
                return page;
            }
        };
    }

    @Test
    void given_aRespondent_when_init_then_activityIsStampedBeforeThePageIsRenderedInItsOwnTransaction() {
        QuestionService service = new QuestionService();
        service.self = recordingSelf();

        NavResponse response = service.init(8, "1-1-0-1-0-0-0");

        assertSame(page, response);
        assertEquals(List.of("self.recordActivity 8", "self.renderPage 8 1-1-0-1-0-0-0"), calls);
    }

    @Test
    void given_theSessionsRespondent_when_initWithOnlyAKey_then_thatRespondentIsRendered() {
        Respondent respondent = new Respondent();
        respondent.id = 21;
        QuestionService service = new QuestionService();
        service.self = recordingSelf();
        service.sessionDataService = new UISessionDataService() {
            @Override
            public Respondent getRespondent() {
                return respondent;
            }
        };

        NavResponse response = service.init("1-2-0-0-0-0-0");

        assertSame(page, response);
        assertEquals(List.of("self.recordActivity 21", "self.renderPage 21 1-2-0-0-0-0-0"), calls);
    }

    @Test
    void given_aRespondent_when_recordActivity_then_theManagerStampsThem() {
        QuestionService service = new QuestionService();
        service.questionManager = recordingManager();

        service.recordActivity(4);

        assertEquals(List.of("manager.recordActivity 4"), calls);
    }

    @Test
    void given_aKey_when_renderPage_then_missingRowsAreBuiltBeforeNavigating() {
        QuestionService service = new QuestionService();
        service.questionManager = recordingManager();

        NavResponse response = service.renderPage(4, "1-1-0-2-0-0-0");

        assertSame(page, response);
        assertEquals(List.of("manager.init 4 1-1-0-2-0-0-0", "manager.navigate 4 1-1-0-2-0-0-0"), calls);
    }

    @Test
    void given_aLanguageSwitch_when_relocalize_then_theManagerRebuildsThatRespondentsLabels() {
        QuestionService service = new QuestionService();
        service.questionManager = recordingManager();

        service.relocalize(6);

        assertEquals(List.of("manager.relocalize 6"), calls);
    }

    @Test
    void given_aRespondent_when_finalize_then_deactivatePurgeLoadAndPostActionsRunInThatOrder() {
        QuestionService service = new QuestionService() {
            @Override
            public void setActiveFalse(int respondentId) {
                calls.add("setActiveFalse " + respondentId);
            }

            @Override
            public void PostSurveyActions(int respondentId) {
                calls.add("PostSurveyActions " + respondentId);
            }
        };
        service.questionManager = recordingManager();
        service.etlRespondentService = new ETLRespondentService() {
            @Override
            public String populateFactSectionTable(Integer respondentId) {
                calls.add("etl " + respondentId);
                return "loaded 3 sections";
            }
        };

        try (LogCapture log = LogCapture.of(QuestionService.class)) {
            service.finalize(13);

            assertTrue(log.contains(LogCapture.DEBUG, "loaded 3 sections etl took "), log.messages(LogCapture.DEBUG).toString());
            assertTrue(log.contains(LogCapture.DEBUG, "Post survey actions:"));
        }
        assertEquals(List.of("setActiveFalse 13", "manager.removeDeleted 13", "etl 13", "PostSurveyActions 13"),
                calls);
    }

    @Test
    void given_aRespondent_when_setActiveFalse_then_theirRowIsDeactivatedAndStamped() {
        RecordingEntityManager em = new RecordingEntityManager(sql -> List.of());
        QuestionService service = new QuestionService();
        service.entityManager = em.entityManager();

        service.setActiveFalse(17);

        assertEquals(1, em.queries().size());
        RecordingEntityManager.Recorded update = em.queries().get(0);
        assertEquals("UPDATE survey.respondents set active = false, finalized_dt = CURRENT_TIMESTAMP where id = :respondentId",
                update.sql);
        assertEquals(17, update.parameters.get("respondentId"));
        assertEquals(1, update.executeUpdates);
    }
}
