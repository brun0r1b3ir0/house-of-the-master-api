package com.houseofthemaster.api.ficha;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tetos técnicos de FichaLimites no PATCH e no POST. */
@SpringBootTest
@AutoConfigureMockMvc
class FichaLimitesTest extends FichaTestBase {

    /** Todo campo inteiro do PATCH com o seu teto. */
    static Stream<Arguments> camposInteiros() {
        return Stream.of(
                FichaEstado.ATRIBUTOS.stream().map(c -> Arguments.of("atributos." + c, FichaLimites.VALOR_MAXIMO)),
                FichaEstado.ATUAIS.stream().map(c -> Arguments.of("atuais." + c, FichaLimites.VALOR_MAXIMO)),
                FichaEstado.CONTADORES.stream().map(c -> Arguments.of("contadores." + c, FichaLimites.maximoDoContador(c))),
                Stream.of(
                    Arguments.of("perdaPermanenteTam", FichaLimites.VALOR_MAXIMO),
                    Arguments.of("condicoes.em-choque", FichaLimites.VALOR_MAXIMO),
                    Arguments.of("pericias.Furtividade.ganho", FichaLimites.VALOR_MAXIMO),
                    Arguments.of("pericias.Esquivar.ganho", FichaLimites.VALOR_MAXIMO),
                    Arguments.of("pericias.Língua Nativa (Inglês).ganho", FichaLimites.VALOR_MAXIMO)))
            .flatMap(s -> s);
    }

    @ParameterizedTest(name = "[{index}] {0} aceita 0..{1}")
    @MethodSource("camposInteiros")
    void inteiroNoTetoPassaEUmAcimaRetorna400(String campo, int teto) throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, op(campo, String.valueOf(teto + 1)))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("entre 0 e " + teto)));

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, op(campo, String.valueOf(teto)))
            .andExpect(status().isOk());

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, op(campo, "0"))
            .andExpect(status().isOk());
    }

    @Test
    void sangriaMantemOTetoProprio() {
        org.assertj.core.api.Assertions.assertThat(FichaLimites.maximoDoContador("sangria")).isEqualTo(10);
        org.assertj.core.api.Assertions.assertThat(FichaLimites.maximoDoContador("fome"))
            .isEqualTo(FichaLimites.VALOR_MAXIMO);
    }

    @ParameterizedTest(name = "[{index}] PATCH {0}")
    @ValueSource(strings = {"nome", "ocupacao"})
    void textoDoPatchAte120Caracteres(String campo) throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        String noLimite = "a".repeat(FichaLimites.TEXTO_MAXIMO);

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, op(campo, "\"" + noLimite + "a\""))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("no máximo " + FichaLimites.TEXTO_MAXIMO)));

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, op(campo, "\"" + noLimite + "\""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$." + campo).value(noLimite));
    }

    @ParameterizedTest(name = "[{index}] POST {0}")
    @ValueSource(strings = {"nome", "ocupacao"})
    void textoDoPostAte120Caracteres(String campo) throws Exception {
        Mesa mesa = montarMesa();
        String noLimite = "a".repeat(FichaLimites.TEXTO_MAXIMO);
        String outro = campo.equals("nome") ? "ocupacao" : "nome";

        postFicha(mesa.jogador1(), mesa.campanhaId(), """
            {"tipo": "PJ", "%s": "%sa", "%s": "Ok"}
            """.formatted(campo, noLimite, outro)).andExpect(status().isBadRequest());

        postFicha(mesa.jogador1(), mesa.campanhaId(), """
                {"tipo": "PJ", "%s": "%s", "%s": "Ok"}
                """.formatted(campo, noLimite, outro))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$." + campo).value(noLimite));
    }

    @Test
    void caminhoDaOpAcimaDoTetoRetorna400() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        String nomeLongo = "a".repeat(FichaLimites.CAMPO_OP_MAXIMO);

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, op("pericias." + nomeLongo + ".ganho", "1"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void maisOpsQueOTetoRetorna400() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        String uma = """
            {"campo": "atuais.PV", "valor": 1}""";

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha,
                "{\"ops\": [" + String.join(",", java.util.Collections.nCopies(FichaLimites.OPS_MAXIMAS + 1, uma)) + "]}")
            .andExpect(status().isBadRequest());

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha,
                "{\"ops\": [" + String.join(",", java.util.Collections.nCopies(FichaLimites.OPS_MAXIMAS, uma)) + "]}")
            .andExpect(status().isOk());
    }

    private static String op(String campo, String valorJson) {
        return """
            {"ops": [{"campo": "%s", "valor": %s}]}
            """.formatted(campo, valorJson);
    }
}
