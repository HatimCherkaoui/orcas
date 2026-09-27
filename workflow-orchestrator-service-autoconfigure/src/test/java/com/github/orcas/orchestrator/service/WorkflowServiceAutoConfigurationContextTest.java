package com.github.orcas.orchestrator.service;

import com.github.orcas.orchestrator.service.api.WorkflowAdminService;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService;
import com.github.orcas.orchestrator.service.api.WorkflowReplayPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowServiceAutoConfigurationContextTest {
    @Test
    void registersOperationalControllerWithoutWorkflowRuntime() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(NamedParameterJdbcTemplate.class,
                    (Supplier<NamedParameterJdbcTemplate>) () ->
                            new NamedParameterJdbcTemplate(new DriverManagerDataSource()));
            context.registerBean(ObjectMapper.class, (Supplier<ObjectMapper>) ObjectMapper::new);
            context.registerBean(WorkflowReplayPublisher.class,
                    (Supplier<WorkflowReplayPublisher>) () -> (workflowId, stepName) -> {});
            context.register(WorkflowServiceAutoConfiguration.class);

            context.refresh();

            assertThat(context.getBean(WorkflowQueryService.class)).isNotNull();
            assertThat(context.getBean(WorkflowAdminService.class)).isNotNull();
            assertThat(context.getBean(WorkflowServiceController.class)).isNotNull();
        }
    }
    @Test
    void doesNotCreateControllerWhenRequiredServicesAreMissing() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class, (Supplier<ObjectMapper>) ObjectMapper::new);
            context.register(WorkflowServiceAutoConfiguration.class);
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource(
                            "test", java.util.Map.of("workflow.orchestrator.service.enabled", "false")));

            context.refresh();

            assertThat(context.getBeansOfType(WorkflowServiceController.class)).isEmpty();
        }
    }

}
