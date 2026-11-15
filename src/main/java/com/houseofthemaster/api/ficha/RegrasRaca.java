package com.houseofthemaster.api.ficha;

import java.util.Map;

/** Regras de raça do War If usadas no PATCH de ficha. */
public final class RegrasRaca {

    public static final String MENSAGEM_VAMPIRO_PERMANENTE = "A raça Vampiro é permanente e não pode ser trocada.";

    /** Atributos gravados na transformação em Vampiro (troca de outra raça, ou de sem raça, para VAMPIRO). */
    public static final Map<String, Integer> ATRIBUTOS_DA_TRANSFORMACAO_VAMPIRO = Map.of("FOR", 120, "DES", 120);

    private RegrasRaca() {
    }

    /** Vampiro é permanente: não troca para outra raça nem para null. VAMPIRO -> VAMPIRO não é troca. */
    public static boolean trocaPermitida(Raca atual, Raca nova) {
        return atual != Raca.VAMPIRO || nova == Raca.VAMPIRO;
    }

    /** A troca é a transformação em Vampiro? */
    public static boolean transformaEmVampiro(Raca atual, Raca nova) {
        return atual != Raca.VAMPIRO && nova == Raca.VAMPIRO;
    }
}
