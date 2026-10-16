package com.houseofthemaster.api;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Condição para testes com Testcontainers.
 *
 * - Docker funcionando: o teste roda.
 * - Docker indisponível (ou o Testcontainers não conseguiu iniciar, ex.: Ryuk travado):
 *     - localmente o teste é PULADO, com um aviso bem visível na saída;
 *     - com a variável de ambiente CI=true o teste FALHA, para o CI nunca passar sem testar as migrations.
 */
public class DockerDisponivelOuFalhaNoCi implements ExecutionCondition {

    private final Supplier<String> motivoDeDockerIndisponivel;
    private final Function<String, String> variavelDeAmbiente;
    private final Consumer<String> avisar;

    public DockerDisponivelOuFalhaNoCi() {
        this(DockerDisponivelOuFalhaNoCi::verificarDocker, System::getenv, DockerDisponivelOuFalhaNoCi::avisarNaSaida);
    }

    DockerDisponivelOuFalhaNoCi(Supplier<String> motivoDeDockerIndisponivel,
                                Function<String, String> variavelDeAmbiente,
                                Consumer<String> avisar) {
        this.motivoDeDockerIndisponivel = motivoDeDockerIndisponivel;
        this.variavelDeAmbiente = variavelDeAmbiente;
        this.avisar = avisar;
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return avaliar(context.getRequiredTestClass().getSimpleName());
    }

    ConditionEvaluationResult avaliar(String teste) {
        String motivo = motivoDeDockerIndisponivel.get();
        if (motivo == null) {
            return ConditionEvaluationResult.enabled("Docker disponível");
        }

        if ("true".equalsIgnoreCase(variavelDeAmbiente.apply("CI"))) {
            throw new IllegalStateException(
                "CI=true e o Docker não está disponível: " + teste + " não pode ser pulado no CI. Motivo: " + motivo);
        }

        String aviso = teste + " PULADO: as migrations NÃO foram testadas contra o Postgres. Motivo: " + motivo;
        avisar.accept(aviso);
        return ConditionEvaluationResult.disabled(aviso);
    }

    private static void avisarNaSaida(String aviso) {
        System.err.println();
        System.err.println("=".repeat(100));
        System.err.println("ATENÇÃO: " + aviso);
        System.err.println("=".repeat(100));
        System.err.println();
    }

    /** null se o Docker estiver utilizável; senão, a causa encontrada pelo Testcontainers. */
    private static String verificarDocker() {
        try {
            DockerClientFactory.instance().client();
            return null;
        } catch (Throwable e) {
            Throwable raiz = e;
            while (raiz.getCause() != null && raiz.getCause() != raiz) {
                raiz = raiz.getCause();
            }
            return e.getClass().getSimpleName() + ": " + e.getMessage()
                + (raiz != e ? " (causa: " + raiz.getClass().getSimpleName() + ": " + raiz.getMessage() + ")" : "");
        }
    }
}
