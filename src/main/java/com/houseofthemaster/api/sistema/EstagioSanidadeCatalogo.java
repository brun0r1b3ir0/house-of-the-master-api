package com.houseofthemaster.api.sistema;

import com.houseofthemaster.api.ficha.EstagioSanidade;

/** Estágio de Sanidade do catálogo; ordem 1 = melhor (Plenitude), 8 = pior (Inferno). */
public record EstagioSanidadeCatalogo(
        EstagioSanidade chave,
        String nome,
        int ordem
) {
}
