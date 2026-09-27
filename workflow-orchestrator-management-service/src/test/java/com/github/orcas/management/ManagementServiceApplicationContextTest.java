package com.github.orcas.management;

import com.github.orcas.orchestrator.service.WorkflowServiceController;
import com.github.orcas.orchestrator.service.api.WorkflowAdminService;
import com.github.orcas.orchestrator.service.api.WorkflowQueryService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = ManagementServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:orchestrator;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "workflow.orchestrator.jdbc.schema-initialization=true",
                "workflow.orchestrator.kafka.enabled=false",
                "spring.kafka.bootstrap-servers=localhost:9092"
        }
)
class ManagementServiceApplicationContextTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WorkflowServiceController controller;

    @Autowired
    private WorkflowQueryService queryService;

    @Autowired
    private WorkflowAdminService adminService;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private javax.sql.DataSource dataSource;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void registersJdbcInfrastructureAndWorkflowManagementController() {
        assertThat(jdbc).isNotNull();
        assertThat(dataSource).isNotNull();
        assertThat(jdbc.getJdbcTemplate().queryForObject("select count(*) from workflow", Integer.class)).isZero();
        assertThat(controller).isNotNull();
        assertThat(queryService).isNotNull();
        assertThat(adminService).isNotNull();
        assertThat(applicationContext.getBeansOfType(WorkflowServiceController.class)).hasSize(1);
    }

    @Test
    void exposesWorkflowManagementEndpoint() {
        RestAssured
                .given()
                .port(port)
                .queryParam("page", 0)
                .queryParam("size", 8)
                .when()
                .get("/api/orchestrator/workflows")
                .then()
                .statusCode(200);
    }

    @Test
    void exposesWorkflowManagementMappings() {
        RestAssured
                .given()
                .port(port)
                .when()
                .get("/actuator/mappings")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.containsString("/api/orchestrator/workflows"));
    }
}
