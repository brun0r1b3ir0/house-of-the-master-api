package com.houseofthemaster.api.campanha;

import com.houseofthemaster.api.participacao.Papel;

import java.util.UUID;

public record CampanhaResumoResponse(
        UUID id,
        String titulo,
        Papel papel,
        long jogadores,
        Integer maxJogadores,
        String sistema,
        String codigoConvite
) {
}