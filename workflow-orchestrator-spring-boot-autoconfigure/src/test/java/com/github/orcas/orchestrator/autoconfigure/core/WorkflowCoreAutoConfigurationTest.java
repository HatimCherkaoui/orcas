package com.github.orcas.orchestrator.autoconfigure.core;

import com.github.orcas.orchestrator.core.builder.StepCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowCoreAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    WorkflowCorePropertiesAutoConfiguration.class,
                    WorkflowCoreAutoConfiguration.class);

    @Test
    void registersStepCatalogBean() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(StepCatalog.class));
    }
}
