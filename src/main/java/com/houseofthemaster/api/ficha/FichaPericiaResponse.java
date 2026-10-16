package com.houseofthemaster.api.ficha;

/** Perícia da ficha. Sem valorInicial nem valor: o frontend calcula a partir do catálogo. */
public record FichaPericiaResponse(
        String nome,
        CategoriaPericia categoria,
        int ganho,
        int ganhoTreino,
        OrigemPericia origem
) {
}
