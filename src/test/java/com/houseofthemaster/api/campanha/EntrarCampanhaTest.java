package com.houseofthemaster.api.campanha;

import com.houseofthemaster.api.participacao.Papel;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EntrarCampanhaTest {

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
        mockMvc.perform(post("/campanhas/entrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(entrarJson("ABCD-EFGH")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void jogadorEntraComCodigoValidoSemReceberOCodigo() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        String tokenJogador = registrarELogar("jogador@email.com");
        String codigo = criarCampanha(tokenMestre, "Tormenta", 5);

        entrar(tokenJogador, codigo)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titulo").value("Tormenta"))
            .andExpect(jsonPath("$.sistema").value("D&D 5e"))
            .andExpect(jsonPath("$.papel").value("JOGADOR"))
            .andExpect(jsonPath("$.jogadores").value(1))
            .andExpect(jsonPath("$.maxJogadores").value(5))
            .andExpect(jsonPath("$.codigoConvite").doesNotExist());
    }

    @Test
    void codigoInvalidoRetorna404() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        String tokenJogador = registrarELogar("jogador@email.com");
        criarCampanha(tokenMestre, "Tormenta", 5);

        entrar(tokenJogador, "ZZZZ-9999")
            .andExpect(status().isNotFound());

        assertThat(participacaoRepository.count()).isEqualTo(1);
    }

    @Test
    void codigoEmBrancoRetorna400() throws Exception {
        String token = registrarELogar("jogador@email.com");

        entrar(token, "")
            .andExpect(status().isBadRequest());
    }

    @Test
    void jogadorQueJaParticipaRetorna409() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        String tokenJogador = registrarELogar("jogador@email.com");
        String codigo = criarCampanha(tokenMestre, "Tormenta", 5);

        entrar(tokenJogador, codigo).andExpect(status().isOk());

        entrar(tokenJogador, codigo)
            .andExpect(status().isConflict());

        assertThat(participacaoRepository.countByCampanhaIdAndPapel(campanhaIdPorCodigo(codigo), Papel.JOGADOR))
            .isEqualTo(1);
    }

    @Test
    void mestreTentandoEntrarNaPropriaCampanhaRetorna409() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        String codigo = criarCampanha(tokenMestre, "Tormenta", 5);

        entrar(tokenMestre, codigo)
            .andExpect(status().isConflict());

        assertThat(participacaoRepository.count()).isEqualTo(1);
    }

    @Test
    void campanhaCheiaRetorna409() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        String tokenJogador1 = registrarELogar("jogador1@email.com");
        String tokenJogador2 = registrarELogar("jogador2@email.com");
        String codigo = criarCampanha(tokenMestre, "Mesa Pequena", 1);

        entrar(tokenJogador1, codigo).andExpect(status().isOk());

        entrar(tokenJogador2, codigo)
            .andExpect(status().isConflict());

        assertThat(participacaoRepository.countByCampanhaIdAndPapel(campanhaIdPorCodigo(codigo), Papel.JOGADOR))
            .isEqualTo(1);
    }

    @Test
    void mestreNaoContaParaOLimiteDeJogadores() throws Exception {
        String tokenMestre = registrarELogar("mestre@email.com");
        String tokenJogador = registrarELogar("jogador@email.com");
        String codigo = criarCampanha(tokenMestre, "Mesa Pequena", 1);

        entrar(tokenJogador, codigo)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jogadores").value(1));
    }

    // ---------- Auxiliares ----------

    private ResultActions entrar(String token, String codigo) throws Exception {
        return mockMvc.perform(post("/campanhas/entrar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(entrarJson(codigo)));
    }

    private UUID campanhaIdPorCodigo(String codigo) {
        return campanhaRepository.findByCodigoConvite(codigo).orElseThrow().getId();
    }

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
    private String criarCampanha(String token, String nome, Integer maxJogadores) throws Exception {
        String corpo = mockMvc.perform(post("/campanhas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nome": "%s", "sistema": "D&D 5e", "descricao": "Descrição de teste", "maxJogadores": %s}
                    """.formatted(nome, maxJogadores)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        return JsonPath.read(corpo, "$.codigoConvite");
    }

    private static String entrarJson(String codigo) {
        return """
            {"codigo": "%s"}
            """.formatted(codigo);
    }
}
