package com.houseofthemaster.api.campanha;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DetalharCampanhaTest {

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
        mockMvc.perform(get("/campanhas/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void mestreVeDetalheComCodigoDeConvite() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        String corpo = criarCampanha(tokenMestre, "A Maldição de Strahd", 4);
        String id = JsonPath.read(corpo, "$.id");
        String codigo = JsonPath.read(corpo, "$.codigoConvite");

        mockMvc.perform(get("/campanhas/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenMestre))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.titulo").value("A Maldição de Strahd"))
            .andExpect(jsonPath("$.descricao").value("Descrição de teste"))
            .andExpect(jsonPath("$.sistema").value("D&D 5e"))
            .andExpect(jsonPath("$.papel").value("MESTRE"))
            .andExpect(jsonPath("$.jogadores").value(0))
            .andExpect(jsonPath("$.maxJogadores").value(4))
            .andExpect(jsonPath("$.codigoConvite").value(codigo));
    }

    @Test
    void jogadorVeDetalheSemCodigoDeConvite() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        String tokenJogador = registrarELogar("jogador@email.com");
        String corpo = criarCampanha(tokenMestre, "Tormenta", 5);
        String id = JsonPath.read(corpo, "$.id");
        entrar(tokenJogador, JsonPath.read(corpo, "$.codigoConvite"));

        mockMvc.perform(get("/campanhas/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenJogador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titulo").value("Tormenta"))
            .andExpect(jsonPath("$.papel").value("JOGADOR"))
            .andExpect(jsonPath("$.jogadores").value(1))
            .andExpect(jsonPath("$.codigoConvite").doesNotExist());
    }

    @Test
    void naoParticipanteRecebe404IgualAIdInexistente() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        String tokenOutro = registrarELogar("outro@email.com");
        String id = JsonPath.read(criarCampanha(tokenMestre, "Campanha Secreta", 4), "$.id");

        MockHttpServletResponse naoParticipa = mockMvc.perform(get("/campanhas/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOutro))
            .andExpect(status().isNotFound())
            .andReturn().getResponse();

        MockHttpServletResponse inexistente = mockMvc.perform(get("/campanhas/{id}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOutro))
            .andExpect(status().isNotFound())
            .andReturn().getResponse();

        // A resposta não pode revelar que a campanha existe.
        assertThat(naoParticipa.getContentAsString()).isEqualTo(inexistente.getContentAsString());
        assertThat(naoParticipa.getContentAsString()).doesNotContain("Campanha Secreta");
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
    private String criarCampanha(String token, String nome, Integer maxJogadores) throws Exception {
        return mockMvc.perform(post("/campanhas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nome": "%s", "sistema": "D&D 5e", "descricao": "Descrição de teste", "maxJogadores": %s}
                    """.formatted(nome, maxJogadores)))
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
