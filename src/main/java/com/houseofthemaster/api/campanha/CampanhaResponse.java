package com.houseofthemaster.api.campanha;

import com.houseofthemaster.api.participacao.Papel;

import java.util.UUID;

public record CampanhaResponse(
        UUID id,
        String nome,
        String sistema,
        String descricao,
        Integer maxJogadores,
        String codigoConvite,
        Papel papel
) {
}