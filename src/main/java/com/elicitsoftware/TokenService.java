package com.elicitsoftware;

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

import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.response.AddResponse;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Service class for managing and handling survey tokens and their associated
 * respondents. This class provides functionalities such as token generation,
 * respondent registration, survey retrieval, user login, and deactivation.
 * <p>
 * The class uses a secure random token generator to create unique tokens for
 * surveys and manages these tokens associated with survey respondents. It
 * also provides transactional capabilities for database operations.
 */
@RequestScoped
public class TokenService {

    private static final String TOKEN_ERROR = "Error Generating Token";

    private final RandomStringGenerator randomStringGenerator;

    @ConfigProperty(name = "token.autoRegister", defaultValue = "false")
    boolean autoRegister;

    /**
     * Constructs a new instance of the TokenService class.
     * <p>
     * This constructor initializes the TokenService with a specialized RandomStringGenerator
     * that generates random strings of length 9, designed to use a combination of digits,
     * uppercase consonant characters, and a subset of lowercase letters. The random string
     * generator is seeded with a cryptographically secure random number generator.
     * <p>
     * The character set used for random string generation excludes ambiguous characters
     * to ensure clarity and usability in generated tokens.
     */
    public TokenService() {
        super();
        String easy = RandomStringGenerator.digits + "BCDFGHJKLMNPQRSTVWXZbcdfghjkmnpqrstvwxz2456789";
        randomStringGenerator = new RandomStringGenerator(9, new SecureRandom(), easy);
    }

    /**
     * Retrieves a map containing survey data.
     *
     * @return A HashMap where the key is a String ("Surveys") and the value is a List of Survey objects
     * representing all surveys retrieved from the database.
     */
    public HashMap<String, List<Survey>> getSurveys() {
        HashMap<String, List<Survey>> hm = new HashMap<>();
        hm.put("Surveys", Survey.findAll().list());
        return hm;
    }

    /**
     * Retrieves a survey from the database based on its unique identifier.
     *
     * @param id The unique identifier of the survey to be retrieved.
     * @return The {@code Survey} object corresponding to the provided identifier,
     * or {@code null} if no survey is found with the given ID.
     */
    public Survey getSurvey(@QueryParam(value = "id") int id) {
        return Survey.findById(id);
    }

    /**
     * Adds a unique token for a respondent in the context of the specified survey.
     * <p>
     * This method delegates the token generation and respondent association to the {@code addToken}
     * method. It ensures that the token is successfully created for the given survey and returns
     * the resulting response.
     *
     * @param surveyId The unique identifier of the survey for which the token is to be added.
     * @return An {@code AddResponse} object containing either the generated token and respondent ID
     * or an error message, depending on the operation's success.
     */
    @Transactional
    public AddResponse putToken(int surveyId) {
        return addToken(surveyId);
    }

    /**
     * Adds a unique token for a respondent in the context of the specified survey.
     * <p>
     * The method generates a unique token for the given survey and associates it with a newly created
     * respondent. If the survey does not exist or if token generation fails, the response contains
     * an error indicating the issue. Otherwise, the response includes the generated token and the
     * respondent's ID.
     *
     * @param surveyId The unique identifier of the survey for which the token is to be added.
     * @return An {@code AddResponse} object containing either the generated token and respondent ID
     * or an error message, depending on the operation's success.
     */
    public AddResponse addToken(int surveyId) {
        AddResponse ar = new AddResponse();
        // Tokens have to be unique within a survey
        String token = generateUniqueToken(4, 0, surveyId);
        Survey survey = Survey.findById(surveyId);

        if (survey == null || Objects.equals(token, TOKEN_ERROR)) {
            ar.setError(TOKEN_ERROR);
            return ar;
        }

        Respondent respondent = new Respondent();
        respondent.token = token;
        respondent.survey = survey;
        respondent.persist();
        ar.setToken(respondent.token);
        ar.setRespondentId(respondent.id);
        return ar;
    }

