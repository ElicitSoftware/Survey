package com.elicitsoftware.diagnostics;

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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The image creation date the version page shows is found once per application start, and a
 * command that does not finish is abandoned at its bound.
 *
 * <p>Traceability: UC-007 Notes (the version page).</p>
 */
class ContainerInfoTest {

    /** Answers a creation date to the first docker inspect and records every command. */
    private static final class RecordingRunner implements ContainerInfo.CommandRunner {
        final List<String> commands = new ArrayList<>();
        String hostname = "0123456789ab";
        String created = "2025-07-24T15:54:44Z";

        @Override
        public String firstLine(String... command) {
            String line = String.join(" ", command);
            commands.add(line);
            if (line.equals("hostname")) {
                return hostname;
            }
            return line.contains("--format '{{.Created}}'") ? created : null;
        }
    }

    @Test
    void theDateIsFoundOnceAndRememberedForEveryLaterCall() {
        RecordingRunner runner = new RecordingRunner();
        ContainerInfo info = new ContainerInfo(runner);

        assertEquals("2025-07-24T15:54:44Z", info.imageCreationDate());
        int commandsForTheFirstCall = runner.commands.size();
        assertTrue(commandsForTheFirstCall > 0);

        for (int i = 0; i < 20; i++) {
            assertEquals("2025-07-24T15:54:44Z", info.imageCreationDate());
        }

        assertEquals(commandsForTheFirstCall, runner.commands.size(), "no command runs after the first call");
    }

    @Test
    void anUnknownDateIsRememberedTooAndNotLookedForAgain() {
        RecordingRunner runner = new RecordingRunner();
        runner.hostname = "my-laptop";
        runner.created = null;
        ContainerInfo info = new ContainerInfo(runner);

        assertNull(info.imageCreationDate());
        int commandsForTheFirstCall = runner.commands.size();
        assertNull(info.imageCreationDate());

        assertEquals(commandsForTheFirstCall, runner.commands.size());
        assertTrue(runner.commands.stream().noneMatch(c -> c.contains("docker inspect")),
                "with no container id, docker is not asked about a container");
    }

    @Test
    void outputThatIsNotADateIsPassedOver() {
        ContainerInfo info = new ContainerInfo(command -> {
            String line = String.join(" ", command);
            if (line.equals("hostname")) {
                return "0123456789ab";
            }
            if (line.contains("--format '{{.Created}}'")) {
                return "no such object";
            }
            return line.contains("stat -c %y /.dockerenv") ? "2025-07-24 15:54:44.626850013 +0000" : null;
        });

        assertEquals("2025-07-24 15:54:44.626850013 +0000", info.imageCreationDate());
        assertTrue(ContainerInfo.looksLikeADate("2025/07/24"));
        assertFalse(ContainerInfo.looksLikeADate("no such object"));
    }

    @Test
    void aCommandAnswersItsFirstLine() {
        assertEquals("first", ContainerInfo.run(Duration.ofSeconds(5), "sh", "-c", "echo ' first '; echo second"));
        assertNull(ContainerInfo.run(Duration.ofSeconds(5), "sh", "-c", "true"), "no output");
        assertNull(ContainerInfo.run(Duration.ofSeconds(5), "no-such-program-" + System.nanoTime()), "cannot start");
    }

    @Test
    void aCommandThatDoesNotFinishIsAbandonedAtTheBound() {
        long start = System.nanoTime();

        String line = ContainerInfo.run(Duration.ofMillis(300), "sh", "-c", "sleep 30; echo late");

        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertNull(line);
        assertTrue(elapsedMillis < 5_000, "gave up after " + elapsedMillis + " ms");
    }

    @Test
    void theBoundIsTwoSeconds() {
        assertEquals(Duration.ofSeconds(2), ContainerInfo.COMMAND_TIMEOUT);
    }
}
