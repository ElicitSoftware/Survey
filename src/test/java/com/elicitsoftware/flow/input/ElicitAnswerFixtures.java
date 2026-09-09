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
import com.elicitsoftware.model.SelectGroup;
import com.elicitsoftware.model.SelectItem;

import java.util.List;

/**
 * In-memory Question/Answer graphs for exercising the Elicit* input field wrappers
 * (UC-002: Answer Survey Questions, BR-004 per-question validation rules) without a
 * database round trip. Every Elicit* wrapper only ever reads the Question/Answer object
 * graph handed to its constructor - it never queries Panache itself - so these plain
 * POJOs are all a wrapper needs to be exercised.
 */
final class ElicitAnswerFixtures {

    private ElicitAnswerFixtures() {
    }

    static Answer answer(String displayKey, String displayText, Question question, String textValue) {
        Answer answer = new Answer();
        answer.displayKey = displayKey;
        answer.displayText = displayText;
        answer.question = question;
        answer.setTextValue(textValue);
        return answer;
    }

    static Question question(boolean required, Integer minValue, Integer maxValue, String variant,
                              String toolTip, String placeholder, String validationText) {
        Question question = new Question();
        question.required = required;
        question.minValue = minValue;
        question.maxValue = maxValue;
        question.variant = variant;
        question.toolTip = toolTip;
        question.placeholder = placeholder;
        question.validationText = validationText;
        return question;
    }

    static Question question(boolean required, Integer minValue, Integer maxValue, String variant,
                              String validationText) {
        return question(required, minValue, maxValue, variant, null, null, validationText);
    }

    static Question selectQuestion(boolean required, String validationText, List<SelectItem> items) {
        Question question = question(required, null, null, null, validationText);
        SelectGroup group = new SelectGroup();
        group.selectItems = items;
        question.selectGroup = group;
        return question;
    }

    static SelectItem item(String codedValue, String displayText) {
        SelectItem item = new SelectItem();
        item.codedValue = codedValue;
        item.displayText = displayText;
        return item;
    }
}
