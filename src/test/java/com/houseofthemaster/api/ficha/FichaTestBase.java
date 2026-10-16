package com.houseofthemaster.api.ficha;

import com.houseofthemaster.api.campanha.CampanhaRepository;
import com.houseofthemaster.api.participacao.ParticipacaoRepository;
import com.houseofthemaster.api.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auxiliares comuns aos testes HTTP de fichas.
 *
 * As fichas referenciam usuario e campanha, então são apagadas antes deles, e também DEPOIS de cada
 * teste: as outras classes de teste só limpam participacao/campanha/usuario.
 */
abstract class FichaTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private FichaEventoRepository fichaEventoRepository;

    @Autowired
    private FichaRepository fichaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private ParticipacaoRepository participacaoRepository;

    @BeforeEach
    @AfterEach
    void limparBanco() {
        fichaEventoRepository.deleteAll();
        fichaRepository.deleteAll();
        participacaoRepository.deleteAll();
        campanhaRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    /** Campanha com um Mestre e dois jogadores. */
    protected record Mesa(String campanhaId, String mestre, String jogador1, String jogador2) {
    }

    protected Mesa montarMesa() throws Exception {
        String mestre = registrarELogar("Mestre", "mestre@email.com");
        String jogador1 = registrarELogar("Ana", "ana@email.com");
        String jogador2 = registrarELogar("Bia", "bia@email.com");
        String corpo = criarCampanha(mestre, "Mesa War If");
        String codigo = JsonPath.read(corpo, "$.codigoConvite");
        entrar(jogador1, codigo);
        entrar(jogador2, codigo);
        return new Mesa(JsonPath.read(corpo, "$.id"), mestre, jogador1, jogador2);
    }

    protected String registrarELogar(String nome, String email) throws Exception {
        mockMvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nome": "%s", "email": "%s", "senha": "senha1234"}
                    """.formatted(nome, email)))
            .andExpect(status().isCreated());

        String corpo = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "%s", "senha": "senha1234"}
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        return JsonPath.read(corpo, "$.token");
    }

    /** Cria a campanha e devolve o corpo da resposta. */
    protected String criarCampanha(String token, String nome) throws Exception {
        return mockMvc.perform(post("/campanhas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nome": "%s", "sistema": "War If", "descricao": "Descrição de teste", "maxJogadores": 5}
                    """.formatted(nome)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    }

    protected void entrar(String token, String codigo) throws Exception {
        mockMvc.perform(post("/campanhas/entrar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"codigo": "%s"}
                    """.formatted(codigo)))
            .andExpect(status().isOk());
    }

    protected ResultActions postFicha(String token, String campanhaId, String json) throws Exception {
        return mockMvc.perform(post("/campanhas/{cid}/fichas", campanhaId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
    }

    /** Cria a ficha (espera 201) e devolve o id. */
    protected String criarFicha(String token, String campanhaId, TipoFicha tipo, String nome) throws Exception {
        String corpo = postFicha(token, campanhaId, """
                {"tipo": "%s", "nome": "%s", "ocupacao": "Detetive", "raca": "HUMANO"}
                """.formatted(tipo, nome))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.id");
    }

    protected ResultActions listarFichas(String token, String campanhaId) throws Exception {
        return mockMvc.perform(get("/campanhas/{cid}/fichas", campanhaId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    protected ResultActions getFicha(String token, String campanhaId, String fichaId) throws Exception {
        return mockMvc.perform(get("/campanhas/{cid}/fichas/{fid}", campanhaId, fichaId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    protected ResultActions patchFicha(String token, String campanhaId, String fichaId, String json) throws Exception {
        return mockMvc.perform(patch("/campanhas/{cid}/fichas/{fid}", campanhaId, fichaId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
    }

    protected ResultActions deleteFicha(String token, String campanhaId, String fichaId) throws Exception {
        return mockMvc.perform(delete("/campanhas/{cid}/fichas/{fid}", campanhaId, fichaId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
