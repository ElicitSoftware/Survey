package com.elicitsoftware.flow.input;

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
import com.elicitsoftware.model.Question;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datepicker.DatePickerVariant;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePickerVariant;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.component.timepicker.TimePickerVariant;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.answer;
import static com.elicitsoftware.flow.input.ElicitAnswerFixtures.question;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002 (Answer Survey Questions), BR-004: coverage of the date/time Elicit* wrappers -
 * ElcitDatePicker, ElicitDateTimePicker, ElicitTimePicker. minValue/maxValue mean different
 * things for each: epoch days for the date picker, HHMM-encoded time-of-day for the other
 * two (see each class's own setBindings javadoc).
 */
@QuarkusTest
class ElicitDateTimeFieldsTest {

    @Test
    void datePicker_construction_parsesInitialValueFromAnswer() {
        Question question = question(false, null, null, null, null);
        Answer answer = answer("1.1.1.1.4.1.1", "Birth date", question, "2020-01-15");

        DatePicker picker = new ElcitDatePicker(answer).component;

        assertEquals(LocalDate.of(2020, 1, 15), picker.getValue());
    }

    @Test
    void datePicker_required_blocksEmptyValue_allowsFilledValue() {
        Question question = question(true, null, null, null, "A date is required");
        Answer answer = answer("1.1.1.1.4.1.2", "Birth date", question, null);

        ElcitDatePicker wrapper = new ElcitDatePicker(answer);
        DatePicker picker = wrapper.component;

        assertTrue(picker.isRequiredIndicatorVisible());
        assertFalse(wrapper.getBinder().validate().isOk());

        picker.setValue(LocalDate.of(2020, 1, 1));
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void datePicker_minMax_areInterpretedAsEpochDays() {
        Question question = question(false, 0, 100, null, null);
        Answer answer = answer("1.1.1.1.4.1.3", "Birth date", question, null);

        DatePicker picker = new ElcitDatePicker(answer).component;

        assertEquals(LocalDate.ofEpochDay(0), picker.getMin());
        assertEquals(LocalDate.ofEpochDay(100), picker.getMax());
    }

    @Test
    void datePicker_variants_appliedFromQuestion() {
        String variants = String.join(",", DatePickerVariant.LUMO_ALIGN_RIGHT.getVariantName(),
                DatePickerVariant.LUMO_SMALL.getVariantName());
        Question question = question(false, null, null, variants, null);
        Answer answer = answer("1.1.1.1.4.1.4", "Birth date", question, null);

        DatePicker picker = new ElcitDatePicker(answer).component;

        assertTrue(picker.getThemeNames().contains(DatePickerVariant.LUMO_ALIGN_RIGHT.getVariantName()));
        assertTrue(picker.getThemeNames().contains(DatePickerVariant.LUMO_SMALL.getVariantName()));
    }

    @Test
    void dateTimePicker_construction_parsesInitialValueFromAnswer() {
        Question question = question(false, null, null, null, null);
        Answer answer = answer("1.1.1.1.4.2.1", "Appointment", question, "2020-01-15T09:30");

        DateTimePicker picker = new ElicitDateTimePicker(answer).component;

        assertEquals(LocalDateTime.of(2020, 1, 15, 9, 30), picker.getValue());
    }

    @Test
    void dateTimePicker_required_blocksEmptyValue_allowsFilledValue() {
        Question question = question(true, null, null, null, "An appointment time is required");
        Answer answer = answer("1.1.1.1.4.2.2", "Appointment", question, null);

        ElicitDateTimePicker wrapper = new ElicitDateTimePicker(answer);
        DateTimePicker picker = wrapper.component;

        assertTrue(picker.isRequiredIndicatorVisible());
        assertFalse(wrapper.getBinder().validate().isOk());

        picker.setValue(LocalDateTime.of(2020, 1, 1, 9, 0));
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void dateTimePicker_minMax_areInterpretedAsHHMMOnTodaysDate() {
        // minValue=830 / maxValue=1730 mean 8:30 AM / 5:30 PM on whatever day the component
        // is constructed (see ElicitDateTimePicker.createLocalDateTimeFromHHMM).
        Question question = question(false, 830, 1730, null, null);
        Answer answer = answer("1.1.1.1.4.2.3", "Appointment", question, null);

        DateTimePicker picker = new ElicitDateTimePicker(answer).component;

        LocalDate today = LocalDate.now();
        assertEquals(LocalDateTime.of(today, LocalTime.of(8, 30)), picker.getMin());
        assertEquals(LocalDateTime.of(today, LocalTime.of(17, 30)), picker.getMax());
    }

    @Test
    void dateTimePicker_variants_appliedFromQuestion() {
        String variants = DateTimePickerVariant.LUMO_SMALL.getVariantName();
        Question question = question(false, null, null, variants, null);
        Answer answer = answer("1.1.1.1.4.2.4", "Appointment", question, null);

        DateTimePicker picker = new ElicitDateTimePicker(answer).component;

        assertTrue(picker.getThemeNames().contains(DateTimePickerVariant.LUMO_SMALL.getVariantName()));
    }

    @Test
    void timePicker_construction_neverAppliesInitialValue_becauseSetValueIsUnimplemented() {
        // ElicitTimePicker.setValue(Answer) is a stubbed-out TODO - it does nothing - so a
        // previously-saved answer never reaches the component. Documented here as current
        // behavior; fixing it wasn't in scope for this test pass.
        Question question = question(false, null, null, null, null);
        Answer answer = answer("1.1.1.1.4.3.0", "Preferred time", question, "09:00");

        TimePicker picker = new ElicitTimePicker(answer).component;

        assertNull(picker.getValue());
    }

    @Test
    void timePicker_required_blocksEmptyValue_allowsFilledValue() {
        Question question = question(true, null, null, null, "A time is required");
        Answer answer = answer("1.1.1.1.4.3.1", "Preferred time", question, null);

        ElicitTimePicker wrapper = new ElicitTimePicker(answer);
        TimePicker picker = wrapper.component;

        assertTrue(picker.isRequiredIndicatorVisible());
        assertFalse(wrapper.getBinder().validate().isOk());

        picker.setValue(LocalTime.of(9, 0));
        assertTrue(wrapper.getBinder().validate().isOk());
    }

    @Test
    void timePicker_minMax_areInterpretedAsNanoOfDay() {
        // ElicitTimePicker.setBindings feeds Question.minValue/maxValue straight into
        // LocalTime.ofNanoOfDay(int). Question.minValue/maxValue are plain Integer columns,
        // and a full day is ~86.4 trillion nanoseconds - far past Integer range - so only
        // tiny fractions of a second near midnight are actually reachable through this path.
        // Documented here as current behavior with the values the code can actually accept.
        Question question = question(false, 0, Integer.MAX_VALUE, null, null);
        Answer answer = answer("1.1.1.1.4.3.2", "Preferred time", question, null);

        TimePicker picker = new ElicitTimePicker(answer).component;

        assertEquals(LocalTime.ofNanoOfDay(0), picker.getMin());
        assertEquals(LocalTime.ofNanoOfDay(Integer.MAX_VALUE), picker.getMax());
    }

    @Test
    void timePicker_variants_appliedFromQuestion() {
        String variants = TimePickerVariant.LUMO_ALIGN_LEFT.getVariantName();
        Question question = question(false, null, null, variants, null);
        Answer answer = answer("1.1.1.1.4.3.3", "Preferred time", question, null);

        TimePicker picker = new ElicitTimePicker(answer).component;

        assertTrue(picker.getThemeNames().contains(TimePickerVariant.LUMO_ALIGN_LEFT.getVariantName()));
    }
}
