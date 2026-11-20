package com.elicitsoftware.etl;

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
import java.util.function.Predicate;

/**
 * A hand-written stand-in for the {@code owner} persistence unit of {@link ETLService}, so the
 * reporting ETL's sequencing and SQL assembly (UC-008, UC-010, UC-011) can be tested without a
 * database. It supports only {@code createNativeQuery(String)}: every query it hands out records
 * its SQL, its parameters and how it was executed, and answers from rules matched on the SQL in
 * the order they were added. A query no rule matches answers an empty list, a count of zero or
 * zero rows updated.
 */
final class ScriptedEntityManager {

    /** One executed statement: its SQL, how it ran ({@code list}, {@code single}, {@code update}) and its parameters. */
    record Executed(String sql, String how, Map<String, Object> params) {
    }

    private record Rule(Predicate<String> matches, String how, Object answer) {
    }

    private final List<Rule> rules = new ArrayList<>();
    final List<Executed> executed = new ArrayList<>();

    /** Answers {@code getResultList()} on SQL equal to {@code sql} with {@code rows}. */
    ScriptedEntityManager list(String sql, List<?> rows) {
        return on(sql::equals, "list", rows);
    }

    /** Answers {@code getSingleResult()} on SQL equal to {@code sql}. */
    ScriptedEntityManager single(String sql, Object value) {
        return on(sql::equals, "single", value);
    }

    /** Answers {@code executeUpdate()} on SQL equal to {@code sql}; a {@link RuntimeException} answer is thrown. */
    ScriptedEntityManager update(String sql, Object rowsOrFailure) {
        return on(sql::equals, "update", rowsOrFailure);
    }

    /** The general rule: an answer for one way of executing, on any SQL the predicate accepts. */
    ScriptedEntityManager on(Predicate<String> matches, String how, Object answer) {
        rules.add(new Rule(matches, how, answer));
        return this;
    }

    EntityManager entityManager() {
        return (EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
                new Class<?>[]{EntityManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("createNativeQuery") && args.length == 1) {
                        return query((String) args[0]);
                    }
                    throw new UnsupportedOperationException("EntityManager." + method.getName());
                });
    }

    /** The SQL of every statement executed, in order. */
    List<String> sqls() {
        return executed.stream().map(Executed::sql).toList();
    }

    /** The executed statements whose SQL equals {@code sql}. */
    List<Executed> runs(String sql) {
        return executed.stream().filter(e -> e.sql().equals(sql)).toList();
    }

    private Query query(String sql) {
        Map<String, Object> params = new LinkedHashMap<>();
        return (Query) Proxy.newProxyInstance(Query.class.getClassLoader(), new Class<?>[]{Query.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setParameter" -> {
                        params.put(String.valueOf(args[0]), args[1]);
                        yield proxy;
                    }
                    case "getResultList" -> answer(sql, "list", params, List.of());
                    case "getSingleResult" -> answer(sql, "single", params, 0L);
                    case "executeUpdate" -> answer(sql, "update", params, 0);
                    default -> throw new UnsupportedOperationException("Query." + method.getName());
                });
    }

    private Object answer(String sql, String how, Map<String, Object> params, Object fallback) {
        executed.add(new Executed(sql, how, new LinkedHashMap<>(params)));
        for (Rule rule : rules) {
            if (rule.how().equals(how) && rule.matches().test(sql)) {
                if (rule.answer() instanceof RuntimeException failure) {
                    throw failure;
                }
                return rule.answer();
            }
        }
        return fallback;
    }
}
