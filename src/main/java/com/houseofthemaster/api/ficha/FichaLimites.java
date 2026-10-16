package com.houseofthemaster.api.ficha;

/**
 * Limites técnicos da ficha (POST e PATCH). Único lugar a mudar.
 *
 * Não são regras de jogo: só tetos para impedir valores absurdos. As regras (ex.: atual <= máximo)
 * são do frontend. Campos com teto próprio do sistema (sangria 0-10) mantêm o seu.
 */
public final class FichaLimites {

    /** Teto de todo inteiro da ficha: atributos, atuais, contadores, ganho de perícia, condições etc. */
    public static final int VALOR_MAXIMO = 9999;

    /** Sangria tem teto próprio do sistema. */
    public static final int SANGRIA_MAXIMA = 10;

    /** Nome e ocupação da ficha. */
    public static final int TEXTO_MAXIMO = 120;

    /** Chave de condição (condicoes.<chave>). */
    public static final int CHAVE_CONDICAO_MAXIMA = 64;

    /** Caminho de uma op do PATCH (ex.: pericias.Armas de Fogo (Rifles/Espingardas).ganho). */
    public static final int CAMPO_OP_MAXIMO = 200;

    /** Operações por PATCH. */
    public static final int OPS_MAXIMAS = 100;

    private FichaLimites() {
    }

    public static int maximoDoContador(String chave) {
        return "sangria".equals(chave) ? SANGRIA_MAXIMA : VALOR_MAXIMO;
    }
}
