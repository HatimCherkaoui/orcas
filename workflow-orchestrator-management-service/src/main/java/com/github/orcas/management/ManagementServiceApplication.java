package com.github.orcas.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone management plane for the workflow orchestrator.
 *
 * <p>The management application deliberately relies on Spring Boot's normal
 * auto-configuration discovery. The management module depends on the JDBC,
 * service, Kafka and dashboard starters, so their {@code AutoConfiguration.imports}
 * entries are discovered automatically and their {@code @AutoConfiguration(after = ...)}
 * ordering is applied by Boot.</p>
 *
 * <p>This is important here: manually listing the JDBC/service auto-configurations
 * with {@code @ImportAutoConfiguration} makes the management application's
 * configuration graph depend on the import declaration rather than on the normal
 * Boot auto-configuration graph. The service controller is then vulnerable to
 * bean-condition evaluation before the JDBC/service bean definitions are visible.
 * The standard Boot graph avoids that ordering problem.</p>
 *
 * <p>No application workflow package is component-scanned by this application.
 * Workflow definitions therefore remain in the runtime/example application,
 * while this service owns the persisted operational API.</p>
 */
@SpringBootApplication
public class ManagementServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ManagementServiceApplication.class, args);
    }
}
