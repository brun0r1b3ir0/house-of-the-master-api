package com.houseofthemaster.api.ficha;

import com.houseofthemaster.api.RelogioAjustavel;
import com.houseofthemaster.api.usuario.Usuario;
import com.houseofthemaster.api.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FichaEventoTest.RelogioDeTeste.class)
class FichaEventoTest extends FichaTestBase {

    private static final Instant T0 = Instant.parse("2026-10-16T12:00:00Z");

    @TestConfiguration
    static class RelogioDeTeste {
        @Bean
        @Primary
        RelogioAjustavel relogioAjustavel() {
            return new RelogioAjustavel(T0);
        }
    }

    @Autowired
    private RelogioAjustavel relogio;

    @Autowired
    private FichaEventoService fichaEventoService;

    @Autowired
    private FichaRepository fichaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void zerarRelogio() {
        relogio.definir(T0);
    }

    // ---------- Criação de eventos ----------

    @Test
    void eventoSoQuandoOValorMuda() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        relogio.avancar(Duration.ofSeconds(10));

        patch(mesa.jogador1(), mesa, ficha, "atuais.PV", "7").andExpect(status().isOk());

        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].campo").value("atuais.PV"))
            .andExpect(jsonPath("$[0].anterior").value(10))
            .andExpect(jsonPath("$[0].novo").value(7))
            .andExpect(jsonPath("$[0].autorNome").value("Ana"))
            .andExpect(jsonPath("$[0].em").value("2026-10-16T12:00:10Z"));

        // valor igual ao atual: nenhum evento e atualizadaEm não avança
        relogio.avancar(Duration.ofSeconds(60));
        patch(mesa.jogador1(), mesa, ficha, "atuais.PV", "7").andExpect(status().isOk());

        eventos(mesa.jogador1(), mesa, ficha, "").andExpect(jsonPath("$", hasSize(1)));
        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.atualizadaEm").value("2026-10-16T12:00:10Z"))
            .andExpect(jsonPath("$.criadaEm").value("2026-10-16T12:00:00Z"));
    }

    @ParameterizedTest(name = "raca = {0}")
    @ValueSource(strings = {"null", "\"\"", "\"   \""})
    void racaNulaOuVaziaEmHumanoContinuaHumanoSemEvento(String valor) throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patch(mesa.jogador1(), mesa, ficha, "raca", valor)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.raca").value("HUMANO"));

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha).andExpect(jsonPath("$.raca").value("HUMANO"));
        eventos(mesa.jogador1(), mesa, ficha, "").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void racaNulaEmBruxoViraHumanoComEvento() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        patch(mesa.jogador1(), mesa, ficha, "raca", "\"BRUXO\"").andExpect(status().isOk());
        relogio.avancar(Duration.ofSeconds(1));

        patch(mesa.jogador1(), mesa, ficha, "raca", "null")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.raca").value("HUMANO"));

        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].campo").value("raca"))
            .andExpect(jsonPath("$[0].anterior").value("BRUXO"))
            .andExpect(jsonPath("$[0].novo").value("HUMANO"));
    }

    @Test
    void campoDoEventoEhOCaminhoCompletoDaOp() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patch(mesa.jogador1(), mesa, ficha, "pericias.Furtividade.ganho", "20").andExpect(status().isOk());
        relogio.avancar(Duration.ofSeconds(1));
        patch(mesa.jogador1(), mesa, ficha, "raca", "\"BRUXO\"").andExpect(status().isOk());

        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].campo").value("raca"))
            .andExpect(jsonPath("$[0].anterior").value("HUMANO"))
            .andExpect(jsonPath("$[0].novo").value("BRUXO"))
            .andExpect(jsonPath("$[1].campo").value("pericias.Furtividade.ganho"))
            .andExpect(jsonPath("$[1].anterior").value(0))
            .andExpect(jsonPath("$[1].novo").value(20));
    }

    @Test
    void opsRepetidasNoMesmoPatchViramUmEventoSo() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patchOps(mesa.jogador1(), mesa, ficha, """
            {"campo": "atuais.PV", "valor": 8}, {"campo": "atuais.PV", "valor": 6}
            """).andExpect(status().isOk());

        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].anterior").value(10))
            .andExpect(jsonPath("$[0].novo").value(6));

        // ida e volta no mesmo PATCH: nenhum evento e atualizadaEm não avança
        relogio.avancar(Duration.ofSeconds(60));
        patchOps(mesa.jogador1(), mesa, ficha, """
            {"campo": "atuais.PE", "valor": 4}, {"campo": "atuais.PE", "valor": 10}
            """).andExpect(status().isOk());

        eventos(mesa.jogador1(), mesa, ficha, "").andExpect(jsonPath("$", hasSize(1)));
        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.estado.atuais.PE").value(10))
            .andExpect(jsonPath("$.atualizadaEm").value("2026-10-16T12:00:00Z"));
    }

    // ---------- Junção ----------

    @Test
    void juncaoDentroDaJanelaMantemOAnteriorOriginal() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patch(mesa.jogador1(), mesa, ficha, "atuais.PV", "8");
        relogio.avancar(Duration.ofSeconds(29));
        patch(mesa.jogador1(), mesa, ficha, "atuais.PV", "7");

        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].anterior").value(10))
            .andExpect(jsonPath("$[0].novo").value(7))
            .andExpect(jsonPath("$[0].em").value("2026-10-16T12:00:29Z"));

        // a janela conta a partir do último "em": mais 29 s ainda junta
        relogio.avancar(Duration.ofSeconds(29));
        patch(mesa.jogador1(), mesa, ficha, "atuais.PV", "6");

        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].anterior").value(10))
            .andExpect(jsonPath("$[0].novo").value(6))
            .andExpect(jsonPath("$[0].em").value("2026-10-16T12:00:58Z"));
    }

    @Test
    void foraDaJanelaCriaOutroEvento() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patch(mesa.jogador1(), mesa, ficha, "atuais.PV", "8");
        relogio.avancar(Duration.ofSeconds(30));
        patch(mesa.jogador1(), mesa, ficha, "atuais.PV", "7");

        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].anterior").value(8))
            .andExpect(jsonPath("$[0].novo").value(7))
            .andExpect(jsonPath("$[0].em").value("2026-10-16T12:00:30Z"))
            .andExpect(jsonPath("$[1].anterior").value(10))
            .andExpect(jsonPath("$[1].novo").value(8));
    }

    @Test
    void campoDiferenteNaoJunta() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patch(mesa.jogador1(), mesa, ficha, "atuais.PV", "8");
        relogio.avancar(Duration.ofSeconds(5));
        patch(mesa.jogador1(), mesa, ficha, "atuais.PE", "8");

        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].campo").value("atuais.PE"))
            .andExpect(jsonPath("$[1].campo").value("atuais.PV"));
    }

    @Test
    void juncaoQueVoltaAoValorOriginalApagaOEvento() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patch(mesa.jogador1(), mesa, ficha, "atuais.PV", "8");
        relogio.avancar(Duration.ofSeconds(5));
        patch(mesa.jogador1(), mesa, ficha, "atuais.PV", "10");

        eventos(mesa.jogador1(), mesa, ficha, "").andExpect(jsonPath("$", hasSize(0)));

        // a próxima mudança começa um evento novo, a partir do valor atual
        relogio.avancar(Duration.ofSeconds(5));
        patch(mesa.jogador1(), mesa, ficha, "atuais.PV", "9");

        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].anterior").value(10))
            .andExpect(jsonPath("$[0].novo").value(9));
    }

    @Test
    void autorDiferenteNaoJunta() throws Exception {
        // Pelas regras atuais só o dono edita a ficha; o outro autor entra direto pelo serviço de log.
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        patch(mesa.jogador1(), mesa, ficha, "atuais.PV", "8");

        relogio.avancar(Duration.ofSeconds(5));
        transactionTemplate.executeWithoutResult(tx -> {
            Ficha entidade = fichaRepository.findById(UUID.fromString(ficha)).orElseThrow();
            Usuario mestre = usuarioRepository.findByEmail("mestre@email.com").orElseThrow();
            fichaEventoService.registrar(entidade, mestre,
                List.of(new FichaPatchAplicador.AlteracaoAplicada("atuais.PV", 8, 5)), relogio.instant());
        });

        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].autorNome").value("Mestre"))
            .andExpect(jsonPath("$[0].anterior").value(8))
            .andExpect(jsonPath("$[0].novo").value(5))
            .andExpect(jsonPath("$[1].autorNome").value("Ana"));

        // o último evento do campo agora é de outro autor: a Ana também não junta com ele
        relogio.avancar(Duration.ofSeconds(5));
        patch(mesa.jogador1(), mesa, ficha, "atuais.PV", "4");

        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].autorNome").value("Ana"))
            .andExpect(jsonPath("$[0].anterior").value(8))
            .andExpect(jsonPath("$[0].novo").value(4));
    }

    @Test
    void mestreEditandoNpcFicaComoAutor() throws Exception {
        Mesa mesa = montarMesa();
        String npc = criarFicha(mesa.mestre(), mesa.campanhaId(), TipoFicha.NPC, "Conde");

        patch(mesa.mestre(), mesa, npc, "atuais.PV", "3").andExpect(status().isOk());

        eventos(mesa.mestre(), mesa, npc, "")
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].autorNome").value("Mestre"));
    }

    // ---------- Raça e Olfato ----------

    @Test
    void trocaDeRacaNaoJuntaMesmoDentroDaJanela() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patch(mesa.jogador1(), mesa, ficha, "raca", "\"BRUXO\"").andExpect(status().isOk());
        relogio.avancar(Duration.ofSeconds(5));
        patch(mesa.jogador1(), mesa, ficha, "raca", "\"HUMANO\"").andExpect(status().isOk());

        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].campo").value("raca"))
            .andExpect(jsonPath("$[0].anterior").value("BRUXO"))
            .andExpect(jsonPath("$[0].novo").value("HUMANO"))
            .andExpect(jsonPath("$[0].autorNome").value("Ana"))
            .andExpect(jsonPath("$[1].anterior").value("HUMANO"))
            .andExpect(jsonPath("$[1].novo").value("BRUXO"));
    }

    @Test
    void deixarDeSerLobisomemZeraOlfatoERegistraNoHistorico() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Lobo");
        patchOps(mesa.jogador1(), mesa, ficha, """
            {"campo": "raca", "valor": "LOBISOMEM"}, {"campo": "pericias.Olfato.ganho", "valor": 20}
            """).andExpect(status().isOk());
        // ganhoTreino não é editável pelo PATCH: prepara direto no banco
        transactionTemplate.executeWithoutResult(tx -> fichaRepository.findById(UUID.fromString(ficha)).orElseThrow()
            .getPericias().stream().filter(p -> p.getNome().equals("Olfato")).findFirst().orElseThrow()
            .setGanhoTreino(3));

        relogio.avancar(Duration.ofSeconds(60));
        patch(mesa.jogador1(), mesa, ficha, "raca", "\"BRUXO\"").andExpect(status().isOk())
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Olfato')].ganho").value(0))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Olfato')].ganhoTreino").value(0));

        eventos(mesa.jogador1(), mesa, ficha, "?antes=2026-10-16T12:00:30Z")
            .andExpect(jsonPath("$[?(@.campo == 'pericias.Olfato.ganho')].novo").value(20));
        eventos(mesa.jogador1(), mesa, ficha, "?limite=3")
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[?(@.campo == 'raca')].anterior").value("LOBISOMEM"))
            .andExpect(jsonPath("$[?(@.campo == 'raca')].novo").value("BRUXO"))
            .andExpect(jsonPath("$[?(@.campo == 'pericias.Olfato.ganho')].anterior").value(20))
            .andExpect(jsonPath("$[?(@.campo == 'pericias.Olfato.ganho')].novo").value(0))
            .andExpect(jsonPath("$[?(@.campo == 'pericias.Olfato.ganhoTreino')].anterior").value(3))
            .andExpect(jsonPath("$[?(@.campo == 'pericias.Olfato.ganhoTreino')].novo").value(0));
    }

    @Test
    void trocarDeRacaSemOlfatoNaoGeraEventoDeOlfato() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patch(mesa.jogador1(), mesa, ficha, "raca", "\"BRUXO\"").andExpect(status().isOk());

        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].campo").value("raca"));
    }

    // ---------- Exceções (overrides) e alcateia ----------

    @Test
    void removerExcecaoGeraEventoComNovoNull() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patch(mesa.jogador1(), mesa, ficha, "overrides.PV", "15").andExpect(status().isOk());
        relogio.avancar(Duration.ofSeconds(60));
        patch(mesa.jogador1(), mesa, ficha, "overrides.PV", "null").andExpect(status().isOk());

        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].campo").value("overrides.PV"))
            .andExpect(jsonPath("$[0].anterior").value(15))
            .andExpect(jsonPath("$[0].novo").doesNotExist())
            .andExpect(jsonPath("$[1].anterior").doesNotExist())
            .andExpect(jsonPath("$[1].novo").value(15));
    }

    @Test
    void removerExcecaoQueNaoExisteNaoGeraEvento() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        relogio.avancar(Duration.ofSeconds(10));

        patch(mesa.jogador1(), mesa, ficha, "regenOverrides.PE", "null").andExpect(status().isOk());

        eventos(mesa.jogador1(), mesa, ficha, "").andExpect(jsonPath("$", hasSize(0)));
        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.atualizadaEm").value("2026-10-16T12:00:00Z"));
    }

    @Test
    void criarERemoverExcecaoDentroDaJanelaApagaOEvento() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patch(mesa.jogador1(), mesa, ficha, "overrides.MOV", "8");
        relogio.avancar(Duration.ofSeconds(5));
        patch(mesa.jogador1(), mesa, ficha, "overrides.MOV", "9");

        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].anterior").doesNotExist())
            .andExpect(jsonPath("$[0].novo").value(9));

        relogio.avancar(Duration.ofSeconds(5));
        patch(mesa.jogador1(), mesa, ficha, "overrides.MOV", "null");

        eventos(mesa.jogador1(), mesa, ficha, "").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void camposDaAlcateiaEntramNoLog() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patchOps(mesa.jogador1(), mesa, ficha, """
            {"campo": "alcateia.ativa", "valor": true}, {"campo": "alcateia.papel", "valor": "alfa"},
            {"campo": "alcateia.linhagem", "valor": "normal"}, {"campo": "abates.vampiros", "valor": 2}
            """).andExpect(status().isOk());

        // linhagem já era "normal": não gera evento
        eventos(mesa.jogador1(), mesa, ficha, "")
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].campo").value("abates.vampiros"))
            .andExpect(jsonPath("$[1].campo").value("alcateia.ativa"))
            .andExpect(jsonPath("$[1].anterior").value(false))
            .andExpect(jsonPath("$[1].novo").value(true))
            .andExpect(jsonPath("$[2].campo").value("alcateia.papel"))
            .andExpect(jsonPath("$[2].anterior").value("membro"))
            .andExpect(jsonPath("$[2].novo").value("alfa"));
    }

    // ---------- Atomicidade ----------

    @Test
    void patchInvalidoOuProibidoNaoDeixaEvento() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patchOps(mesa.jogador1(), mesa, ficha, """
            {"campo": "atuais.PV", "valor": 7}, {"campo": "contadores.sangria", "valor": 11}
            """).andExpect(status().isBadRequest());
        patch(mesa.mestre(), mesa, ficha, "atuais.PV", "1").andExpect(status().isForbidden());

        eventos(mesa.jogador1(), mesa, ficha, "").andExpect(jsonPath("$", hasSize(0)));
        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.estado.atuais.PV").value(10))
            .andExpect(jsonPath("$.atualizadaEm").value("2026-10-16T12:00:00Z"));
    }

    // ---------- Paginação ----------

    @Test
    void paginacaoComAntesELimite() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        for (int pv = 9; pv >= 5; pv--) {
            relogio.avancar(Duration.ofSeconds(60));
            patch(mesa.jogador1(), mesa, ficha, "atuais.PV", String.valueOf(pv)).andExpect(status().isOk());
        }

        String primeira = eventos(mesa.jogador1(), mesa, ficha, "?limite=2")
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].novo").value(5))
            .andExpect(jsonPath("$[1].novo").value(6))
            .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(primeira, "$[1].em");

        String segunda = eventos(mesa.jogador1(), mesa, ficha, "?limite=2&antes=" + cursor)
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].novo").value(7))
            .andExpect(jsonPath("$[1].novo").value(8))
            .andReturn().getResponse().getContentAsString();
        cursor = JsonPath.read(segunda, "$[1].em");

        eventos(mesa.jogador1(), mesa, ficha, "?limite=2&antes=" + cursor)
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].novo").value(9));

        // sem limite: padrão 50
        eventos(mesa.jogador1(), mesa, ficha, "").andExpect(jsonPath("$", hasSize(5)));
        eventos(mesa.jogador1(), mesa, ficha, "?limite=200").andExpect(status().isOk());
    }

    @Test
    void paginaNaoCortaEventosDoMesmoInstante() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        patch(mesa.jogador1(), mesa, ficha, "atuais.SAN", "90");
        relogio.avancar(Duration.ofSeconds(60));
        patchOps(mesa.jogador1(), mesa, ficha, """
            {"campo": "atuais.PV", "valor": 1}, {"campo": "atuais.PE", "valor": 2}, {"campo": "atuais.PM", "valor": 3}
            """);

        String pagina = eventos(mesa.jogador1(), mesa, ficha, "?limite=2")
            .andExpect(jsonPath("$", hasSize(3)))
            .andReturn().getResponse().getContentAsString();

        eventos(mesa.jogador1(), mesa, ficha, "?limite=2&antes=" + JsonPath.read(pagina, "$[2].em"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].campo").value("atuais.SAN"));
    }

    @Test
    void limiteForaDaFaixaOuAntesInvalidoRetorna400() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        eventos(mesa.jogador1(), mesa, ficha, "?limite=0").andExpect(status().isBadRequest());
        eventos(mesa.jogador1(), mesa, ficha, "?limite=201").andExpect(status().isBadRequest());
        eventos(mesa.jogador1(), mesa, ficha, "?limite=abc").andExpect(status().isBadRequest());
        eventos(mesa.jogador1(), mesa, ficha, "?antes=ontem").andExpect(status().isBadRequest());
    }

    // ---------- Permissões ----------

    @Test
    void quemPodeLerOLog() throws Exception {
        Mesa mesa = montarMesa();
        String pj = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        String npc = criarFicha(mesa.mestre(), mesa.campanhaId(), TipoFicha.NPC, "Conde");
        patch(mesa.jogador1(), mesa, pj, "atuais.PV", "8");

        eventos(mesa.jogador1(), mesa, pj, "").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        eventos(mesa.mestre(), mesa, pj, "").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        eventos(mesa.mestre(), mesa, npc, "").andExpect(status().isOk());

        eventos(mesa.jogador2(), mesa, pj, "").andExpect(status().isNotFound());
        eventos(mesa.jogador1(), mesa, npc, "").andExpect(status().isNotFound());

        String outro = registrarELogar("Outro", "outro@email.com");
        eventos(outro, mesa, pj, "").andExpect(status().isNotFound());
    }

    @Test
    void logDeFichaArquivadaOuDeOutraCampanhaRetorna404() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        patch(mesa.jogador1(), mesa, ficha, "atuais.PV", "8");
        String outraCampanha = JsonPath.read(criarCampanha(mesa.jogador1(), "Outra Mesa"), "$.id");

        mockMvc.perform(get("/campanhas/{cid}/fichas/{fid}/eventos", outraCampanha, ficha)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + mesa.jogador1()))
            .andExpect(status().isNotFound());

        deleteFicha(mesa.jogador1(), mesa.campanhaId(), ficha).andExpect(status().isNoContent());
        eventos(mesa.jogador1(), mesa, ficha, "").andExpect(status().isNotFound());
        eventos(mesa.mestre(), mesa, ficha, "").andExpect(status().isNotFound());
    }

    // ---------- Auxiliares ----------

    private ResultActions patch(String token, Mesa mesa, String ficha, String campo, String valorJson) throws Exception {
        return patchOps(token, mesa, ficha, """
            {"campo": "%s", "valor": %s}
            """.formatted(campo, valorJson));
    }

    private ResultActions patchOps(String token, Mesa mesa, String ficha, String ops) throws Exception {
        return patchFicha(token, mesa.campanhaId(), ficha, "{\"ops\": [" + ops + "]}");
    }

    private ResultActions eventos(String token, Mesa mesa, String ficha, String query) throws Exception {
        return mockMvc.perform(get("/campanhas/{cid}/fichas/{fid}/eventos" + query, mesa.campanhaId(), ficha)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
