package com.houseofthemaster.api.ficha;

import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Corpo de POST /campanhas/{cid}/fichas/{fid}/criacao/concluir.
 * Os números ficam como JsonNode para a validação recusar "50", 50.5 e null (sem conversão silenciosa).
 */
@Getter
@Setter
public class ConcluirCriacaoRequest {

    /** FOR, CON, TAM, DES, APA, INT, POD, EDU (valores digitados). */
    private Map<String, JsonNode> atributos;

    private Profissao profissao;

    private Ganhos ganhos;

    @Getter
    @Setter
    public static class Profissao {
        private String principal;
        private String secundario;
        private List<String> pericias;
    }

    /** Pontos distribuídos, por bolso: nome da perícia -> pontos. */
    @Getter
    @Setter
    public static class Ganhos {
        private Map<String, JsonNode> profissao;
        private Map<String, JsonNode> interesse;
    }
}
