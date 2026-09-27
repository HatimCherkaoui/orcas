package com.github.orcas.orchestrator.core.builder;

import com.github.orcas.orchestrator.core.api.Step;
import com.github.orcas.orchestrator.core.api.StepResult;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepCatalogTest {
    @Test
    void resolvesByAnnotationNameAndType() {
        var validate = new ValidateStep();
        var catalog = new StepCatalog(List.of(validate));

        assertThat(catalog.get("validate")).isSameAs(validate);
        assertThat(catalog.get(ValidateStep.class)).isSameAs(validate);
        assertThat(catalog.names()).containsExactly("validate");
    }

    @Test
    void rejectsDuplicateNames() {
        assertThatThrownBy(() -> new StepCatalog(List.of(new ValidateStep(), new ValidateStep())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate step: validate");
    }

    @WorkflowStep("validate")
    static final class ValidateStep extends Step {
        @Override
        public StepResult execute(WorkflowContext context) {
            return StepResult.success(context);
        }
    }
}
