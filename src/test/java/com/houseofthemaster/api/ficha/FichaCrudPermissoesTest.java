package com.houseofthemaster.api.ficha;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FichaCrudPermissoesTest extends FichaTestBase {

    // ---------- Não participante ----------

    @Test
    void semTokenRetorna401() throws Exception {
        mockMvc.perform(get("/campanhas/{cid}/fichas", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void naoParticipanteRecebe404EmTodasAsRotas() throws Exception {
        Mesa mesa = montarMesa();
        String fichaId = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        String outro = registrarELogar("Outro", "outro@email.com");
        String cid = mesa.campanhaId();

        MockHttpServletResponse lista = listarFichas(outro, cid).andExpect(status().isNotFound())
            .andReturn().getResponse();
        MockHttpServletResponse inexistente = listarFichas(outro, UUID.randomUUID().toString())
            .andExpect(status().isNotFound()).andReturn().getResponse();
        assertThat(lista.getContentAsString()).isEqualTo(inexistente.getContentAsString());

        postFicha(outro, cid, """
            {"tipo": "PJ", "nome": "Intruso"}
            """).andExpect(status().isNotFound());
        getFicha(outro, cid, fichaId).andExpect(status().isNotFound());
        patchFicha(outro, cid, fichaId, """
            {"ops": [{"campo": "nome", "valor": "Hackeado"}]}
            """).andExpect(status().isNotFound());
        deleteFicha(outro, cid, fichaId).andExpect(status().isNotFound());

        getFicha(mesa.jogador1(), cid, fichaId)
            .andExpect(jsonPath("$.nome").value("Ezra"));
    }

    // ---------- Criação ----------

    @Test
    void jogadorCriaPjComAs71PericiasEEstadoInicial() throws Exception {
        Mesa mesa = montarMesa();

        postFicha(mesa.jogador1(), mesa.campanhaId(), """
                {"tipo": "PJ", "nome": "Ezra", "ocupacao": "Detetive", "raca": "VAMPIRO"}
                """)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(notNullValue()))
            .andExpect(jsonPath("$.campanhaId").value(mesa.campanhaId()))
            .andExpect(jsonPath("$.tipo").value("PJ"))
            .andExpect(jsonPath("$.nome").value("Ezra"))
            .andExpect(jsonPath("$.ocupacao").value("Detetive"))
            .andExpect(jsonPath("$.raca").value("VAMPIRO"))
            .andExpect(jsonPath("$.donoNome").value("Ana"))
            .andExpect(jsonPath("$.criadaEm").value(notNullValue()))
            .andExpect(jsonPath("$.estado.atributos.FOR").value(50))
            .andExpect(jsonPath("$.estado.atributos.EDU").value(50))
            .andExpect(jsonPath("$.estado.atuais.PV").value(10))
            .andExpect(jsonPath("$.estado.atuais.SAN").value(100))
            .andExpect(jsonPath("$.estado.atuais.SRT").value(100))
            .andExpect(jsonPath("$.estado.estagioSanidade").value("MENTE_SA"))
            .andExpect(jsonPath("$.estado.contadores.sangria").value(0))
            .andExpect(jsonPath("$.estado.flags.verbena").value(false))
            .andExpect(jsonPath("$.estado.perdaPermanenteTam").value(0))
            .andExpect(jsonPath("$.pericias", hasSize(71)))
            .andExpect(jsonPath("$.pericias[0].nome").value("Armas de Fogo (Arcos)"))
            .andExpect(jsonPath("$.pericias[0].ganho").value(0))
            .andExpect(jsonPath("$.pericias[0].ganhoTreino").value(0))
            .andExpect(jsonPath("$.pericias[0].origem").value("CATALOGO"))
            .andExpect(jsonPath("$.pericias[0].valorInicial").doesNotExist())
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Olfato')].categoria").value("FISICA"))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Primórdios')].categoria").value("DESCONHECIDA"));
    }

    @Test
    void ocupacaoERacaSaoOpcionais() throws Exception {
        Mesa mesa = montarMesa();

        postFicha(mesa.jogador1(), mesa.campanhaId(), """
                {"tipo": "PJ", "nome": "Sem Raça"}
                """)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ocupacao").doesNotExist())
            .andExpect(jsonPath("$.raca").doesNotExist())
            .andExpect(jsonPath("$.pericias", hasSize(71)));
    }

    @Test
    void semNomeOuSemTipoOuRacaInvalidaRetorna400() throws Exception {
        Mesa mesa = montarMesa();

        postFicha(mesa.jogador1(), mesa.campanhaId(), """
            {"tipo": "PJ", "nome": ""}
            """).andExpect(status().isBadRequest());
        postFicha(mesa.jogador1(), mesa.campanhaId(), """
            {"nome": "Ezra"}
            """).andExpect(status().isBadRequest());
        postFicha(mesa.jogador1(), mesa.campanhaId(), """
            {"tipo": "PJ", "nome": "Ezra", "raca": "ELFO"}
            """).andExpect(status().isBadRequest());
        postFicha(mesa.jogador1(), mesa.campanhaId(), """
            {"tipo": "DEUS", "nome": "Ezra"}
            """).andExpect(status().isBadRequest());

        listarFichas(mesa.jogador1(), mesa.campanhaId()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void jogadorNaoCriaNpc() throws Exception {
        Mesa mesa = montarMesa();

        postFicha(mesa.jogador1(), mesa.campanhaId(), """
            {"tipo": "NPC", "nome": "Capanga"}
            """).andExpect(status().isForbidden());

        listarFichas(mesa.mestre(), mesa.campanhaId()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void mestreCriaNpcMasNaoPj() throws Exception {
        Mesa mesa = montarMesa();

        postFicha(mesa.mestre(), mesa.campanhaId(), """
            {"tipo": "PJ", "nome": "PJ do Mestre"}
            """).andExpect(status().isForbidden());

        postFicha(mesa.mestre(), mesa.campanhaId(), """
                {"tipo": "NPC", "nome": "Conde"}
                """)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tipo").value("NPC"))
            .andExpect(jsonPath("$.donoNome").value("Mestre"))
            .andExpect(jsonPath("$.pericias", hasSize(71)));
    }

    // ---------- Listagem ----------

    @Test
    void jogadorListaSoAsSuasEMestreListaTodas() throws Exception {
        Mesa mesa = montarMesa();
        criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra 2");
        criarFicha(mesa.jogador2(), mesa.campanhaId(), TipoFicha.PJ, "Lia");
        criarFicha(mesa.mestre(), mesa.campanhaId(), TipoFicha.NPC, "Conde");

        listarFichas(mesa.jogador1(), mesa.campanhaId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[*].nome", containsInAnyOrder("Ezra", "Ezra 2")))
            .andExpect(jsonPath("$[0].donoNome").value("Ana"))
            .andExpect(jsonPath("$[0].estado").doesNotExist());

        listarFichas(mesa.jogador2(), mesa.campanhaId())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].nome").value("Lia"));

        listarFichas(mesa.mestre(), mesa.campanhaId())
            .andExpect(jsonPath("$", hasSize(4)))
            .andExpect(jsonPath("$[*].nome", containsInAnyOrder("Ezra", "Ezra 2", "Lia", "Conde")));
    }

    // ---------- Leitura ----------

    @Test
    void jogadorNaoLeFichaDeOutroJogadorNemNpc() throws Exception {
        Mesa mesa = montarMesa();
        String fichaDaBia = criarFicha(mesa.jogador2(), mesa.campanhaId(), TipoFicha.PJ, "Lia");
        String npc = criarFicha(mesa.mestre(), mesa.campanhaId(), TipoFicha.NPC, "Conde");

        MockHttpServletResponse alheia = getFicha(mesa.jogador1(), mesa.campanhaId(), fichaDaBia)
            .andExpect(status().isNotFound()).andReturn().getResponse();
        MockHttpServletResponse inexistente = getFicha(mesa.jogador1(), mesa.campanhaId(), UUID.randomUUID().toString())
            .andExpect(status().isNotFound()).andReturn().getResponse();
        assertThat(alheia.getContentAsString()).isEqualTo(inexistente.getContentAsString());

        getFicha(mesa.jogador1(), mesa.campanhaId(), npc).andExpect(status().isNotFound());
    }

    @Test
    void mestreLePjDeJogador() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        getFicha(mesa.mestre(), mesa.campanhaId(), ficha)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Ezra"))
            .andExpect(jsonPath("$.donoNome").value("Ana"))
            .andExpect(jsonPath("$.pericias", hasSize(71)));
    }

    @Test
    void fichaDeOutraCampanhaRetorna404() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        // A Ana também participa (como Mestre) de outra campanha: a URL com o cid errado não acha a ficha.
        String outraCampanha = JsonPath.read(criarCampanha(mesa.jogador1(), "Outra Mesa"), "$.id");

        getFicha(mesa.jogador1(), outraCampanha, ficha).andExpect(status().isNotFound());
        patchFicha(mesa.jogador1(), outraCampanha, ficha, """
            {"ops": [{"campo": "nome", "valor": "X"}]}
            """).andExpect(status().isNotFound());
        deleteFicha(mesa.jogador1(), outraCampanha, ficha).andExpect(status().isNotFound());

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Ezra"));
    }

    // ---------- Arquivamento ----------

    @Test
    void jogadorArquivaASuaEElaSomeParaTodos() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        deleteFicha(mesa.jogador1(), mesa.campanhaId(), ficha).andExpect(status().isNoContent());

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha).andExpect(status().isNotFound());
        getFicha(mesa.mestre(), mesa.campanhaId(), ficha).andExpect(status().isNotFound());
        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
            {"ops": [{"campo": "nome", "valor": "X"}]}
            """).andExpect(status().isNotFound());
        deleteFicha(mesa.jogador1(), mesa.campanhaId(), ficha).andExpect(status().isNotFound());
        listarFichas(mesa.jogador1(), mesa.campanhaId()).andExpect(jsonPath("$", hasSize(0)));
        listarFichas(mesa.mestre(), mesa.campanhaId()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void jogadorNaoArquivaFichaAlheia() throws Exception {
        Mesa mesa = montarMesa();
        String fichaDaBia = criarFicha(mesa.jogador2(), mesa.campanhaId(), TipoFicha.PJ, "Lia");

        deleteFicha(mesa.jogador1(), mesa.campanhaId(), fichaDaBia).andExpect(status().isNotFound());

        getFicha(mesa.jogador2(), mesa.campanhaId(), fichaDaBia).andExpect(status().isOk());
    }

    @Test
    void mestreNaoArquivaPjMasArquivaNpc() throws Exception {
        Mesa mesa = montarMesa();
        String pj = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        String npc = criarFicha(mesa.mestre(), mesa.campanhaId(), TipoFicha.NPC, "Conde");

        deleteFicha(mesa.mestre(), mesa.campanhaId(), pj).andExpect(status().isForbidden());
        getFicha(mesa.jogador1(), mesa.campanhaId(), pj).andExpect(status().isOk());

        deleteFicha(mesa.mestre(), mesa.campanhaId(), npc).andExpect(status().isNoContent());
        listarFichas(mesa.mestre(), mesa.campanhaId())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].nome").value("Ezra"));
    }
}
