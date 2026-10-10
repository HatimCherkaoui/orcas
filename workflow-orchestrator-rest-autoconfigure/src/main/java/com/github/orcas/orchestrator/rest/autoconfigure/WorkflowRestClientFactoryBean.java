package com.github.orcas.orchestrator.rest.autoconfigure;

import com.github.orcas.orchestrator.core.model.WorkflowContextHolder;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;


/**
 * Creates the dynamic proxy for a declarative workflow REST client.
 *
 * <p>The factory owns only transport concerns: connection pooling, HTTP
 * timeouts, authentication, default headers and workflow metadata propagation.
 * Application code continues to depend on the framework-neutral
 * {@code WorkflowRestClient} contract.</p>
 *
 * @param <T> declarative REST client interface type
 */
public final class WorkflowRestClientFactoryBean<T>
        implements FactoryBean<T>, InitializingBean, DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowRestClientFactoryBean.class);
    private static final String CONNECTION_POOL_NAME = "workflow-orchestrator-rest";

    private final Class<T> type;
    private final String rawBaseUrl;
    private final WorkflowRestClientProperties properties;
    private final Environment environment;
    private final ObjectProvider<WebClientCustomizer> customizers;

    private com.github.orcas.orchestrator.core.engine.WorkflowTelemetry telemetry = com.github.orcas.orchestrator.core.engine.WorkflowTelemetry.noop();
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTelemetry(com.github.orcas.orchestrator.core.engine.WorkflowTelemetry telemetry) { this.telemetry = telemetry; }
    private T proxy;
    private ConnectionProvider connectionProvider;

    public WorkflowRestClientFactoryBean(
            Class<T> type,
            String rawBaseUrl,
            WorkflowRestClientProperties properties,
            Environment environment,
            ObjectProvider<WebClientCustomizer> customizers) {
        this.type = type;
        this.rawBaseUrl = rawBaseUrl;
        this.properties = properties;
        this.environment = environment;
        this.customizers = customizers;
    }

    @Override
    public void afterPropertiesSet() {
        String baseUrl = environment.resolvePlaceholders(rawBaseUrl);
        LOG.info("Configuring workflow REST client '{}' for base URL {}", type.getSimpleName(), baseUrl);

        connectionProvider = createConnectionProvider();
        HttpClient httpClient = createHttpClient(connectionProvider);
        WebClient client = createWebClient(baseUrl, httpClient);
        proxy = HttpServiceProxyFactory.builderFor(WebClientAdapter.create(client))
                .build()
                .createClient(type);
    }

    private ConnectionProvider createConnectionProvider() {
        return ConnectionProvider.builder(CONNECTION_POOL_NAME)
                .maxConnections(properties.getMaxConnections())
                .pendingAcquireTimeout(properties.getPendingAcquireTimeout())
                .build();
    }

    private HttpClient createHttpClient(ConnectionProvider pool) {
        return HttpClient.create(pool)
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(properties.getConnectTimeout().toMillis()))
                .responseTimeout(properties.getResponseTimeout());
    }

    private WebClient createWebClient(String baseUrl, HttpClient httpClient) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultStatusHandler(
                        org.springframework.http.HttpStatusCode::isError,
                        response -> Mono.empty())
                .filter(this::propagateWorkflowMetadata);

        applyDefaultHeaders(builder);
        applyAuthentication(builder);
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));

        return builder.build();
    }

    private Mono<org.springframework.web.reactive.function.client.ClientResponse> propagateWorkflowMetadata(
            ClientRequest request,
            org.springframework.web.reactive.function.client.ExchangeFunction next) {
        var context = WorkflowContextHolder.current();
        var metadata = context == null ? com.github.orcas.orchestrator.core.model.CorrelationIdentifiers.fromHeaders(request.headers().toSingleValueMap()) : context.metadata();
        var execution = WorkflowContextHolder.execution();
        var attributes = new java.util.LinkedHashMap<String,String>();
        attributes.put("http.request.method", request.method().name());
        attributes.put("server.address", request.url().getHost());
        if (execution != null && execution.workflowId() != null) {
            attributes.put("workflowId", execution.workflowId()); attributes.put("workflow", execution.workflow());
            if (execution.step() != null) attributes.put("workflowStep", execution.step().stepName());
        }
        var operation = telemetry.begin("RestCall", "http.client " + request.method().name(), metadata, attributes);
        var builder = ClientRequest.from(request);
        var headers = operation.propagation();
        if (headers.isEmpty()) headers = com.github.orcas.orchestrator.core.model.CorrelationIdentifiers.headers(metadata);
        headers.forEach((key,value) -> builder.headers(h -> h.set(key,value)));
        try {
            return next.exchange(builder.build()).doOnNext(response -> operation.attribute("http.response.status_code",Integer.toString(response.statusCode().value())))
                    .doOnError(operation::error).doFinally(signal -> operation.close());
        } catch (RuntimeException error) { operation.error(error); operation.close(); throw error; }
        finally { operation.detach(); }
    }

    private void applyDefaultHeaders(WebClient.Builder builder) {
        properties.getDefaultHeaders().forEach(builder::defaultHeader);
    }

    private void applyAuthentication(WebClient.Builder builder) {
        var security = properties.getSecurity();
        applyBearerAuthentication(builder, security.getBearerToken());
        applyBasicAuthentication(builder, security.getBasicUsername(), security.getBasicPassword());
    }

    private void applyBearerAuthentication(WebClient.Builder builder, String bearerToken) {
        if (bearerToken != null) {
            builder.defaultHeaders(headers ->
                    headers.setBearerAuth(environment.resolvePlaceholders(bearerToken)));
        }
    }

    private void applyBasicAuthentication(
            WebClient.Builder builder,
            String username,
            String password) {
        if (username == null) {
            return;
        }

        String resolvedUsername = environment.resolvePlaceholders(username);
        String resolvedPassword = environment.resolvePlaceholders(password == null ? "" : password);
        builder.defaultHeaders(headers -> headers.setBasicAuth(resolvedUsername, resolvedPassword));
    }

    @Override
    public T getObject() {
        return proxy;
    }

    @Override
    public Class<?> getObjectType() {
        return type;
    }

    @Override
    public void destroy() {
        if (connectionProvider != null) {
            connectionProvider.dispose();
            connectionProvider = null;
        }
    }
}
