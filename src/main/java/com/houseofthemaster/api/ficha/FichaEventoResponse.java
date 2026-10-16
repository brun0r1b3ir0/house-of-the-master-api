package com.houseofthemaster.api.ficha;

import java.time.Instant;
import java.util.UUID;

/** Linha do log de alterações da ficha. */
public record FichaEventoResponse(
        UUID id,
        String campo,
        Object anterior,
        Object novo,
        UUID autorId,
        String autorNome,
        Instant em
) {
}
