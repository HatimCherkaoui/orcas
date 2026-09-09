package com.github.orcas.orchestrator.autoconfigure.rest;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import com.github.orcas.orchestrator.core.model.WorkflowContextHolder;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import io.netty.channel.ChannelOption;

import java.util.Map;

/**
 * Builds the reactive {@link WebClient}-backed dynamic proxy for a
 * {@link WorkflowRestClient} interface, applying connection pooling, timeouts,
 * default headers/auth from {@link WorkflowRestClientProperties}, and a filter that
 * propagates configured pipeline metadata keys (e.g. correlation/trace headers)
 * downstream on every outbound call.
 *
 * @param <T> the declarative REST client interface type
 */
public final class WorkflowRestClientFactoryBean<T> implements FactoryBean<T>, InitializingBean {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowRestClientFactoryBean.class);

    private final Class<T> type;
    private final String rawBaseUrl;
    private final WorkflowRestClientProperties properties;
    private final Environment environment;
    private final ObjectProvider<WebClientCustomizer> customizers;
    private T proxy;

    public WorkflowRestClientFactoryBean(Class<T> type, String rawBaseUrl, WorkflowRestClientProperties properties, Environment environment, ObjectProvider<WebClientCustomizer> customizers) {
        this.type = type; this.rawBaseUrl = rawBaseUrl; this.properties = properties; this.environment = environment; this.customizers = customizers;
    }
    @Override public void afterPropertiesSet() {
        String baseUrl = environment.resolvePlaceholders(rawBaseUrl);
        log.info("Configuring workflow REST client '{}' for base URL {}", type.getSimpleName(), baseUrl);
        ConnectionProvider pool = ConnectionProvider.builder("workflow-orchestrator-rest")
                .maxConnections(properties.getMaxConnections())
                .pendingAcquireTimeout(properties.getPendingAcquireTimeout())
                .build();
        HttpClient httpClient = HttpClient.create(pool)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(properties.getConnectTimeout().toMillis()))
                .responseTimeout(properties.getResponseTimeout());
        WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultStatusHandler(org.springframework.http.HttpStatusCode::isError, response -> Mono.empty())
                .filter((request, next) -> {
                    var context = WorkflowContextHolder.current();
                    if (context == null) return next.exchange(request);
                    var b = org.springframework.web.reactive.function.client.ClientRequest.from(request);
                    context.metadata().asMap().forEach((key, value) -> {
                        if (!properties.getPropagatedMetadataKeys().stream().anyMatch(k -> k.equalsIgnoreCase(key))) return;
                        if (!request.headers().containsHeader(key)) b.header(key, value);
                    });
                    return next.exchange(b.build());
                });
        properties.getDefaultHeaders().forEach(builder::defaultHeader);
        if (properties.getSecurity().getBearerToken() != null) builder.defaultHeaders(h -> h.setBearerAuth(environment.resolvePlaceholders(properties.getSecurity().getBearerToken())));
        if (properties.getSecurity().getBasicUsername() != null) builder.defaultHeaders(h -> h.setBasicAuth(
                environment.resolvePlaceholders(properties.getSecurity().getBasicUsername()),
                environment.resolvePlaceholders(properties.getSecurity().getBasicPassword() == null ? "" : properties.getSecurity().getBasicPassword())));
        customizers.orderedStream().forEach(c -> c.customize(builder));
        WebClient client = builder.build();
        proxy = HttpServiceProxyFactory.builderFor(WebClientAdapter.create(client)).build().createClient(type);
    }
    @Override public T getObject() { return proxy; }
    @Override public Class<?> getObjectType() { return type; }
}
