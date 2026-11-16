package com.houseofthemaster.api;

import com.houseofthemaster.api.ficha.FichaPersistenciaCasos;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Roda as migrations do Flyway num Postgres 16 vazio e sobe o contexto com ddl-auto=validate:
 * se alguma entidade não bater com o esquema das migrations, o contexto nem sobe.
 * Também roda, no Postgres (jsonb), os mesmos casos de persistência da ficha usados no H2.
 * Sem Docker disponível o teste é pulado com aviso, ou falha se CI=true (ver DockerDisponivelOuFalhaNoCi).
 */
@ExtendWith(DockerDisponivelOuFalhaNoCi.class)
@Testcontainers
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.datasource.driver-class-name=org.postgresql.Driver"
})
class MigrationsPostgresTest extends FichaPersistenciaCasos {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private Flyway flyway;

    @Test
    void migrationsAplicamEEsquemaPassaNoValidate() {
        MigrationInfo[] aplicadas = flyway.info().applied();

        assertThat(aplicadas).isNotEmpty();
        assertThat(Arrays.stream(aplicadas).allMatch(m -> m.getState().isApplied() && !m.getState().isFailed()))
            .isTrue();
        assertThat(Arrays.stream(aplicadas).map(m -> m.getVersion().getVersion()))
            .containsExactly("1", "2", "3", "4");
        assertThat(flyway.info().pending()).isEmpty();
    }
}
