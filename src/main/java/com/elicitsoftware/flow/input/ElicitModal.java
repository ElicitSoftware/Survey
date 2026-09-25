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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;

/**
 * Represents an informational modal dialog driven by a MODAL question.
 * <p>
 * Like {@link ElicitHtml} this is a display-only element: it renders
 * {@code answer.displayText} as HTML, collects no value, and never saves an
 * answer. It differs only in presentation — the content is shown in a modal
 * dialog that opens as soon as the section is displayed, rather than inline.
 * <p>
 * The dialog opens on attach rather than in the constructor, because
 * {@code SectionView.buildQuestions()} rebuilds every component on each render
 * but only attaches the ones that are newly displayed. Opening on attach keeps
 * the dialog from reappearing every time an unrelated answer on the page is
 * saved.
 * <p>
 * Note that {@code setId} on a Dialog lands on the hidden host element, not on
 * the rendered overlay, so the close button carries its own id for tests to
 * locate.
 */
public class ElicitModal extends Dialog {

    /**
     * Suffix for the close button's id, appended to the answer's display key.
     */
    public static final String CLOSE_BUTTON_ID_SUFFIX = "-close";

    /**
     * Constructs an ElicitModal for the given answer.
     * <p>
     * The question's short text, when present, becomes the dialog header.
     *
     * @param answer the answer whose display text is shown as HTML in the dialog
     */
    public ElicitModal(Answer answer) {
        super();
        String displayKey = answer.getDisplayKey();
        this.setId(displayKey);

        if (answer.question.shortText != null && !answer.question.shortText.isEmpty()) {
            setHeaderTitle(answer.question.shortText);
        }

        Div content = new Div();
        content.getElement().setAttribute("data-i18n-content", "");
        content.getElement().setProperty("innerHTML", answer.displayText);
        add(content);

        Button close = new Button("Close");
        close.setId(displayKey + CLOSE_BUTTON_ID_SUFFIX);
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        close.addClickListener(e -> close());
        getFooter().add(close);

        setCloseOnEsc(true);
        setCloseOnOutsideClick(true);

        addAttachListener(e -> setOpened(true));
    }
}
