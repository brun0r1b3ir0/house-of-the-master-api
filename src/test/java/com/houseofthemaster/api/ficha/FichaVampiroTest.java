package com.houseofthemaster.api.ficha;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** VAMPIRO é permanente; FOR e DES são fixas em 120 (POST, transformação e PATCH). */
@SpringBootTest
@AutoConfigureMockMvc
class FichaVampiroTest extends FichaTestBase {

    private static final String MENSAGEM = "A raça Vampiro é permanente e não pode ser trocada.";
    private static final String MENSAGEM_FOR_DES = "FOR e DES de vampiro são fixas em 120 e não podem ser alteradas.";

    private String fichaVampira(Mesa mesa) throws Exception {
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Carmilla");
        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "raca", "valor": "VAMPIRO"}]}
                """)
            .andExpect(status().isOk());
        return ficha;
    }

    @Test
    void vampiroNaoViraHumano() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = fichaVampira(mesa);

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "raca", "valor": "HUMANO"}]}
                """)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString(MENSAGEM)));

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.raca").value("VAMPIRO"));
    }

    @Test
    void vampiroNaoFicaSemRaca() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = fichaVampira(mesa);

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "raca", "valor": null}]}
                """)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString(MENSAGEM)));

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.raca").value("VAMPIRO"));
    }

    @Test
    void humanoViraVampiroComForEDes120() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Carmilla");
        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "raca", "valor": "HUMANO"}, {"campo": "atributos.CON", "valor": 65}]}
                """)
            .andExpect(status().isOk());

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "raca", "valor": "VAMPIRO"}]}
                """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.raca").value("VAMPIRO"))
            .andExpect(jsonPath("$.estado.atributos.FOR").value(120))
            .andExpect(jsonPath("$.estado.atributos.DES").value(120));

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.raca").value("VAMPIRO"))
            .andExpect(jsonPath("$.estado.atributos.FOR").value(120))
            .andExpect(jsonPath("$.estado.atributos.DES").value(120))
            .andExpect(jsonPath("$.estado.atributos.CON").value(65));
    }

    @Test
    void vampiroParaVampiroNaoEErroENaoMudaNada() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = fichaVampira(mesa);
        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "atributos.CON", "valor": 70}]}
                """)
            .andExpect(status().isOk());

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "raca", "valor": "VAMPIRO"}]}
                """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.raca").value("VAMPIRO"))
            .andExpect(jsonPath("$.estado.atributos.FOR").value(120))
            .andExpect(jsonPath("$.estado.atributos.DES").value(120))
            .andExpect(jsonPath("$.estado.atributos.CON").value(70));
    }

    // ---------- FOR/DES fixas em 120 ----------

    @Test
    void postDeVampiroJaNasceComForEDes120() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarVampiroPeloPost(mesa);

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.raca").value("VAMPIRO"))
            .andExpect(jsonPath("$.estado.atributos.FOR").value(120))
            .andExpect(jsonPath("$.estado.atributos.DES").value(120))
            .andExpect(jsonPath("$.estado.atributos.CON").value(50));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({"FOR, 100", "DES, 50", "FOR, 0", "DES, 121"})
    void patchDeForOuDesDiferenteDe120EmVampiroRetorna400ENadaMuda(String atributo, int valor) throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarVampiroPeloPost(mesa);

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "atributos.CON", "valor": 70}, {"campo": "atributos.%s", "valor": %d}]}
                """.formatted(atributo, valor))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("ops[1] (atributos." + atributo + "): " + MENSAGEM_FOR_DES)));

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.estado.atributos.FOR").value(120))
            .andExpect(jsonPath("$.estado.atributos.DES").value(120))
            .andExpect(jsonPath("$.estado.atributos.CON").value(50));
    }

    @Test
    void patchDeForEDes120EmVampiroENoOp() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarVampiroPeloPost(mesa);

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "atributos.FOR", "valor": 120}, {"campo": "atributos.DES", "valor": 120},
                         {"campo": "atributos.CON", "valor": 70}]}
                """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado.atributos.FOR").value(120))
            .andExpect(jsonPath("$.estado.atributos.DES").value(120))
            .andExpect(jsonPath("$.estado.atributos.CON").value(70));

        // só CON gerou evento: FOR/DES = 120 não mudou nada
        eventos(mesa, ficha)
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].campo").value("atributos.CON"));
    }

    @Test
    void transformarEDepoisMudarForNoMesmoPatchRetorna400() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Carmilla");

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "raca", "valor": "VAMPIRO"}, {"campo": "atributos.FOR", "valor": 100}]}
                """)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("ops[1] (atributos.FOR): " + MENSAGEM_FOR_DES)));

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.raca").value("HUMANO"))
            .andExpect(jsonPath("$.estado.atributos.FOR").value(50));
    }

    @Test
    void forAlteradaAntesDaTransformacaoNoMesmoPatchTerminaEm120() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Carmilla");

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "atributos.FOR", "valor": 100}, {"campo": "raca", "valor": "VAMPIRO"}]}
                """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado.atributos.FOR").value(120))
            .andExpect(jsonPath("$.estado.atributos.DES").value(120));
    }

    @Test
    void patchDeForEDesEmHumanoContinuaFuncionando() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Van Helsing");

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "atributos.FOR", "valor": 80}, {"campo": "atributos.DES", "valor": 30}]}
                """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado.atributos.FOR").value(80))
            .andExpect(jsonPath("$.estado.atributos.DES").value(30));

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.estado.atributos.FOR").value(80))
            .andExpect(jsonPath("$.estado.atributos.DES").value(30));
    }

    private String criarVampiroPeloPost(Mesa mesa) throws Exception {
        String resposta = postFicha(mesa.jogador1(), mesa.campanhaId(), """
                {"tipo": "PJ", "nome": "Carmilla", "raca": "VAMPIRO"}
                """)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.estado.atributos.FOR").value(120))
            .andExpect(jsonPath("$.estado.atributos.DES").value(120))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resposta, "$.id");
    }

    private ResultActions eventos(Mesa mesa, String ficha) throws Exception {
        return mockMvc.perform(get("/campanhas/{cid}/fichas/{fid}/eventos", mesa.campanhaId(), ficha)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + mesa.jogador1()));
    }

    @Test
    void transformarEDesfazerNoMesmoPatchFalhaENadaEAplicado() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Carmilla");

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "raca", "valor": "VAMPIRO"}, {"campo": "raca", "valor": "HUMANO"}]}
                """)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("ops[1]")))
            .andExpect(content().string(containsString(MENSAGEM)));

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.raca").value("HUMANO"))
            .andExpect(jsonPath("$.estado.atributos.FOR").value(50))
            .andExpect(jsonPath("$.estado.atributos.DES").value(50));
    }
}
