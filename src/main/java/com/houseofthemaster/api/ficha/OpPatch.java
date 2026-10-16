package com.houseofthemaster.api.ficha;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

/**
 * Uma operação do PATCH: caminho do campo + novo valor.
 * O valor fica como JsonNode para a validação distinguir 7, "7", 7.5, true e null.
 * Ausente no JSON = null em Java; "valor": null explícito = NullNode.
 */
@Getter
@Setter
public class OpPatch {

    @NotBlank(message = "Campo é obrigatório")
    private String campo;

    private JsonNode valor;
}
