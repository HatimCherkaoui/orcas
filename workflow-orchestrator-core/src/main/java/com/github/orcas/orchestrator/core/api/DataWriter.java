package com.github.orcas.orchestrator.core.api;

/** Writes one value to an application-managed destination. */
@FunctionalInterface
public interface DataWriter<I> {
    void write(I value) throws Exception;
}
