package com.github.orcas.orchestrator.jdbc.autoconfigure;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/** Runs the library schema once when JDBC persistence is enabled. */
public final class WorkflowJdbcSchemaInitializer implements InitializingBean {
    private final DataSource dataSource;
    private final WorkflowJdbcProperties properties;

    public WorkflowJdbcSchemaInitializer(DataSource dataSource, WorkflowJdbcProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.isSchemaInitialization()) return;
        var populator = new ResourceDatabasePopulator(
                new DefaultResourceLoader().getResource(properties.getSchemaLocation()));
        populator.execute(dataSource);
    }
}
