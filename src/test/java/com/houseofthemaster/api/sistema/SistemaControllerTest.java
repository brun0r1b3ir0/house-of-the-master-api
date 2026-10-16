package com.houseofthemaster.api.sistema;

import com.houseofthemaster.api.campanha.CampanhaRepository;
import com.houseofthemaster.api.participacao.ParticipacaoRepository;
import com.houseofthemaster.api.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SistemaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private ParticipacaoRepository participacaoRepository;

    @BeforeEach
    void limparBanco() {
        participacaoRepository.deleteAll();
        campanhaRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mockMvc.perform(get("/campanhas/{id}/sistema", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void mestreRecebeOCatalogo() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        String id = JsonPath.read(criarCampanha(tokenMestre), "$.id");

        mockMvc.perform(get("/campanhas/{id}/sistema", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenMestre))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sistema").value("War If"))
            .andExpect(jsonPath("$.pericias", hasSize(71)))
            .andExpect(jsonPath("$.estagiosSanidade", hasSize(8)));
    }

    @Test
    void jogadorRecebeOCatalogoComValoresIniciaisEmNumeroOuFormula() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        String tokenJogador = registrarELogar("jogador@email.com");
        String corpo = criarCampanha(tokenMestre);
        String id = JsonPath.read(corpo, "$.id");
        entrar(tokenJogador, JsonPath.read(corpo, "$.codigoConvite"));

        mockMvc.perform(get("/campanhas/{id}/sistema", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenJogador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pericias", hasSize(71)))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Esquivar')].valorInicial").value("DES_METADE"))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Esquivar')].exigePrecisao").value(true))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Língua Nativa (Inglês)')].valorInicial").value("EDU"))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Primórdios')].valorInicial").value(0))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Primórdios')].categoria").value("DESCONHECIDA"))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Furtividade')].valorInicial").value(20))
            .andExpect(jsonPath("$.estagiosSanidade[0].chave").value("PLENITUDE"))
            .andExpect(jsonPath("$.estagiosSanidade[7].chave").value("INFERNO"));
    }

    @Test
    void naoParticipanteRecebe404IgualACampanhaInexistente() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        String tokenOutro = registrarELogar("outro@email.com");
        String id = JsonPath.read(criarCampanha(tokenMestre), "$.id");

        MockHttpServletResponse naoParticipa = mockMvc.perform(get("/campanhas/{id}/sistema", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOutro))
            .andExpect(status().isNotFound())
            .andReturn().getResponse();

        MockHttpServletResponse inexistente = mockMvc.perform(get("/campanhas/{id}/sistema", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOutro))
            .andExpect(status().isNotFound())
            .andReturn().getResponse();

        assertThat(naoParticipa.getContentAsString()).isEqualTo(inexistente.getContentAsString());
    }

    // ---------- Auxiliares ----------

    private String registrarELogar(String email) throws Exception {
        mockMvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nome": "Usuário Teste", "email": "%s", "senha": "senha1234"}
                    """.formatted(email)))
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
    private String criarCampanha(String token) throws Exception {
        return mockMvc.perform(post("/campanhas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nome": "Mesa War If", "sistema": "War If", "descricao": "Descrição de teste", "maxJogadores": 4}
                    """))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    }

    private void entrar(String token, String codigo) throws Exception {
        mockMvc.perform(post("/campanhas/entrar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"codigo": "%s"}
                    """.formatted(codigo)))
            .andExpect(status().isOk());
    }
}
