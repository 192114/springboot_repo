package com.shadow.backend.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdScalarSerializer;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(long.class, ToStringSerializer.instance);
        module.addSerializer(LocalDateTime.class, new StdScalarSerializer<LocalDateTime>(LocalDateTime.class) {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(value.format(DATE_TIME_FORMATTER));
            }
        });

        return builder -> builder
                .changeDefaultPropertyInclusion(inclusion ->
                        inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .addModule(module);
    }
}
