package com.houseofthemaster.api.ficha;

/** Regras de perícias específicas do War If usadas no PATCH e na criação de personagem. */
public final class RegrasPericia {

    /** Exclusiva do Lobisomem: só ele pode receber pontos nela. */
    public static final String OLFATO = "Olfato";

    /** Sem categoria; nunca recebe pontos na criação. */
    public static final String PRIMORDIOS = "Primórdios";

    public static final String MENSAGEM_OLFATO = "Olfato é exclusiva do Lobisomem: a raça da ficha precisa ser LOBISOMEM.";

    private RegrasPericia() {
    }

    /** A raça pode receber pontos nesta perícia? (Olfato só para LOBISOMEM.) */
    public static boolean permitidaParaRaca(String pericia, Raca raca) {
        return !OLFATO.equals(pericia) || raca == Raca.LOBISOMEM;
    }
}
