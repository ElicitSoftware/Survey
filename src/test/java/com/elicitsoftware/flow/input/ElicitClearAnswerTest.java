package com.elicitsoftware.flow.input;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.PostgresTestResource;
import com.elicitsoftware.i18n.ContentTexts;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static com.elicitsoftware.flow.input.InputFieldSupport.errors;
import static com.elicitsoftware.flow.input.InputFieldSupport.requiredFallback;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002 BR-017: a respondent can clear a date, date-time, time, integer or number field. The
 * cleared value is {@code null}, which SectionView saves as unanswered; a required question then
 * shows its required message, so navigation stays blocked (BR-004, A1).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ElicitClearAnswerTest {

    private static Answer requiredAnswer() {
        Question question = ElicitAnswerFixtures.question(true, null, null, null, null);
        return InputFieldSupport.answer(question, null);
    }

    @Test
    void clearedRequiredDateShowsTheRequiredMessage() {
        ElicitDatePicker wrapper = new ElicitDatePicker(requiredAnswer(), ContentTexts.base());
        wrapper.component.setValue(LocalDate.of(2026, 11, 20));
        wrapper.component.clear();
        assertNull(wrapper.component.getValue());
        assertTrue(errors(wrapper.getBinder()).contains(requiredFallback()));
    }

    @Test
    void clearedRequiredDateTimeShowsTheRequiredMessage() {
        ElicitDateTimePicker wrapper = new ElicitDateTimePicker(requiredAnswer(), ContentTexts.base());
        wrapper.component.setValue(LocalDateTime.of(2026, 11, 20, 8, 30));
        wrapper.component.clear();
        assertNull(wrapper.component.getValue());
        assertTrue(errors(wrapper.getBinder()).contains(requiredFallback()));
    }

    @Test
    void clearedRequiredTimeShowsTheRequiredMessage() {
        ElicitTimePicker wrapper = new ElicitTimePicker(requiredAnswer(), ContentTexts.base());
        wrapper.component.setValue(LocalTime.of(8, 30));
        wrapper.component.clear();
        assertNull(wrapper.component.getValue());
        assertTrue(errors(wrapper.getBinder()).contains(requiredFallback()));
    }

    @Test
    void clearedRequiredIntegerShowsTheRequiredMessage() {
        ElicitIntegerField wrapper = new ElicitIntegerField(requiredAnswer(), ContentTexts.base());
        wrapper.component.setValue(3);
        wrapper.component.clear();
        assertNull(wrapper.component.getValue());
        assertTrue(errors(wrapper.getBinder()).contains(requiredFallback()));
    }

    @Test
    void clearedRequiredNumberShowsTheRequiredMessage() {
        ElicitDoubleField wrapper = new ElicitDoubleField(requiredAnswer(), ContentTexts.base());
        wrapper.component.setValue(2.5d);
        wrapper.component.clear();
        assertNull(wrapper.component.getValue());
        assertTrue(errors(wrapper.getBinder()).contains(requiredFallback()));
    }
}
