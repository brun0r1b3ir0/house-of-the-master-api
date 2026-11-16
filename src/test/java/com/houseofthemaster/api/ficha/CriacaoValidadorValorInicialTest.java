package com.houseofthemaster.api.ficha;

import com.houseofthemaster.api.sistema.FormulaValorInicial;
import com.houseofthemaster.api.sistema.ValorInicial;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valor inicial da Esquivar (fórmula DES_METADE). Na criação a DES do Vampiro é sempre 120, e aí DES/2
 * também daria 60; por isso a independência da DES é verificada aqui, com DES diferente de 120.
 */
class CriacaoValidadorValorInicialTest {

    private static final ValorInicial ESQUIVAR = new ValorInicial(null, FormulaValorInicial.DES_METADE);

    @ParameterizedTest(name = "DES = {0}")
    @ValueSource(ints = {0, 50, 120, 121, 200, 9999})
    void esquivarDeVampiroE60MesmoComDesDiferente(int des) {
        assertThat(CriacaoValidador.valorInicial(ESQUIVAR, Map.of("DES", des, "EDU", 60), Raca.VAMPIRO))
            .isEqualTo(60);
    }

    @Test
    void esquivarDasOutrasRacasContinuaDesMetade() {
        for (Raca raca : Raca.values()) {
            if (raca == Raca.VAMPIRO) {
                continue;
            }
            assertThat(CriacaoValidador.valorInicial(ESQUIVAR, Map.of("DES", 71, "EDU", 60), raca))
                .as("raça %s", raca)
                .isEqualTo(35);
        }
        assertThat(CriacaoValidador.valorInicial(ESQUIVAR, Map.of("DES", 71, "EDU", 60), null)).isEqualTo(35);
    }
}