    /**
     * Generates a unique token for a given survey by attempting to create a random string
     * and checking if it already exists within the context of the specified survey.
     * If a duplicate token is found, the method recursively tries again until a unique
     * token is generated or the maximum number of attempts is reached.
     *
     * @param maxTries   The maximum number of attempts allowed to generate a unique token.
     * @param currentTry The current attempt number in the token generation process.
     * @param surveyId   The unique identifier of the survey for which the token is being generated.
     * @return The generated unique token as a String, or a predefined error token if the maximum
     * number of attempts is exceeded.
     */
    private String generateUniqueToken(int maxTries, int currentTry, int surveyId) {
        if (currentTry >= maxTries) {
            return TOKEN_ERROR;
        }
        //Generate a random string for the token.
        String token = randomStringGenerator.nextString();
        Respondent respondent = Respondent.findBySurveyAndToken(surveyId, token);
        if (respondent == null) {
            return token;
        } else {
            return generateUniqueToken(maxTries, currentTry + 1, surveyId);
        }
    }

    /**
     * Logs in a respondent for a given survey using a provided unique token.
     * If no respondent exists, a new respondent may be auto-registered based
     * on the system's configuration.
     * <p>
     * When a valid respondent is found:
     * - Increments the number of logins for the respondent.
     * - Sets the first access date if it's not already set.
     * - Persists these changes to the database.
     * <p>
     * If no existing respondent is found for the given token:
     * - Auto-registers a new respondent if the system allows it, assigns
     * the survey ID, token, and active status, then persists the respondent.
     *
     * @param surveyId The unique identifier of the survey for which the respondent
     *                 is logging in.
     * @param token    The unique token associated with the respondent attempting to log in.
     * @return The {@code Respondent} object representing the logged-in user.
     * Returns a new or updated respondent object, or {@code null} if
     * the respondent cannot be determined and auto-registration is disabled.
     */
    @Transactional
    public Respondent login(int surveyId, String token) {
        Log.info(String.format("autoRegister = ", autoRegister));
        Log.info(String.format("Login attempt: %s", token));

        Respondent user = getUser(surveyId, token);

        Survey survey = Survey.findById(surveyId);
        //Check for a valid survey id
        if (user == null) {
            if (autoRegister) {
                user = new Respondent();
                user.active = true;
                user.survey = survey;
                user.token = token;
                user.persistAndFlush();
            }
        } else {
            if (user.active) {
                user.logins = user.logins + 1;
                if (user.firstAccessDt == null) {
                    user.firstAccessDt = OffsetDateTime.now();
                }
                user.persistAndFlush();
            }
        }
        return user;
    }

    public boolean isAutoRegister(){
        return autoRegister;
    }

    /**
     * Returns a respondent based on the specified survey ID and token following these rules:
     * 1) If a survey ID is provided (not zero), it retrieves the respondent associated
     * with the given survey and token, regardless of the survey's active status.
     *
     * @param surveyId The unique identifier of the survey. A value of zero indicates no specific survey ID is provided.
     * @param token    The unique token associated with the respondent.
     * @return A {@code Respondent} object corresponding to the given survey ID and token,
     * or {@code null} if no respondent is found matching the specified criteria.
     */
    // This method will return user based on this logic:
    // 1) return user from survey not based on active status if they
    //    specifically request a survey and token.
    // 2) search database by token for active survey if
    //    found return the lowest survey number.
    //    e.g. fhhs survey or consent survey if fhhs is not active
    // 3) if all surveys are complete return the lowest survey
    //    e.g. fhhs if both are complete.
    private Respondent getUser(int surveyId, String token) {
        Respondent user = null;
        if (surveyId != 0) {
            user = Respondent.findBySurveyAndToken(surveyId, token);
        }
        return user;
    }

    /**
     * Deactivates a respondent by setting their active status to false.
     * If the respondent has not been finalized yet, sets the finalized date to the current time.
     * Updates and persists the changes to the database.
     *
     * @param respondentId The unique identifier of the respondent to be deactivated.
     * @return The {@code Respondent} object after updating its status and finalized date,
     * or the unchanged object if the respondent was not active.
     */
    @Transactional
    public Respondent deactivate(int respondentId) {
        Respondent user = Respondent.findById(respondentId);
        if (user.active) {
            user.active = false;
            if (user.finalizedDt == null) {
                user.finalizedDt = OffsetDateTime.now();
            }
            user.persistAndFlush();
        }
        return user;
    }
}
