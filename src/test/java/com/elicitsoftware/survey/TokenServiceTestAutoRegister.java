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

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
//@TestProfile(TokenServiceTestAutoRegister.BuildTimeValueChangeTestProfile.class)
class TokenServiceTestAutoRegister {
//
//    private final RandomStringGenerator randomStringGenerator = new RandomStringGenerator(9);
//
//    @BeforeEach
//    public void setUp() {
//        RestAssured.basePath = "/api/token";
//    }
//
//    @Test
//    public void testUserNotFoundAndAutoLoginNotActive() {
//
//        LoginRequest request = new LoginRequest();
//        request.surveyId = 999; // Invalid Survey ID
//        request.token = randomStringGenerator.nextString();
//
//        given()
//                .contentType(MediaType.APPLICATION_JSON)
//                .body(request)
//                .when()
//                .post("/login")
//                .then()
//                .statusCode(204);
//    }
//
//    public static class BuildTimeValueChangeTestProfile implements QuarkusTestProfile {
//
//        @Override
//        public Map<String, String> getConfigOverrides() {
//            return Map.of("token.autoRegister", "false");
//        }
//
//    }
}
