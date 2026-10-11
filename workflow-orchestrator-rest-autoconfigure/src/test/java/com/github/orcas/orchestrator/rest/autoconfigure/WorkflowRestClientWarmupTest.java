package com.github.orcas.orchestrator.rest.autoconfigure;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.service.annotation.GetExchange;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class WorkflowRestClientWarmupTest {
    interface Api {
        @GetExchange("/business") String business();
    }

    @Test void warmsOnlyTheExplicitHealthEndpointBeforeCreatingTheProxy() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            requests.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] body = "{\"status\":\"UP\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var properties = new WorkflowRestClientProperties();
        properties.setWarmupPath("/health");
        properties.setWarmupTimeout(Duration.ofSeconds(30));
        var beans = new DefaultListableBeanFactory();
        var factory = new WorkflowRestClientFactoryBean<>(Api.class,
                "http://127.0.0.1:" + server.getAddress().getPort(), properties,
                new StandardEnvironment(), beans.getBeanProvider(WebClientCustomizer.class));
        try {
            factory.afterPropertiesSet();
            assertThat(factory.getObject()).isNotNull();
            assertThat(requests.get()).isEqualTo(1);
        } finally {
            factory.destroy();
            server.stop(0);
        }
    }

    @Test void warmupPathCannotChangeTheTargetHost() {
        var properties = new WorkflowRestClientProperties();
        assertThat(properties.getWarmupPath()).isNull();
        assertThatThrownBy(() -> properties.setWarmupPath("https://other.example/health"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setWarmupPath("//other.example/health"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void failedHealthEndpointFailsInitialization() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        var properties = new WorkflowRestClientProperties();
        properties.setWarmupPath("/health");
        var factory = new WorkflowRestClientFactoryBean<>(Api.class,
                "http://127.0.0.1:" + server.getAddress().getPort(), properties,
                new StandardEnvironment(), new DefaultListableBeanFactory().getBeanProvider(WebClientCustomizer.class));
        try {
            assertThatThrownBy(factory::afterPropertiesSet)
                    .isInstanceOf(org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable.class);
        } finally {
            factory.destroy();
            server.stop(0);
        }
    }
}
