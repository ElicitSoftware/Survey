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

import com.elicitsoftware.AccessCodeService;
import com.elicitsoftware.RandomStringGenerator;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.response.AddResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import com.elicitsoftware.PostgresTestResource;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-001: Enter Survey via Access Code — covers survey lookup, access code issuance,
 * and login (including auto-register) via AccessCodeService.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
public class AccessCodeServiceTest {

    @Inject
    AccessCodeService service;


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
    // UC-001: access code issuance for a valid survey
    public void testPutAccessCode() {
        AddResponse response = service.putAccessCode(1);
        assertNotNull(response.getAccessCode());
        assertNull(response.getError());
    }

    @Test
    @Transactional
    // UC-001 main success scenario: an issued access code is stored on the respondent and
    // logging in with it returns that same respondent, with the login recorded.
    public void testLoginWithIssuedAccessCode() {
        AddResponse response = service.putAccessCode(1);
        String accessCode = response.getAccessCode();
        assertNotNull(accessCode);

        Respondent stored = Respondent.findBySurveyAndAccessCode(1, accessCode);
        assertNotNull(stored);
        assertEquals(response.getRespondentId(), stored.id);
        assertEquals(accessCode, stored.accessCode);

        stored.active = true;
        Respondent loggedIn = service.login(1, accessCode);
        assertNotNull(loggedIn);
        assertEquals(stored.id, loggedIn.id);
        assertEquals(1, loggedIn.logins);
        assertNotNull(loggedIn.firstAccessDt);
    }

    @Test
    @Transactional
    // UC-001 A-flow: access code issuance fails for an invalid survey id
    public void testAddAccessCodeForInvalidSurvey() {
        // A small hardcoded id (e.g. 3) previously collided with fixture-inserted survey rows
        // added for other tests. Integer.MAX_VALUE can never be a real fixture id.
        AddResponse response = service.addAccessCode(Integer.MAX_VALUE);
        assertEquals("Error Generating Access Code", response.getError());
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
    // UC-001 main success scenario: unrecognized access code + auto-register enabled creates an active respondent
    public void testLoginWithAutoRegister() {

        RandomStringGenerator generator = new RandomStringGenerator(10);
        Respondent user = service.login(1, generator.nextString());
        assertNotNull(user);
        assertTrue(user.active);
    }
}
