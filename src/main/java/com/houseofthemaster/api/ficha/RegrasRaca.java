package com.houseofthemaster.api.ficha;

import java.util.Arrays;
import java.util.Map;

/** Regras de raça do War If usadas na criação, no POST e no PATCH de ficha. */
public final class RegrasRaca {

    public static final String MENSAGEM_VAMPIRO_PERMANENTE = "A raça Vampiro é permanente e não pode ser trocada.";

    public static final String MENSAGEM_FOR_DES_VAMPIRO = "FOR e DES de vampiro são fixas em 120 e não podem ser alteradas.";

    /** FOR e DES de toda ficha VAMPIRO (gravados no POST, na transformação e na conclusão da criação). */
    public static final int FOR_DES_VAMPIRO = 120;

    /** Atributos fixos do Vampiro: FOR = 120 e DES = 120. */
    public static final Map<String, Integer> ATRIBUTOS_FIXOS_VAMPIRO = Map.of("FOR", FOR_DES_VAMPIRO, "DES", FOR_DES_VAMPIRO);

    /**
     * Valor inicial (base) da Esquivar do Vampiro: 60 (= 120 / 2), CRAVADO. Não é calculado a partir da DES
     * de propósito: no futuro FOR/DES do vampiro sobem com a idade, e a Esquivar não pode subir junto.
     */
    public static final int ESQUIVAR_INICIAL_VAMPIRO = 60;

    private RegrasRaca() {
    }

    /** Vampiro é permanente: não troca para outra raça (null/"" chegam aqui como HUMANO). VAMPIRO -> VAMPIRO não é troca. */
    public static boolean trocaPermitida(Raca atual, Raca nova) {
        return atual != Raca.VAMPIRO || nova == Raca.VAMPIRO;
    }

    /** A troca é a transformação em Vampiro? */
    public static boolean transformaEmVampiro(Raca atual, Raca nova) {
        return atual != Raca.VAMPIRO && nova == Raca.VAMPIRO;
    }

    /**
     * Não existe ficha sem raça: nulo, vazio ou só espaços viram HUMANO. Um nome válido vira a raça;
     * qualquer outro texto lança IllegalArgumentException (400).
     */
    public static Raca normalizar(String raca) {
        if (raca == null || raca.isBlank()) {
            return Raca.HUMANO;
        }
        try {
            return Raca.valueOf(raca);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("raca deve ser um de " + Arrays.toString(Raca.values()) + ".");
        }
    }

    /** Valor fixo do atributo para a raça, ou null se o atributo é livre (FOR/DES do Vampiro são fixos). */
    public static Integer atributoFixo(Raca raca, String atributo) {
        return raca == Raca.VAMPIRO ? ATRIBUTOS_FIXOS_VAMPIRO.get(atributo) : null;
    }
}
