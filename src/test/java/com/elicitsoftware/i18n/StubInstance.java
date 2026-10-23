package com.elicitsoftware.i18n;

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

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;

/**
 * A hand-written {@link Instance} for plain tests: satisfied by one bean, or unsatisfied.
 * Supports what {@link LocaleSelection} uses ({@code isUnsatisfied}, {@code get}) and nothing else.
 *
 * @param <T> the bean type
 */
final class StubInstance<T> implements Instance<T> {

    private final T bean;

    private StubInstance(T bean) {
        this.bean = bean;
    }

    static <T> StubInstance<T> of(T bean) {
        return new StubInstance<>(bean);
    }

    static <T> StubInstance<T> unsatisfied() {
        return new StubInstance<>(null);
    }

    @Override
    public T get() {
        if (bean == null) {
            throw new IllegalStateException("unsatisfied");
        }
        return bean;
    }

    @Override
    public boolean isUnsatisfied() {
        return bean == null;
    }

    @Override
    public boolean isAmbiguous() {
        return false;
    }

    @Override
    public Iterator<T> iterator() {
        return bean == null ? List.<T>of().iterator() : List.of(bean).iterator();
    }

    @Override
    public Instance<T> select(Annotation... qualifiers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void destroy(T instance) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Handle<T> getHandle() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<? extends Handle<T>> handles() {
        throw new UnsupportedOperationException();
    }
}
