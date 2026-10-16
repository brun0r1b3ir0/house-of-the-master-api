package com.houseofthemaster.api.sistema;

import com.houseofthemaster.api.ficha.CategoriaPericia;

/** Perícia do catálogo do sistema (CATALOGO-PERICIAS.md). */
public record PericiaCatalogo(
        String nome,
        CategoriaPericia categoria,
        ValorInicial valorInicial,
        /** Alerta Vermelho penaliza as que exigem precisão. */
        boolean exigePrecisao,
        String observacao
) {
}
