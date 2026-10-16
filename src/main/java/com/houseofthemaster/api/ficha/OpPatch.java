package com.houseofthemaster.api.ficha;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = FichaLimites.CAMPO_OP_MAXIMO, message = "Campo deve ter no máximo {max} caracteres")
    private String campo;

    private JsonNode valor;
}
