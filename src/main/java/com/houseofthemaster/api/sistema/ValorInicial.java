package com.houseofthemaster.api.sistema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Valor inicial de uma perícia do catálogo: um número fixo OU uma fórmula.
 * No JSON aparece como número (ex.: 15) ou como texto da fórmula (ex.: "DES_METADE").
 */
public record ValorInicial(Integer fixo, FormulaValorInicial formula) {

    public ValorInicial {
        if ((fixo == null) == (formula == null)) {
            throw new IllegalArgumentException("Valor inicial deve ser um número fixo ou uma fórmula.");
        }
        if (fixo != null && fixo < 0) {
            throw new IllegalArgumentException("Valor inicial fixo não pode ser negativo.");
        }
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ValorInicial de(Object valor) {
        if (valor instanceof Integer numero) {
            return new ValorInicial(numero, null);
        }
        if (valor instanceof String texto) {
            return new ValorInicial(null, FormulaValorInicial.valueOf(texto));
        }
        throw new IllegalArgumentException("Valor inicial inválido: " + valor);
    }

    @JsonValue
    public Object json() {
        return fixo != null ? fixo : formula.name();
    }
}
