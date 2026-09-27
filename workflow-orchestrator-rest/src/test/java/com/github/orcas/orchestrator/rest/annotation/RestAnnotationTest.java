package com.github.orcas.orchestrator.rest.annotation;

import com.github.orcas.orchestrator.core.annotation.Workflow;
import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.api.ResponseConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

class RestAnnotationTest {
    @Test
    void usesFrameworkNeutralDefaults() throws NoSuchMethodException {
        var method = TestClient.class.getMethods()[0];
        var rest = method.getAnnotation(WorkflowRestCall.class);
        var launch = TestController.class
                .getDeclaredMethod("reserve")
                .getAnnotation(LaunchWorkflow.class);

        assertThat(rest.mapper()).isEqualTo(ContextMapper.Identity.class);
        assertThat(rest.responseSubscriber()).isEqualTo(ResponseConsumer.Void.class);
        assertThat(launch.workflow()).isEqualTo(TestWorkflow.class);
    }

    interface TestClient {
        @com.github.orcas.orchestrator.core.annotation.WorkflowStep
        @WorkflowRestCall
        String reserve();
    }


    @RestController
    static final class TestController {
        @LaunchWorkflow(workflow = TestWorkflow.class)
        public String reserve() {
            return "ok";
        }
    }

    @Workflow("test")
    static final class TestWorkflow {
    }
}
