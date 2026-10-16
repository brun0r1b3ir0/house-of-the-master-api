package com.houseofthemaster.api.config;

import org.hibernate.cfg.MappingSettings;
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Define como o Hibernate converte as colunas JSON (ficha.estado, ficha_evento.anterior/novo).
 *
 * Sem isto o Hibernate escolhe sozinho o Jackson 2, que só está no classpath por causa do
 * jjwt-jackson e não sabe serializar java.time. O mapper é próprio (padrões do Jackson 3,
 * datas em ISO-8601) para que mudanças no JSON da API não alterem o que é gravado no banco.
 */
@Configuration
public class JpaJsonConfig {

    @Bean
    public HibernatePropertiesCustomizer jsonFormatMapperCustomizer() {
        JsonMapper mapper = JsonMapper.builder().build();
        return properties -> properties.put(MappingSettings.JSON_FORMAT_MAPPER, new Jackson3JsonFormatMapper(mapper));
    }
}
