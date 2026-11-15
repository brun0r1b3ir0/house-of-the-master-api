package com.houseofthemaster.api.ficha;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** VAMPIRO é permanente; a transformação grava FOR = 120 e DES = 120. */
@SpringBootTest
@AutoConfigureMockMvc
class FichaVampiroTest extends FichaTestBase {

    private static final String MENSAGEM = "A raça Vampiro é permanente e não pode ser trocada.";

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
        // FOR alterada depois da transformação (o bloqueio de FOR/DES é outra tarefa)
        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "atributos.FOR", "valor": 100}]}
                """)
            .andExpect(status().isOk());

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "raca", "valor": "VAMPIRO"}]}
                """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.raca").value("VAMPIRO"))
            .andExpect(jsonPath("$.estado.atributos.FOR").value(100))
            .andExpect(jsonPath("$.estado.atributos.DES").value(120));
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
