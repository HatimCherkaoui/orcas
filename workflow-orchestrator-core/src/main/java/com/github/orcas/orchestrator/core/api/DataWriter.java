package com.github.orcas.orchestrator.core.api;

@FunctionalInterface
public interface DataWriter<I> {
    void write(I value) throws Exception;
}
