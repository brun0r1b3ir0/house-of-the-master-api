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
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ListarCampanhasTest {

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
        mockMvc.perform(get("/campanhas"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void comTokenInvalidoRetorna401() throws Exception {
        mockMvc.perform(get("/campanhas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token.invalido.qualquer"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioSemCampanhasRecebeListaVazia() throws Exception {
        String token = registrarELogar("ana@email.com");

        mockMvc.perform(get("/campanhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void mestreVeSuaCampanhaComCodigoDeConvite() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        String codigo = criarCampanha(tokenMestre, "A Maldição de Strahd", "D&D 5e", 4);

        mockMvc.perform(get("/campanhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenMestre))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].titulo").value("A Maldição de Strahd"))
            .andExpect(jsonPath("$[0].sistema").value("D&D 5e"))
            .andExpect(jsonPath("$[0].papel").value("MESTRE"))
            .andExpect(jsonPath("$[0].jogadores").value(0))
            .andExpect(jsonPath("$[0].maxJogadores").value(4))
            .andExpect(jsonPath("$[0].codigoConvite").value(codigo));
    }

    @Test
    void jogadorVeCampanhaSemCodigoEContagemAtualizaParaOMestre() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        String tokenJogador = registrarELogar("jogador@email.com");
        String codigo = criarCampanha(tokenMestre, "Tormenta", "Tormenta20", 5);

        mockMvc.perform(post("/campanhas/entrar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenJogador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"codigo": "%s"}
                    """.formatted(codigo)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/campanhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenJogador))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].titulo").value("Tormenta"))
            .andExpect(jsonPath("$[0].papel").value("JOGADOR"))
            .andExpect(jsonPath("$[0].jogadores").value(1))
            .andExpect(jsonPath("$[0].codigoConvite").doesNotExist());

        mockMvc.perform(get("/campanhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenMestre))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].papel").value("MESTRE"))
            .andExpect(jsonPath("$[0].jogadores").value(1));
    }

    @Test
    void usuarioNaoVeCampanhasDasQuaisNaoParticipa() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        String tokenOutro = registrarELogar("outro@email.com");
        criarCampanha(tokenMestre, "Campanha Secreta", "Ordem Paranormal", null);
        criarCampanha(tokenOutro, "Campanha do Outro", "Call of Cthulhu", 3);

        mockMvc.perform(get("/campanhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOutro))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].titulo").value("Campanha do Outro"));
    }

    @Test
    void mestreComVariasCampanhasVeTodas() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        criarCampanha(tokenMestre, "Campanha 1", "D&D 5e", 4);
        criarCampanha(tokenMestre, "Campanha 2", "Pathfinder", 6);

        mockMvc.perform(get("/campanhas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenMestre))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
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

    /** Cria a campanha e devolve o código de convite gerado. */
    private String criarCampanha(String token, String nome, String sistema, Integer maxJogadores) throws Exception {
        String max = maxJogadores == null ? "null" : maxJogadores.toString();

        String corpo = mockMvc.perform(post("/campanhas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nome": "%s", "sistema": "%s", "descricao": "Descrição de teste", "maxJogadores": %s}
                    """.formatted(nome, sistema, max)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        return JsonPath.read(corpo, "$.codigoConvite");
    }
}
