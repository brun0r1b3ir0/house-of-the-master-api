package com.houseofthemaster.api.ficha;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ficha completa: dados + estado + perícias. Nenhum campo calculado. */
public record FichaResponse(
        UUID id,
        UUID campanhaId,
        TipoFicha tipo,
        String nome,
        String ocupacao,
        Raca raca,
        UUID donoId,
        String donoNome,
        Instant criadaEm,
        Instant atualizadaEm,
        FichaEstado estado,
        List<FichaPericiaResponse> pericias
) {
}
