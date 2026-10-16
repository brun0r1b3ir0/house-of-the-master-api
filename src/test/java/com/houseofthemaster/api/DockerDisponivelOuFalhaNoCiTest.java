package com.houseofthemaster.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Os avisos vão para uma lista, não para a saída: assim não parecem um pulo real no log dos testes. */
class DockerDisponivelOuFalhaNoCiTest {

    private final List<String> avisos = new ArrayList<>();

    @Test
    void comDockerOTesteRodaSemAviso() {
        var condicao = new DockerDisponivelOuFalhaNoCi(() -> null, Map.of("CI", "true")::get, avisos::add);

        assertThat(condicao.avaliar("MigrationsPostgresTest").isDisabled()).isFalse();
        assertThat(avisos).isEmpty();
    }

    @Test
    void semDockerLocalmenteOTesteEhPuladoComAvisoEMotivo() {
        var condicao = new DockerDisponivelOuFalhaNoCi(() -> "Ryuk não iniciou", Map.<String, String>of()::get, avisos::add);

        ConditionEvaluationResult resultado = condicao.avaliar("MigrationsPostgresTest");

        assertThat(resultado.isDisabled()).isTrue();
        assertThat(resultado.getReason()).hasValueSatisfying(motivo -> assertThat(motivo)
            .contains("MigrationsPostgresTest PULADO")
            .contains("NÃO foram testadas")
            .contains("Ryuk não iniciou"));
        assertThat(avisos).containsExactly(resultado.getReason().orElseThrow());
    }

    @Test
    void semDockerNoCiOTesteFalha() {
        var condicao = new DockerDisponivelOuFalhaNoCi(() -> "Docker indisponível", Map.of("CI", "true")::get, avisos::add);

        assertThatThrownBy(() -> condicao.avaliar("MigrationsPostgresTest"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CI=true")
            .hasMessageContaining("Docker indisponível");
    }

    @Test
    void ciComOutroValorNaoContaComoCi() {
        var condicao = new DockerDisponivelOuFalhaNoCi(() -> "Docker indisponível", Map.of("CI", "false")::get, avisos::add);

        assertThat(condicao.avaliar("MigrationsPostgresTest").isDisabled()).isTrue();
    }
}
