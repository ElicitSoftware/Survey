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

@QuarkusTest
public class TokenServiceTest {

    @Inject
    TokenService service;


    @Test
    public void testGetSurveys() {
        var surveys = service.getSurveys();
        assertNotNull(surveys);
        assertTrue(surveys.containsKey("Surveys"));
    }

    @Test
    public void testGetSurvey() {
        Survey survey = service.getSurvey(1);
        assertNotNull(survey);
    }

    @Test
    @Transactional
    public void testPutToken() {
        AddResponse response = service.putToken(1);
        assertNotNull(response.getToken());
        assertNull(response.getError());
    }

    @Test
    @Transactional
    public void testAddTokenForInvalidSurvey() {
        AddResponse response = service.addToken(3);
        assertEquals("Error Generating Token", response.getError());
    }

    @Test
    @Transactional
    public void testDeactivate() {
        Respondent user = service.deactivate(1);
        assertFalse(user.active);
    }

    @Test
    @Transactional
    public void testLoginWithAutoRegister() {

        RandomStringGenerator generator = new RandomStringGenerator(10);
        Respondent user = service.login(1, generator.nextString());
        assertNotNull(user);
        assertTrue(user.active);
    }
}
