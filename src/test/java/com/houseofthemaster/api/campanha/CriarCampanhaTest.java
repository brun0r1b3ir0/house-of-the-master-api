package com.houseofthemaster.api.campanha;

import com.houseofthemaster.api.participacao.Papel;
import com.houseofthemaster.api.participacao.Participacao;
import com.houseofthemaster.api.participacao.ParticipacaoRepository;
import com.houseofthemaster.api.usuario.Usuario;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CriarCampanhaTest {

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
        mockMvc.perform(post("/campanhas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(campanhaJson("Tormenta", "Tormenta20", "5")))
            .andExpect(status().isUnauthorized());

        assertThat(campanhaRepository.count()).isZero();
    }

    @Test
    void criadorViraMestreERecebeCodigoDeConvite() throws Exception {
        String token = registrarELogar("mestre@email.com");

        String corpo = mockMvc.perform(post("/campanhas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(campanhaJson("A Maldição de Strahd", "D&D 5e", "4")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(notNullValue()))
            .andExpect(jsonPath("$.nome").value("A Maldição de Strahd"))
            .andExpect(jsonPath("$.sistema").value("D&D 5e"))
            .andExpect(jsonPath("$.descricao").value("Descrição de teste"))
            .andExpect(jsonPath("$.maxJogadores").value(4))
            .andExpect(jsonPath("$.papel").value("MESTRE"))
            .andExpect(jsonPath("$.codigoConvite").value(matchesPattern("[A-Z2-9]{4}-[A-Z2-9]{4}")))
            .andReturn().getResponse().getContentAsString();

        UUID campanhaId = UUID.fromString(JsonPath.read(corpo, "$.id"));
        Usuario mestre = usuarioRepository.findByEmail("mestre@email.com").orElseThrow();
        Participacao participacao = participacaoRepository
            .findByUsuarioIdAndCampanhaId(mestre.getId(), campanhaId).orElseThrow();

        assertThat(participacao.getPapel()).isEqualTo(Papel.MESTRE);
        assertThat(participacaoRepository.countByCampanhaIdAndPapel(campanhaId, Papel.JOGADOR)).isZero();
    }

    @Test
    void campanhaSemLimiteDeJogadoresEhAceita() throws Exception {
        String token = registrarELogar("mestre@email.com");

        mockMvc.perform(post("/campanhas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(campanhaJson("Campanha Livre", "Ordem Paranormal", "null")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.maxJogadores").doesNotExist());
    }

    @Test
    void campanhasDiferentesRecebemCodigosDiferentes() throws Exception {
        String token = registrarELogar("mestre@email.com");

        String codigo1 = criarCampanha(token, "Campanha 1");
        String codigo2 = criarCampanha(token, "Campanha 2");

        assertThat(codigo1).isNotEqualTo(codigo2);
    }

    @Test
    void semNomeRetorna400() throws Exception {
        String token = registrarELogar("mestre@email.com");

        mockMvc.perform(post("/campanhas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(campanhaJson("", "D&D 5e", "4")))
            .andExpect(status().isBadRequest());

        assertThat(campanhaRepository.count()).isZero();
    }

    @Test
    void semSistemaRetorna400() throws Exception {
        String token = registrarELogar("mestre@email.com");

        mockMvc.perform(post("/campanhas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(campanhaJson("Tormenta", "", "4")))
            .andExpect(status().isBadRequest());

        assertThat(campanhaRepository.count()).isZero();
    }

    @Test
    void maxJogadoresZeroRetorna400() throws Exception {
        String token = registrarELogar("mestre@email.com");

        mockMvc.perform(post("/campanhas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(campanhaJson("Tormenta", "Tormenta20", "0")))
            .andExpect(status().isBadRequest());

        assertThat(campanhaRepository.count()).isZero();
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
    private String criarCampanha(String token, String nome) throws Exception {
        String corpo = mockMvc.perform(post("/campanhas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(campanhaJson(nome, "D&D 5e", "4")))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        return JsonPath.read(corpo, "$.codigoConvite");
    }

    private static String campanhaJson(String nome, String sistema, String maxJogadores) {
        return """
            {"nome": "%s", "sistema": "%s", "descricao": "Descrição de teste", "maxJogadores": %s}
            """.formatted(nome, sistema, maxJogadores);
    }
}
