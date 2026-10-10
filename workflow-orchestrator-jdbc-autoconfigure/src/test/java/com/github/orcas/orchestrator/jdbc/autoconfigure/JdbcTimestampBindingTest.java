package com.github.orcas.orchestrator.jdbc.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcTimestampBindingTest {

    @Test
    void timestampWithTimezoneParametersUseTimezoneAwareJavaValues() {
        var value = OffsetDateTime.now(ZoneOffset.UTC);
        var parameters = new MapSqlParameterSource()
                .addValue("created", value, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("updated", value, Types.TIMESTAMP_WITH_TIMEZONE);

        assertThat(parameters.getValue("created")).isInstanceOf(OffsetDateTime.class);
        assertThat(parameters.getValue("updated")).isInstanceOf(OffsetDateTime.class);
    }
}
