package com.elicitsoftware;

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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of {@link AccessCodeService} that need no database (UC-001): the access codes it
 * generates are nine characters drawn from an alphabet without look-alike characters, which is
 * inside BR-001's 5 to 12; generation gives up with the error marker once it is out of tries;
 * no respondent is looked up without a survey id; and the auto-registration switch (BR-002) is
 * reported as configured. Everything else in the service reads or writes respondents through
 * Panache and is covered by {@code AccessCodeServiceTest} under Quarkus.
 */
class AccessCodeServiceGeneratorTest {

    private static final String ALPHABET = "2456789BCDFGHJKLMNPQRSTVWXZbcdfghjkmnpqrstvwxz";

    private static String nextCode(AccessCodeService service) throws Exception {
        Field field = AccessCodeService.class.getDeclaredField("randomStringGenerator");
        field.setAccessible(true);
        return ((RandomStringGenerator) field.get(service)).nextString();
    }

    private static Object invoke(AccessCodeService service, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = AccessCodeService.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(service, args);
    }

    @Test
    void given_aNewService_when_codesAreGenerated_then_nineUnambiguousCharacters() throws Exception {
        AccessCodeService service = new AccessCodeService();
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String code = nextCode(service);
            assertEquals(9, code.length(), code);
            for (char c : code.toCharArray()) {
                assertTrue(ALPHABET.indexOf(c) >= 0, "unexpected character " + c + " in " + code);
            }
            codes.add(code);
        }
        assertTrue(codes.size() > 190, "codes are random: " + codes.size() + " distinct of 200");
    }

    @Test
    void given_noTriesLeft_when_generateUniqueAccessCode_then_theErrorMarkerWithoutALookup() throws Exception {
        AccessCodeService service = new AccessCodeService();

        Class<?>[] types = {int.class, int.class, int.class};
        assertEquals("Error Generating Access Code", invoke(service, "generateUniqueAccessCode", types, 4, 4, 1));
        assertEquals("Error Generating Access Code", invoke(service, "generateUniqueAccessCode", types, 4, 5, 1));
        assertEquals("Error Generating Access Code", invoke(service, "generateUniqueAccessCode", types, 0, 0, 1));
    }

    @Test
    void given_noSurveyId_when_getUser_then_noRespondentAndNoLookup() throws Exception {
        assertNull(invoke(new AccessCodeService(), "getUser", new Class<?>[]{int.class, String.class}, 0, "ABCDE1234"));
    }

    @Test
    void given_theConfiguredSwitch_when_isAutoRegister_then_itIsReported() {
        AccessCodeService service = new AccessCodeService();
        assertFalse(service.isAutoRegister());

        service.autoRegister = true;
        assertTrue(service.isAutoRegister());
    }
}
