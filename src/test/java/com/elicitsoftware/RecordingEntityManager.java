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

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Test support for plain unit tests: an {@link EntityManager} that answers native queries from a
 * function of their SQL and records every query it was asked to run, with its parameters and
 * whether it was executed. Built on a dynamic proxy, so only the calls the code under test makes
 * are implemented; anything else fails loudly rather than returning a silent default.
 */
public final class RecordingEntityManager {

    /** One native query as the code under test built it. */
    public static final class Recorded {
        public final String sql;
        public final Map<String, Object> parameters = new LinkedHashMap<>();
        public int executeUpdates;
        public int resultListCalls;

        Recorded(String sql) {
            this.sql = sql;
        }
    }

    private final List<Recorded> queries = new ArrayList<>();
    private final Function<String, List<?>> results;
    private final EntityManager proxy;

    /**
     * @param results the result list each native query returns, chosen from its SQL
     */
    public RecordingEntityManager(Function<String, List<?>> results) {
        this.results = results;
        this.proxy = (EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
                new Class<?>[]{EntityManager.class}, (p, method, args) -> {
                    switch (method.getName()) {
                        case "createNativeQuery":
                            Recorded recorded = new Recorded((String) args[0]);
                            queries.add(recorded);
                            return query(recorded);
                        case "isJoinedToTransaction":
                            return false;
                        case "toString":
                            return "RecordingEntityManager";
                        case "hashCode":
                            return System.identityHashCode(p);
                        case "equals":
                            return p == args[0];
                        default:
                            throw new UnsupportedOperationException("EntityManager." + method.getName());
                    }
                });
    }

    private Query query(Recorded recorded) {
        return (Query) Proxy.newProxyInstance(Query.class.getClassLoader(), new Class<?>[]{Query.class},
                (p, method, args) -> {
                    switch (method.getName()) {
                        case "setParameter":
                            recorded.parameters.put((String) args[0], args[1]);
                            return p;
                        case "executeUpdate":
                            recorded.executeUpdates++;
                            return 1;
                        case "getResultList":
                            recorded.resultListCalls++;
                            return new ArrayList<>(results.apply(recorded.sql));
                        default:
                            throw new UnsupportedOperationException("Query." + method.getName());
                    }
                });
    }

    /** @return the proxy to hand to the code under test */
    public EntityManager entityManager() {
        return proxy;
    }

    /** @return every native query created, in order */
    public List<Recorded> queries() {
        return queries;
    }
}
