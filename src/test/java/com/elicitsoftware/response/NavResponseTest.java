package com.elicitsoftware.response;

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

import com.elicitsoftware.model.Answer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-002: Answer Survey Questions - NavResponse is the DTO returned by section navigation;
 * getDisplayKeys()/getAnswerByKey() are plain in-memory lookups over the answers list handed
 * to the constructor.
 */
class NavResponseTest {

    private static Answer answerWithDisplayKey(String key) {
        Answer answer = new Answer();
        answer.displayKey = key;
        return answer;
    }

    @Test
    void getters_returnConstructorArguments() {
        NavigationItem current = new NavigationItem("Section 1", false, "/section1", "next", "prev");
        NavigationItem[] items = new NavigationItem[]{current};
        List<Answer> answers = List.of(answerWithDisplayKey("1-1-0-1-0-1-0"));

        NavResponse response = new NavResponse(null, current, answers, items);

        assertNull(response.getStep());
        assertSame(current, response.getCurrentNavItem());
        assertSame(answers, response.getAnswers());
    }

    @Test
    void getDisplayKeys_returnsEveryAnswersDisplayKeyInOrder() {
        List<Answer> answers = List.of(
                answerWithDisplayKey("1-1-0-1-0-1-0"),
                answerWithDisplayKey("1-1-0-1-0-2-0"));
        NavResponse response = new NavResponse(null, null, answers, new NavigationItem[0]);

        assertEquals(List.of("1-1-0-1-0-1-0", "1-1-0-1-0-2-0"), response.getDisplayKeys());
    }

    @Test
    void getDisplayKeys_emptyAnswers_returnsEmptyList() {
        NavResponse response = new NavResponse(null, null, List.of(), new NavigationItem[0]);
        assertTrue(response.getDisplayKeys().isEmpty());
    }

    @Test
    void getAnswerByKey_matchFound_returnsThatAnswer() {
        Answer target = answerWithDisplayKey("1-1-0-1-0-2-0");
        List<Answer> answers = List.of(answerWithDisplayKey("1-1-0-1-0-1-0"), target);
        NavResponse response = new NavResponse(null, null, answers, new NavigationItem[0]);

        assertSame(target, response.getAnswerByKey("1-1-0-1-0-2-0"));
    }

    @Test
    void getAnswerByKey_noMatch_returnsNull() {
        List<Answer> answers = List.of(answerWithDisplayKey("1-1-0-1-0-1-0"));
        NavResponse response = new NavResponse(null, null, answers, new NavigationItem[0]);

        assertNull(response.getAnswerByKey("no-such-key"));
    }
}
