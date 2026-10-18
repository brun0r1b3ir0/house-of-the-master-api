package com.houseofthemaster.api.ficha;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Conteúdo da coluna ficha.estado (JSONB), ver MODELO-FICHA.md seção 2.2.
 * Só dados digitados; nada calculado (efetivo, metade, quinto, máximos, Corpo, MOV, Rank).
 *
 * O equals/hashCode é necessário: o Hibernate compara o estado carregado com o atual
 * para decidir se a ficha mudou (dirty checking).
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class FichaEstado {

    // Chaves conhecidas dos mapas do estado (usadas pelos valores iniciais e pela validação do PATCH).
    public static final List<String> ATRIBUTOS = List.of("FOR", "CON", "TAM", "DES", "APA", "INT", "POD", "EDU");
    public static final List<String> ATUAIS = List.of("PV", "PE", "PM", "SAN", "SRT");
    public static final List<String> CONTADORES = List.of("exaustao", "fome", "sede", "debilitantes", "frio", "sangria");
    public static final List<String> FLAGS = List.of("verbena", "colapsoMagico");
    public static final List<String> ABATES = List.of("vampiros", "outros");
    /** Exceções digitadas sobre valores calculados; chave ausente = sem exceção. */
    public static final List<String> OVERRIDES = List.of("PV", "PE", "PM", "MOV", "Corpo", "Conhecimento");
    public static final List<String> REGEN_OVERRIDES = List.of("PV", "PE", "PM");

    /** FOR, CON, TAM, DES, APA, INT, POD, EDU. */
    private Map<String, Integer> atributos;

    /** PV, PE, PM, SAN, SRT. */
    private Map<String, Integer> atuais;

    private EstagioSanidade estagioSanidade;

    /** exaustao, fome, sede, debilitantes, frio, sangria. */
    private Map<String, Integer> contadores;

    private Integer perdaPermanenteTam;

    /** verbena, colapsoMagico. */
    private Map<String, Boolean> flags;

    private Alcateia alcateia;

    /** vampiros, outros. */
    private Map<String, Integer> abates;

    private Map<String, Object> overrides;

    private Map<String, Object> regenOverrides;

    /** Valor de cada condição do catálogo novo, indexado pela chave. */
    private Map<String, Integer> condicoes;

    /**
     * Criação de personagem. PJ nova: { concluida: false }. Fichas antigas e NPCs: null.
     * Só muda pelo endpoint de conclusão (não está na lista do PATCH).
     */
    private Criacao criacao;

    @Getter
    @Setter
    @EqualsAndHashCode
    @ToString
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Criacao {
        private Boolean concluida;
        /** [principal, secundario] usados no orçamento de profissão. */
        private List<String> profissaoAtributos;
        private List<String> periciasProfissao;
        private SnapshotCriacao snapshot;
        private Instant concluidaEm;
    }

    /** O que foi enviado na conclusão da criação (atributos digitados e pontos por bolso). */
    @Getter
    @Setter
    @EqualsAndHashCode
    @ToString
    public static class SnapshotCriacao {
        private Map<String, Integer> atributos;
        private GanhosCriacao ganhos;
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    @ToString
    public static class GanhosCriacao {
        private Map<String, Integer> profissao;
        private Map<String, Integer> interesse;
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    @ToString
    public static class Alcateia {
        private boolean ativa;
        private int membros;
        private String papel;
        private String linhagem;
    }
}
