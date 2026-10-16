package com.houseofthemaster.api.ficha;

import java.time.Instant;
import java.util.UUID;

/** Item da listagem de fichas da campanha. */
public record FichaResumoResponse(
        UUID id,
        TipoFicha tipo,
        String nome,
        String ocupacao,
        Raca raca,
        UUID donoId,
        String donoNome,
        Instant atualizadaEm
) {
}
