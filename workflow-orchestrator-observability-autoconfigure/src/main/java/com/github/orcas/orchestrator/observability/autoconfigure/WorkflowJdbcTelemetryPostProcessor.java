package com.github.orcas.orchestrator.observability.autoconfigure;

import com.github.orcas.orchestrator.core.engine.WorkflowTelemetry;
import com.github.orcas.orchestrator.core.model.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.Map;

/** Instruments pooled JDBC without opening additional connections or capturing SQL/parameters. */
public final class WorkflowJdbcTelemetryPostProcessor implements BeanPostProcessor {
    private final ObjectProvider<WorkflowTelemetry> telemetry;
    public WorkflowJdbcTelemetryPostProcessor(ObjectProvider<WorkflowTelemetry> telemetry) { this.telemetry = telemetry; }
    @Override public Object postProcessAfterInitialization(Object bean,String name) {
        if (!(bean instanceof DataSource)) return bean;
        var factory = new org.springframework.aop.framework.ProxyFactory(bean);
        factory.setProxyTargetClass(!java.lang.reflect.Modifier.isFinal(bean.getClass().getModifiers()));
        factory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
            Object value = invocation.getMethod().getName().equals("getConnection")
                    ? observe("getConnection",invocation::proceed) : invocation.proceed();
            return value instanceof Connection connection ? connection(connection) : value;
        });
        return factory.getProxy();
    }
    private Connection connection(Connection connection) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args) -> {
            if (method.getName().equals("commit") || method.getName().equals("rollback")) return observe(method.getName(),() -> invoke(connection,method,args));
            Object value = invoke(connection,method,args);
            if (value instanceof CallableStatement statement) return statement(statement,CallableStatement.class);
            if (value instanceof PreparedStatement statement) return statement(statement,PreparedStatement.class);
            if (value instanceof Statement statement) return statement(statement,Statement.class);
            return value;
        });
    }
    private Object statement(Statement statement,Class<?> type) {
        return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args) -> {
            if (!method.getName().startsWith("execute")) return invoke(statement,method,args);
            return observe(method.getName(),() -> invoke(statement,method,args));
        });
    }
    @FunctionalInterface private interface Invocation { Object invoke() throws Throwable; }
    private Object observe(String name,Invocation invocation) throws Throwable {
        var current = WorkflowContextHolder.current();
        var metadata = current == null ? CorrelationIdentifiers.fromHeaders(Map.of()) : current.metadata();
        var execution = WorkflowContextHolder.execution();
        var attributes = new java.util.LinkedHashMap<String,String>();
        attributes.put("db.operation.name",name);
        if (execution != null && execution.workflowId() != null) {
            attributes.put("workflowId",execution.workflowId()); attributes.put("workflow",execution.workflow());
            if (execution.step() != null) attributes.put("workflowStep",execution.step().stepName());
        }
        try (var operation = telemetry.getObject().begin("Database","jdbc."+name,metadata,attributes)) {
            try { return invocation.invoke(); }
            catch (Throwable error) { operation.error(error); throw error; }
        }
    }
    private static Object invoke(Object target,Method method,Object[] args) throws Throwable {
        try { return method.invoke(target,args); }
        catch (InvocationTargetException error) { throw error.getCause(); }
    }
}
