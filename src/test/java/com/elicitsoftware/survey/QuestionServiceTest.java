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

import com.elicitsoftware.QuestionService;
import com.elicitsoftware.TokenService;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.response.AddResponse;
import com.elicitsoftware.response.NavResponse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class QuestionServiceTest {

    @Inject
    QuestionService questionService;

    @Inject
    TokenService tokenService;

    @Test
    public void testInit() {
        NavResponse navResponse = questionService.init(1, "0001-0001-0000-0001-0000-0000-0000");
        assertNotNull(navResponse);
        assertEquals(1, (int) navResponse.getStep().id);
        assertEquals("Welcome", navResponse.getAnswers().get(0).displayText);
    }

    @Test
    @Transactional
    public void testFinalize() {
        AddResponse addResponse = tokenService.addToken(1);
        questionService.finalize(addResponse.getRespondentId());
        Respondent respondent = tokenService.login(1, addResponse.getToken());
        assertFalse(respondent.active);
    }

    @Test
    public void testSaveAnswer() {
        //get the first screen
        NavResponse navResponse = questionService.init(1, "0001-0001-0000-0001-0000-0000-0000");
        assertNotNull(navResponse);

        Answer consentAnswer = navResponse.getAnswers().get(2);
        consentAnswer.setTextValue("true");
        //Save the consent and you will get the respondent question
        navResponse = questionService.saveAnswer(consentAnswer);
        assertEquals(navResponse.getAnswers().size(), 4);

        //Answer the respondent question yes and you will get a name feild
        Answer respondentAnswer = navResponse.getAnswers().get(3);
        respondentAnswer.setTextValue("true");

        navResponse = questionService.saveAnswer(respondentAnswer);
        assertEquals(navResponse.getAnswers().size(), 5);

        //Reset the respondent answer
        respondentAnswer.setTextValue("false");
        navResponse = questionService.saveAnswer(respondentAnswer);
        assertEquals(navResponse.getAnswers().size(), 4);

        //Reset the consent answer
        consentAnswer.setTextValue("false");
        navResponse = questionService.saveAnswer(consentAnswer);

        //We should be back to the original state.
        assertEquals(navResponse.getAnswers().size(), 3);
    }

}
