package com.houseofthemaster.api.ficha;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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
