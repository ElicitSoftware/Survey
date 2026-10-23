package com.elicitsoftware.flow.input;

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

import com.elicitsoftware.i18n.ContentTexts;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions — {@link ElicitModal} shows a MODAL question as a dialog:
 * its header is the question's short text in the respondent's content language (UC-009
 * BR-005), its body is the answer's rendered label marked as authored content, and a primary
 * Close button whose id derives from the display key closes it. It opens itself on attach.
 */
class ElicitModalTest {

    private static Answer modalAnswer(String shortText) {
        Question question = InputFieldSupport.plain();
        question.shortText = shortText;
        Answer answer = ElicitAnswerFixtures.answer("2.1.1.1.1.1.1", "<b>Read me</b>", question, null);
        answer.displayTextLocal = "<b>اقرأني</b>";
        return answer;
    }

    private static Button closeButton(ElicitModal modal) {
        List<Component> footer = modal.getFooter().getElement().getChildren()
                .map(e -> e.getComponent().orElseThrow()).toList();
        return (Button) footer.getFirst();
    }

    @Test
    void given_shortText_when_constructed_then_headerIdAndBodyAreSet() {
        ElicitModal modal = new ElicitModal(modalAnswer("Notice"), ContentTexts.base());

        assertEquals("2.1.1.1.1.1.1", modal.getId().orElse(null));
        assertEquals("Notice", modal.getHeaderTitle());
        Div body = (Div) modal.getChildren().findFirst().orElseThrow();
        assertEquals("<b>اقرأني</b>", body.getElement().getProperty("innerHTML"));
        assertTrue(body.getElement().hasAttribute("data-i18n-content"));
        assertTrue(modal.isCloseOnEsc());
        assertTrue(modal.isCloseOnOutsideClick());
    }

    @Test
    void given_translatedTexts_when_constructed_then_headerIsTranslated() {
        ElicitModal modal = new ElicitModal(modalAnswer("Notice"), InputFieldSupport.translated());

        assertEquals("T:Notice", modal.getHeaderTitle());
    }

    @Test
    void given_noOrEmptyShortText_when_constructed_then_noHeader() {
        assertTrue(new ElicitModal(modalAnswer(null), ContentTexts.base()).getHeaderTitle().isEmpty());
        assertTrue(new ElicitModal(modalAnswer(""), ContentTexts.base()).getHeaderTitle().isEmpty());
    }

    @Test
    void given_modal_when_constructed_then_footerHasPrimaryCloseButton() {
        ElicitModal modal = new ElicitModal(modalAnswer("Notice"), ContentTexts.base());

        Button close = closeButton(modal);

        assertEquals("2.1.1.1.1.1.1" + ElicitModal.CLOSE_BUTTON_ID_SUFFIX, close.getId().orElse(null));
        assertEquals("2.1.1.1.1.1.1-close", close.getId().orElse(null));
        assertTrue(close.hasThemeName(ButtonVariant.LUMO_PRIMARY.getVariantName()));
        assertFalse(close.getText().isEmpty());
    }

    @Test
    void given_attachedModal_when_closeClicked_then_itOpensOnAttachAndCloses() {
        ElicitModal modal = new ElicitModal(modalAnswer("Notice"), ContentTexts.base());
        assertFalse(modal.isOpened());

        UI ui = new UI();
        UI.setCurrent(ui);
        try {
            ui.add(modal);
            assertTrue(modal.isOpened(), "the modal opens itself when attached");

            closeButton(modal).click();
            assertFalse(modal.isOpened());
        } finally {
            UI.setCurrent(null);
        }
    }
}
