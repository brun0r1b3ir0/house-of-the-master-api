package com.houseofthemaster.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerDisponivelOuFalhaNoCiTest {

    @Test
    void comDockerOTesteRoda() {
        var condicao = new DockerDisponivelOuFalhaNoCi(() -> null, Map.of("CI", "true")::get);

        assertThat(condicao.avaliar("MigrationsPostgresTest").isDisabled()).isFalse();
    }

    @Test
    void semDockerLocalmenteOTesteEhPuladoComMotivo() {
        var condicao = new DockerDisponivelOuFalhaNoCi(() -> "Ryuk não iniciou", Map.<String, String>of()::get);

        ConditionEvaluationResult resultado = condicao.avaliar("MigrationsPostgresTest");

        assertThat(resultado.isDisabled()).isTrue();
        assertThat(resultado.getReason()).hasValueSatisfying(motivo -> assertThat(motivo)
            .contains("MigrationsPostgresTest PULADO")
            .contains("NÃO foram testadas")
            .contains("Ryuk não iniciou"));
    }

    @Test
    void semDockerNoCiOTesteFalha() {
        var condicao = new DockerDisponivelOuFalhaNoCi(() -> "Docker indisponível", Map.of("CI", "true")::get);

        assertThatThrownBy(() -> condicao.avaliar("MigrationsPostgresTest"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CI=true")
            .hasMessageContaining("Docker indisponível");
    }

    @Test
    void ciComOutroValorNaoContaComoCi() {
        var condicao = new DockerDisponivelOuFalhaNoCi(() -> "Docker indisponível", Map.of("CI", "false")::get);

        assertThat(condicao.avaliar("MigrationsPostgresTest").isDisabled()).isTrue();
    }
}
