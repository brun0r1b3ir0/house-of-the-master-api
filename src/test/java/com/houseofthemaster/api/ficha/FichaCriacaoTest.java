package com.houseofthemaster.api.ficha;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FichaCriacaoTest extends FichaTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    // ---------- estado.criacao na criação da ficha ----------

    @Test
    void pjNovaComecaEmCriacaoENpcNao() throws Exception {
        Mesa mesa = montarMesa();
        String pj = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        String npc = criarFicha(mesa.mestre(), mesa.campanhaId(), TipoFicha.NPC, "Conde");

        getFicha(mesa.jogador1(), mesa.campanhaId(), pj)
            .andExpect(jsonPath("$.estado.criacao.concluida").value(false))
            .andExpect(jsonPath("$.estado.criacao.length()").value(1));
        getFicha(mesa.mestre(), mesa.campanhaId(), npc)
            .andExpect(jsonPath("$.estado.criacao").doesNotExist());
    }

    @Test
    void criacaoNaoEditavelPeloPatch() throws Exception {
        Mesa mesa = montarMesa();
        String pj = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patchFicha(mesa.jogador1(), mesa.campanhaId(), pj, """
            {"ops": [{"campo": "criacao.concluida", "valor": true}]}
            """).andExpect(status().isBadRequest());

        getFicha(mesa.jogador1(), mesa.campanhaId(), pj)
            .andExpect(jsonPath("$.estado.criacao.concluida").value(false));
    }

    // ---------- Conclusão: caminho feliz ----------

    @Test
    void concluiSubstituindoGanhosEGravandoUmEvento() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        // ganhos digitados durante a criação são SUBSTITUÍDOS, não somados
        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
            {"ops": [{"campo": "pericias.Encontrar.ganho", "valor": 10}, {"campo": "pericias.Medicina.ganho", "valor": 5}]}
            """).andExpect(status().isOk());

        concluir(mesa.jogador1(), mesa, ficha, corpo(base()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(ficha))
            .andExpect(jsonPath("$.estado.atributos.DES").value(70))
            .andExpect(jsonPath("$.estado.atributos.INT").value(70))
            .andExpect(jsonPath("$.estado.atributos.EDU").value(60))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Furtividade')].ganho").value(60))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Escutar')].ganho").value(35))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Medicina')].ganho").value(50))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Ocultismo')].ganho").value(70))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Encontrar')].ganho").value(0))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Língua Nativa (Inglês)')].ganho").value(0))
            .andExpect(jsonPath("$.estado.criacao.concluida").value(true))
            .andExpect(jsonPath("$.estado.criacao.profissaoAtributos[0]").value("DES"))
            .andExpect(jsonPath("$.estado.criacao.profissaoAtributos[1]").value("EDU"))
            .andExpect(jsonPath("$.estado.criacao.periciasProfissao", hasSize(7)))
            .andExpect(jsonPath("$.estado.criacao.snapshot.atributos.DES").value(70))
            .andExpect(jsonPath("$.estado.criacao.snapshot.ganhos.profissao.Furtividade").value(60))
            .andExpect(jsonPath("$.estado.criacao.snapshot.ganhos.interesse.Escutar").value(20))
            .andExpect(jsonPath("$.estado.criacao.concluidaEm").exists());

        // o GET devolve o mesmo
        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.estado.criacao.concluida").value(true))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Encontrar')].ganho").value(0));

        // UM evento para a conclusão (os 2 anteriores são do PATCH)
        eventos(mesa.jogador1(), mesa, ficha)
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[?(@.campo == 'criacao.concluida')]", hasSize(1)))
            .andExpect(jsonPath("$[?(@.campo == 'criacao.concluida')].anterior").value(false))
            .andExpect(jsonPath("$[?(@.campo == 'criacao.concluida')].novo").value(true))
            .andExpect(jsonPath("$[?(@.campo == 'criacao.concluida')].autorNome").value("Ana"));

        // só uma vez
        concluir(mesa.jogador1(), mesa, ficha, corpo(base()))
            .andExpect(status().isConflict())
            .andExpect(content().string("Esta ficha não está em criação."));
    }

    @Test
    void sobraAceitaQuandoOTetoDe80ImpedeGastar() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        Map<String, Object> corpo = base();
        // EDU+EDU = 4 x 90 = 360 de profissão; as 7 escolhidas só têm 315 de espaço até 80
        corpo.put("atributos", atributos(50, 50, 50, 90, 50, 40, 40, 90));
        profissao(corpo).put("principal", "EDU");
        profissao(corpo).put("secundario", "EDU");
        profissao(corpo).put("pericias", new ArrayList<>(List.of("Primeiros Socorros", "Encontrar",
            "Armas de Fogo (Rifles/Espingardas)", "Lutar (Briga)", "Esquivar", "Língua Nativa (Inglês)", "Charme")));
        ganhos(corpo).put("profissao", mapa("Primeiros Socorros", 50, "Encontrar", 55,
            "Armas de Fogo (Rifles/Espingardas)", 55, "Lutar (Briga)", 55, "Esquivar", 35, "Charme", 65));
        // Língua Nativa já começa em 90 (EDU): não recebe pontos, e isso não é erro
        ganhos(corpo).put("interesse", mapa("Medicina", 79, "Química", 1));

        concluir(mesa.jogador1(), mesa, ficha, corpo(corpo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Esquivar')].ganho").value(35))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Língua Nativa (Inglês)')].ganho").value(0))
            .andExpect(jsonPath("$.estado.criacao.profissaoAtributos[0]").value("EDU"))
            .andExpect(jsonPath("$.estado.criacao.profissaoAtributos[1]").value("EDU"));
    }

    @Test
    void esquivarUsaMetadeDaDesDigitadaArredondadaParaBaixo() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        Map<String, Object> corpo = base();
        // DES 71: Esquivar inicial 35; orçamento de profissão 71*2 + 60*2 = 262
        corpo.put("atributos", atributos(49, 50, 60, 71, 50, 70, 50, 60));
        ganhos(corpo).put("profissao", mapa("Furtividade", 60, "Esquivar", 46, "Charme", 65, "Lábia", 75, "Escutar", 16));

        concluir(mesa.jogador1(), mesa, ficha, corpo(corpo))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Esquivar: valor final 81")));

        ganhos(corpo).put("profissao", mapa("Furtividade", 60, "Esquivar", 45, "Charme", 65, "Lábia", 75, "Escutar", 17));
        concluir(mesa.jogador1(), mesa, ficha, corpo(corpo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Esquivar')].ganho").value(45));
    }

    @Test
    void lobisomemPodeUsarOlfato() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFichaComRaca(mesa.jogador1(), mesa, "LOBISOMEM");
        Map<String, Object> corpo = base();
        List<String> sete = pericias(corpo);
        sete.set(sete.indexOf("Encontrar"), "Olfato");
        ganhos(corpo).put("interesse", mapa("Escutar", 20, "Medicina", 50, "Olfato", 30, "Ocultismo", 40));

        concluir(mesa.jogador1(), mesa, ficha, corpo(corpo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Olfato')].ganho").value(30));
    }

    // ---------- Conclusão: regras (400) ----------

    static Stream<Arguments> violacoes() {
        return Stream.of(
            caso("atributo abaixo de 15", c -> c.put("atributos", atributos(14, 86, 60, 70, 50, 70, 50, 60)), "entre 15 e 90"),
            caso("atributo acima de 90", c -> c.put("atributos", atributos(91, 50, 19, 70, 50, 70, 50, 60)), "entre 15 e 90"),
            caso("soma diferente de 460", c -> c.put("atributos", atributos(51, 50, 60, 70, 50, 70, 50, 60)), "exatamente 460"),
            caso("atributo faltando", c -> atributosDe(c).remove("EDU"), "exatamente os 8 atributos"),
            caso("atributo a mais", c -> atributosDe(c).put("SOR", 10), "exatamente os 8 atributos"),
            caso("atributo como texto", c -> atributosDe(c).put("FOR", "50"), "atributos.FOR deve ser um número inteiro"),
            caso("atributo decimal", c -> atributosDe(c).put("FOR", 50.5), "atributos.FOR deve ser um número inteiro"),
            caso("sem profissão", c -> c.remove("profissao"), "Informe a profissão"),
            caso("principal inválido", c -> profissao(c).put("principal", "CON"), "profissao.principal"),
            caso("secundário inválido", c -> profissao(c).put("secundario", "DES"), "profissao.secundario"),
            caso("6 perícias", c -> pericias(c).remove("Encontrar"), "exatamente 7"),
            caso("perícia repetida", c -> pericias(c).set(pericias(c).indexOf("Encontrar"), "Escutar"), "distintas"),
            caso("perícia inexistente", c -> pericias(c).set(pericias(c).indexOf("Encontrar"), "Atletismo"), "não existe"),
            caso("sem INTERPESSOAL", c -> {
                pericias(c).set(pericias(c).indexOf("Charme"), "Nadar");
                pericias(c).set(pericias(c).indexOf("Lábia"), "Saltar");
            }, "INTERPESSOAL"),
            caso("Primórdios na profissão", c -> pericias(c).set(pericias(c).indexOf("Encontrar"), "Primórdios"), "Primórdios"),
            caso("Primórdios nos ganhos", c -> interesse(c).put("Primórdios", 1), "Primórdios"),
            caso("Olfato na profissão (HUMANO)", c -> pericias(c).set(pericias(c).indexOf("Encontrar"), "Olfato"), "Lobisomem"),
            caso("Olfato nos ganhos (HUMANO)", c -> interesse(c).put("Olfato", 1), "Lobisomem"),
            caso("ganho de profissão fora das 7", c -> profissaoGanhos(c).put("Medicina", 1), "não está entre as 7"),
            caso("ganho em perícia inexistente", c -> interesse(c).put("Atletismo", 1), "não existe"),
            caso("ponto zero", c -> interesse(c).put("Química", 0), "entre 1 e 80"),
            caso("ponto negativo", c -> interesse(c).put("Química", -3), "entre 1 e 80"),
            caso("ponto acima de 80", c -> interesse(c).put("Química", 81), "entre 1 e 80"),
            caso("ponto como texto", c -> interesse(c).put("Química", "5"), "entre 1 e 80"),
            caso("valor final acima de 80", c -> profissaoGanhos(c).put("Lábia", 76), "Lábia: valor final 81"),
            caso("Língua Nativa usa EDU", c -> interesse(c).put("Língua Nativa (Inglês)", 21), "Língua Nativa (Inglês): valor final 81"),
            caso("profissão acima do orçamento", c -> profissaoGanhos(c).put("Escutar", 16), "orçamento de profissão é 260"),
            caso("interesse acima do orçamento", c -> interesse(c).put("Medicina", 51), "orçamento de interesse é 140"),
            caso("sobra evitável de profissão", c -> profissaoGanhos(c).put("Escutar", 10), "Faltam gastar 5 pontos de profissão"),
            caso("sobra evitável de interesse", c -> interesse(c).put("Medicina", 45), "Faltam gastar 5 pontos de interesse")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("violacoes")
    void regraVioladaRetorna400SemAlterarAFicha(String descricao, Consumer<Map<String, Object>> alterar,
                                                 String mensagem) throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        Map<String, Object> corpo = base();
        alterar.accept(corpo);

        concluir(mesa.jogador1(), mesa, ficha, corpo(corpo))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString(mensagem)));

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.estado.criacao.concluida").value(false))
            .andExpect(jsonPath("$.estado.atributos.DES").value(50))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Furtividade')].ganho").value(0));
        eventos(mesa.jogador1(), mesa, ficha).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void racaNulaRetorna400() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = JsonPath.read(postFicha(mesa.jogador1(), mesa.campanhaId(), """
                {"tipo": "PJ", "nome": "Sem Raça"}
                """).andReturn().getResponse().getContentAsString(), "$.id");

        concluir(mesa.jogador1(), mesa, ficha, corpo(base()))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Defina a raça")));
    }

    // ---------- Conclusão: permissões e estado (404, 403, 409) ----------

    @Test
    void permissoesNaOrdem404403409400() throws Exception {
        Mesa mesa = montarMesa();
        String pj = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        String npc = criarFicha(mesa.mestre(), mesa.campanhaId(), TipoFicha.NPC, "Conde");
        String invalido = "{\"atributos\": {}}";

        concluir(registrarELogar("Outro", "outro@email.com"), mesa, pj, invalido).andExpect(status().isNotFound());
        concluir(mesa.jogador2(), mesa, pj, invalido).andExpect(status().isNotFound());
        concluir(mesa.jogador1(), mesa, npc, invalido).andExpect(status().isNotFound());
        concluir(mesa.mestre(), mesa, pj, invalido).andExpect(status().isForbidden());
        concluir(mesa.mestre(), mesa, pj, corpo(base())).andExpect(status().isForbidden());
        concluir(mesa.mestre(), mesa, npc, corpo(base()))
            .andExpect(status().isConflict())
            .andExpect(content().string("Esta ficha não está em criação."));
        concluir(mesa.jogador1(), mesa, pj, invalido).andExpect(status().isBadRequest());

        getFicha(mesa.jogador1(), mesa.campanhaId(), pj)
            .andExpect(jsonPath("$.estado.criacao.concluida").value(false));
    }

    @Test
    void fichaConcluidaComCorpoInvalidoDa409() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        concluir(mesa.jogador1(), mesa, ficha, corpo(base())).andExpect(status().isOk());

        concluir(mesa.jogador1(), mesa, ficha, "{}").andExpect(status().isConflict());
    }

    @Test
    void fichaArquivadaOuDeOutraCampanhaDa404() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        String outraCampanha = JsonPath.read(criarCampanha(mesa.jogador1(), "Outra Mesa"), "$.id");

        mockMvc.perform(post("/campanhas/{cid}/fichas/{fid}/criacao/concluir", outraCampanha, ficha)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + mesa.jogador1())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(base())))
            .andExpect(status().isNotFound());

        deleteFicha(mesa.jogador1(), mesa.campanhaId(), ficha).andExpect(status().isNoContent());
        concluir(mesa.jogador1(), mesa, ficha, corpo(base())).andExpect(status().isNotFound());
    }

    // ---------- Corpo base válido e auxiliares ----------

    // ---------- Vampiro: FOR/DES fixas em 120, os outros 6 somam 345 ----------

    @Test
    void vampiroConcluiCom6AtributosEGravaForEDes120() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFichaComRaca(mesa.jogador1(), mesa, "VAMPIRO");

        concluir(mesa.jogador1(), mesa, ficha, corpo(baseVampiro()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado.atributos.FOR").value(120))
            .andExpect(jsonPath("$.estado.atributos.DES").value(120))
            .andExpect(jsonPath("$.estado.atributos.CON").value(50))
            .andExpect(jsonPath("$.estado.atributos.INT").value(70))
            .andExpect(jsonPath("$.estado.atributos.EDU").value(60))
            .andExpect(jsonPath("$.estado.criacao.snapshot.atributos.FOR").value(120))
            .andExpect(jsonPath("$.estado.criacao.snapshot.atributos.DES").value(120))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Esquivar')].ganho").value(20))
            .andExpect(jsonPath("$.estado.criacao.concluida").value(true));

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.estado.atributos.FOR").value(120))
            .andExpect(jsonPath("$.estado.atributos.DES").value(120));
    }

    @Test
    void vampiroAceitaForEDesNoCorpoSeForem120() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFichaComRaca(mesa.jogador1(), mesa, "VAMPIRO");
        Map<String, Object> corpo = baseVampiro();
        atributosDe(corpo).put("FOR", 120);
        atributosDe(corpo).put("DES", 120);

        concluir(mesa.jogador1(), mesa, ficha, corpo(corpo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado.atributos.FOR").value(120))
            .andExpect(jsonPath("$.estado.atributos.DES").value(120));
    }

    static Stream<Arguments> vampiroInvalido() {
        return Stream.of(
            caso("soma dos 6 = 344", c -> atributosDe(c).put("POD", 54),
                "A soma dos 6 atributos do Vampiro (CON, TAM, APA, INT, POD, EDU) deve ser exatamente 345 (recebido: 344)."),
            caso("soma dos 6 = 346", c -> atributosDe(c).put("POD", 56),
                "A soma dos 6 atributos do Vampiro (CON, TAM, APA, INT, POD, EDU) deve ser exatamente 345 (recebido: 346)."),
            caso("atributo fora de 15-90", c -> {
                atributosDe(c).put("CON", 91);
                atributosDe(c).put("POD", 14);
            }, "atributos.CON deve estar entre 15 e 90 (recebido: 91)."),
            caso("falta um dos 6", c -> atributosDe(c).remove("APA"),
                "Informe os 6 atributos do Vampiro: CON, TAM, APA, INT, POD, EDU (FOR e DES são fixas em 120)."),
            caso("FOR diferente de 120", c -> atributosDe(c).put("FOR", 90),
                "atributos.FOR: FOR e DES de vampiro são fixas em 120 e não podem ser alteradas."),
            caso("DES diferente de 120", c -> atributosDe(c).put("DES", 50),
                "atributos.DES: FOR e DES de vampiro são fixas em 120 e não podem ser alteradas."),
            caso("os 8 atributos de humano (soma 460)", c -> c.put("atributos", atributos(50, 50, 60, 70, 50, 70, 50, 60)),
                "atributos.FOR: FOR e DES de vampiro são fixas em 120 e não podem ser alteradas."),
            // Esquivar começa em 60 (não DES/2 com a DES digitada): 60 + 21 passa do teto
            caso("Esquivar 60 + 21", c -> {
                profissaoGanhos(c).put("Esquivar", 21);
                profissaoGanhos(c).put("Lábia", 74);
            }, "Esquivar: valor final 81 passa de 80 (inicial 60 + 21 pontos)."),
            // FOR/DES valem 57,5 no orçamento: DES + EDU = 115 + 120 = 235, não 240 + 120
            caso("profissão acima de 235", c -> profissaoGanhos(c).put("Encontrar", 1),
                "ganhos.profissao: 236 pontos gastos, mas o orçamento de profissão é 235.")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vampiroInvalido")
    void vampiroInvalidoRetorna400ENadaMuda(String descricao, Consumer<Map<String, Object>> alterar, String mensagem)
            throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFichaComRaca(mesa.jogador1(), mesa, "VAMPIRO");
        Map<String, Object> corpo = baseVampiro();
        alterar.accept(corpo);

        concluir(mesa.jogador1(), mesa, ficha, corpo(corpo))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString(mensagem)));

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.estado.criacao.concluida").value(false))
            .andExpect(jsonPath("$.estado.atributos.FOR").value(120))
            .andExpect(jsonPath("$.estado.atributos.DES").value(120));
    }

    @Test
    void vampiroComPrincipalForTemOrcamentoDe115MaisSecundario() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFichaComRaca(mesa.jogador1(), mesa, "VAMPIRO");
        Map<String, Object> corpo = baseVampiro();
        // FOR (57,5 x 2 = 115) + EDU (60 x 2 = 120) = 235: o mesmo orçamento de DES + EDU
        profissao(corpo).put("principal", "FOR");

        concluir(mesa.jogador1(), mesa, ficha, corpo(corpo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado.criacao.profissaoAtributos[0]").value("FOR"));
    }

    /**
     * VAMPIRO; CON 50, TAM 60, APA 50, INT 70, POD 55, EDU 60 (soma 345), sem FOR e DES no corpo.
     * Profissão DES+EDU = 115 + 120 = 235: Furtividade, Esquivar (inicial 60), Charme e Lábia vão a 80;
     * Escutar +15. Interesse INT*2 = 140: Escutar +20, Medicina +50, Ocultismo +70. Tudo gasto.
     */
    private static Map<String, Object> baseVampiro() {
        Map<String, Object> corpo = base();
        corpo.put("atributos", mapa("CON", 50, "TAM", 60, "APA", 50, "INT", 70, "POD", 55, "EDU", 60));
        profissaoGanhos(corpo).put("Esquivar", 20);
        return corpo;
    }

    /**
     * HUMANO; DES 70, INT 70, EDU 60 (soma 460). Profissão DES+EDU = 260 pontos, interesse INT*2 = 140.
     * Profissão: Furtividade, Esquivar, Charme e Lábia vão a 80; Escutar +15. Interesse: Escutar +20,
     * Medicina +50, Ocultismo +70. Os dois bolsos são gastos inteiros.
     */
    private static Map<String, Object> base() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("atributos", atributos(50, 50, 60, 70, 50, 70, 50, 60));
        Map<String, Object> profissao = new LinkedHashMap<>();
        profissao.put("principal", "DES");
        profissao.put("secundario", "EDU");
        profissao.put("pericias", new ArrayList<>(List.of("Furtividade", "Esquivar", "Charme", "Lábia", "Escutar",
            "Encontrar", "Língua Nativa (Inglês)")));
        corpo.put("profissao", profissao);
        Map<String, Object> ganhos = new LinkedHashMap<>();
        ganhos.put("profissao", mapa("Furtividade", 60, "Esquivar", 45, "Charme", 65, "Lábia", 75, "Escutar", 15));
        ganhos.put("interesse", mapa("Escutar", 20, "Medicina", 50, "Ocultismo", 70));
        corpo.put("ganhos", ganhos);
        return corpo;
    }

    private static Map<String, Object> atributos(int forca, int con, int tam, int des, int apa, int inteligencia,
                                                 int pod, int edu) {
        return mapa("FOR", forca, "CON", con, "TAM", tam, "DES", des, "APA", apa, "INT", inteligencia,
            "POD", pod, "EDU", edu);
    }

    private static Map<String, Object> mapa(Object... chavesEValores) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < chavesEValores.length; i += 2) {
            mapa.put((String) chavesEValores[i], chavesEValores[i + 1]);
        }
        return mapa;
    }

    private static Arguments caso(String descricao, Consumer<Map<String, Object>> alterar, String mensagem) {
        return Arguments.of(descricao, alterar, mensagem);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> atributosDe(Map<String, Object> corpo) {
        return (Map<String, Object>) corpo.get("atributos");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> profissao(Map<String, Object> corpo) {
        return (Map<String, Object>) corpo.get("profissao");
    }

    @SuppressWarnings("unchecked")
    private static List<String> pericias(Map<String, Object> corpo) {
        return (List<String>) profissao(corpo).get("pericias");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ganhos(Map<String, Object> corpo) {
        return (Map<String, Object>) corpo.get("ganhos");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> profissaoGanhos(Map<String, Object> corpo) {
        return (Map<String, Object>) ganhos(corpo).get("profissao");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> interesse(Map<String, Object> corpo) {
        return (Map<String, Object>) ganhos(corpo).get("interesse");
    }

    private static String corpo(Map<String, Object> corpo) {
        return JSON.writeValueAsString(corpo);
    }

    private String criarFichaComRaca(String token, Mesa mesa, String raca) throws Exception {
        String resposta = postFicha(token, mesa.campanhaId(), """
                {"tipo": "PJ", "nome": "Lobo", "raca": "%s"}
                """.formatted(raca))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resposta, "$.id");
    }

    private ResultActions concluir(String token, Mesa mesa, String ficha, String json) throws Exception {
        return mockMvc.perform(post("/campanhas/{cid}/fichas/{fid}/criacao/concluir", mesa.campanhaId(), ficha)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
    }

    private ResultActions eventos(String token, Mesa mesa, String ficha) throws Exception {
        return mockMvc.perform(get("/campanhas/{cid}/fichas/{fid}/eventos", mesa.campanhaId(), ficha)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
