package com.houseofthemaster.api.usuario;

import com.houseofthemaster.api.campanha.CampanhaRepository;
import com.houseofthemaster.api.participacao.ParticipacaoRepository;
import com.houseofthemaster.api.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private ParticipacaoRepository participacaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void limparBanco() {
        participacaoRepository.deleteAll();
        campanhaRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    // ---------- Registro ----------

    @Test
    void registroValidoRetorna201ESalvaSenhaComHash() throws Exception {
        mockMvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registroJson("Ana", "ana@email.com", "senha1234")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(notNullValue()))
            .andExpect(jsonPath("$.nome").value("Ana"))
            .andExpect(jsonPath("$.email").value("ana@email.com"))
            .andExpect(jsonPath("$.senha").doesNotExist())
            .andExpect(jsonPath("$.senhaHash").doesNotExist());

        Usuario salvo = usuarioRepository.findByEmail("ana@email.com").orElseThrow();
        assertThat(salvo.getSenhaHash()).isNotEqualTo("senha1234");
        assertThat(passwordEncoder.matches("senha1234", salvo.getSenhaHash())).isTrue();
    }

    @Test
    void registroComEmailJaUsadoRetorna409() throws Exception {
        registrar("Ana", "ana@email.com", "senha1234");

        mockMvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registroJson("Outra Ana", "ana@email.com", "outrasenha")))
            .andExpect(status().isConflict());

        assertThat(usuarioRepository.count()).isEqualTo(1);
    }

    @Test
    void registroComEmailInvalidoRetorna400() throws Exception {
        mockMvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registroJson("Ana", "nao-e-email", "senha1234")))
            .andExpect(status().isBadRequest());

        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    void registroComSenhaCurtaRetorna400() throws Exception {
        mockMvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registroJson("Ana", "ana@email.com", "1234567")))
            .andExpect(status().isBadRequest());

        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    void registroSemNomeRetorna400() throws Exception {
        mockMvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registroJson("", "ana@email.com", "senha1234")))
            .andExpect(status().isBadRequest());
    }

    // ---------- Login ----------

    @Test
    void loginValidoRetornaTokenComIdDoUsuario() throws Exception {
        registrar("Ana", "ana@email.com", "senha1234");
        Usuario usuario = usuarioRepository.findByEmail("ana@email.com").orElseThrow();

        String corpo = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("ana@email.com", "senha1234")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value(notNullValue()))
            .andExpect(jsonPath("$.usuario.id").value(usuario.getId().toString()))
            .andExpect(jsonPath("$.usuario.email").value("ana@email.com"))
            .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(corpo, "$.token");
        Claims claims = jwtService.validar(token);
        assertThat(claims.getSubject()).isEqualTo(usuario.getId().toString());
        assertThat(claims.get("email", String.class)).isEqualTo("ana@email.com");
    }

    @Test
    void loginComSenhaErradaRetorna401() throws Exception {
        registrar("Ana", "ana@email.com", "senha1234");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("ana@email.com", "senhaErrada")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void loginComEmailInexistenteRetorna401() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("ninguem@email.com", "senha1234")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loginComCamposEmBrancoRetorna400() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("", "")))
            .andExpect(status().isBadRequest());
    }

    // ---------- Auxiliares ----------

    private void registrar(String nome, String email, String senha) throws Exception {
        mockMvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registroJson(nome, email, senha)))
            .andExpect(status().isCreated());
    }

    private static String registroJson(String nome, String email, String senha) {
        return """
            {"nome": "%s", "email": "%s", "senha": "%s"}
            """.formatted(nome, email, senha);
    }

    private static String loginJson(String email, String senha) {
        return """
            {"email": "%s", "senha": "%s"}
            """.formatted(email, senha);
    }
}
