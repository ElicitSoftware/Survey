package com.elicitsoftware.survey;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.RandomStringGenerator;
import com.elicitsoftware.TokenService;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.response.AddResponse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-001: Enter Survey via Token — covers survey lookup, token issuance,
 * and login (including auto-register) via TokenService.
 */
@QuarkusTest
public class TokenServiceTest {

    @Inject
    TokenService service;


    @Test
    // UC-001: survey selector population
    public void testGetSurveys() {
        var surveys = service.getSurveys();
        assertNotNull(surveys);
        assertTrue(surveys.containsKey("Surveys"));
    }

    @Test
    // UC-001: survey lookup by id
    public void testGetSurvey() {
        Survey survey = service.getSurvey(1);
        assertNotNull(survey);
    }

    @Test
    @Transactional
    // UC-001: token issuance for a valid survey
    public void testPutToken() {
        AddResponse response = service.putToken(1);
        assertNotNull(response.getToken());
        assertNull(response.getError());
    }

    @Test
    @Transactional
    // UC-001 A-flow: token issuance fails for an invalid survey id
    public void testAddTokenForInvalidSurvey() {
        AddResponse response = service.addToken(3);
        assertEquals("Error Generating Token", response.getError());
    }

    @Test
    @Transactional
    // UC-004: deactivate() sets Respondent.active = false (finalize path, separate from QuestionService.finalize)
    public void testDeactivate() {
        Respondent user = service.deactivate(1);
        assertFalse(user.active);
    }

    @Test
    @Transactional
    // UC-001 main success scenario: unrecognized token + auto-register enabled creates an active respondent
    public void testLoginWithAutoRegister() {

        RandomStringGenerator generator = new RandomStringGenerator(10);
        Respondent user = service.login(1, generator.nextString());
        assertNotNull(user);
        assertTrue(user.active);
    }
}
