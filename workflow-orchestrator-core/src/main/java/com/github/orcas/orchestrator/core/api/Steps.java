package com.github.orcas.orchestrator.core.api;

import java.util.function.Function;

/** Small factory DSL for infrastructure steps. */
public final class Steps {
    private Steps() {}

    public static <I, O> RestCallStep<I, O> rest(
            String name, ContextMapper<I> mapper, Function<I, ?> caller, ResultMapper<O> resultMapper) {
        return new RestCallStep<>(name, mapper, caller, resultMapper);
    }

    public static <I> DatabaseLoader<I> load(
            String name, ContextMapper<I> mapper, DataWriter<I> writer) {
        return new DatabaseLoader<>(name, mapper, writer);
    }
}
