package com.houseofthemaster.api.campanha;

import com.houseofthemaster.api.participacao.Papel;

import java.util.UUID;

public record CampanhaDetalheResponse(
        UUID id,
        String titulo,
        String descricao,
        String sistema,
        Papel papel,
        long jogadores,
        Integer maxJogadores,
        String codigoConvite
) {
}