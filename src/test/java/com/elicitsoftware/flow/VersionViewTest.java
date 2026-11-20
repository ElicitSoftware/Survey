package com.elicitsoftware.flow;

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

import com.elicitsoftware.diagnostics.ContainerInfo;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Paragraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The version page shows the container's time from the JVM's clock and the image creation date
 * from {@link ContainerInfo}, which finds it once; building the page looks nothing up itself.
 *
 * <p>Traceability: UC-007 Notes (the version page).</p>
 */
class VersionViewTest extends PlainViewTestSupport {

    /** Answers a fixed date and counts how often it is asked. */
    private static final class StubContainerInfo extends ContainerInfo {
        final String date;
        int asked;

        StubContainerInfo(String date) {
            this.date = date;
        }

        @Override
        public synchronized String imageCreationDate() {
            asked++;
            return date;
        }
    }

    private static VersionView build(ContainerInfo info) {
        VersionView view = new VersionView();
        view.containerInfo = info;
        view.init();
        UI.getCurrent().add(view);
        return view;
    }

    private static List<String> paragraphs(VersionView view) {
        return view.getChildren()
                .flatMap(c -> c.getChildren())
                .filter(Paragraph.class::isInstance)
                .map(c -> ((Paragraph) c).getText())
                .toList();
    }

    @Test
    void thePageShowsTheContainerTimeAndTheImageCreationDate() {
        StubContainerInfo info = new StubContainerInfo("2025-07-24T15:54:44Z");

        VersionView view = build(info);

        List<String> texts = paragraphs(view);
        assertEquals(t("versionView.containerTime"), texts.get(0));
        assertFalse(texts.get(1).isBlank(), "the container's current time");
        assertEquals(t("versionView.imageCreated"), texts.get(2));
        assertEquals(new VersionView().formatImageCreationDate("2025-07-24T15:54:44Z"), texts.get(3));
        assertEquals(1, info.asked);
    }

    @Test
    void withoutAnImageCreationDateThePageSaysItIsUnavailable() {
        VersionView view = build(new StubContainerInfo(null));

        List<String> texts = paragraphs(view);
        assertTrue(texts.contains(t("versionView.imageCreatedUnavailable")), String.valueOf(texts));
        assertFalse(texts.contains(t("versionView.imageCreated")));
    }

    @Test
    void aDateThePageCannotFormatIsShownAsFound() {
        VersionView view = build(new StubContainerInfo("BUILD_DATE=sometime in 2025-07"));

        assertTrue(paragraphs(view).stream().anyMatch(p -> p.contains("2025-07")), String.valueOf(paragraphs(view)));
    }
}
