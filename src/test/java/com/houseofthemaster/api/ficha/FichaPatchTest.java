package com.houseofthemaster.api.ficha;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FichaPatchTest extends FichaTestBase {

    // ---------- Permissões ----------

    @Test
    void jogadorEditaAPropriaFicha() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [
                  {"campo": "nome", "valor": "Ezra Vance"},
                  {"campo": "ocupacao", "valor": null},
                  {"campo": "raca", "valor": "LOBISOMEM"},
                  {"campo": "atributos.DES", "valor": 70},
                  {"campo": "atuais.PV", "valor": 7},
                  {"campo": "estagioSanidade", "valor": "ALERTA_VERMELHO"},
                  {"campo": "contadores.sangria", "valor": 10},
                  {"campo": "contadores.fome", "valor": 2},
                  {"campo": "perdaPermanenteTam", "valor": 3},
                  {"campo": "flags.verbena", "valor": true},
                  {"campo": "condicoes.em-choque", "valor": 2},
                  {"campo": "pericias.Furtividade.ganho", "valor": 20},
                  {"campo": "pericias.Língua Nativa (Inglês).ganho", "valor": 5},
                  {"campo": "pericias.Armas de Fogo (Rifles/Espingardas).ganho", "valor": 1}
                ]}
                """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Ezra Vance"))
            .andExpect(jsonPath("$.estado.atuais.PV").value(7));

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Ezra Vance"))
            .andExpect(jsonPath("$.ocupacao").doesNotExist())
            .andExpect(jsonPath("$.raca").value("LOBISOMEM"))
            .andExpect(jsonPath("$.estado.atributos.DES").value(70))
            .andExpect(jsonPath("$.estado.atributos.FOR").value(50))
            .andExpect(jsonPath("$.estado.atuais.PV").value(7))
            .andExpect(jsonPath("$.estado.estagioSanidade").value("ALERTA_VERMELHO"))
            .andExpect(jsonPath("$.estado.contadores.sangria").value(10))
            .andExpect(jsonPath("$.estado.contadores.fome").value(2))
            .andExpect(jsonPath("$.estado.perdaPermanenteTam").value(3))
            .andExpect(jsonPath("$.estado.flags.verbena").value(true))
            .andExpect(jsonPath("$.estado.condicoes.em-choque").value(2))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Furtividade')].ganho").value(20))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Língua Nativa (Inglês)')].ganho").value(5))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Armas de Fogo (Rifles/Espingardas)')].ganho").value(1))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Furtividade')].ganhoTreino").value(0));
    }

    @Test
    void jogadorNaoEditaFichaDeOutroJogador() throws Exception {
        Mesa mesa = montarMesa();
        String fichaDaBia = criarFicha(mesa.jogador2(), mesa.campanhaId(), TipoFicha.PJ, "Lia");

        patchFicha(mesa.jogador1(), mesa.campanhaId(), fichaDaBia, """
            {"ops": [{"campo": "atuais.PV", "valor": 0}]}
            """).andExpect(status().isNotFound());

        getFicha(mesa.jogador2(), mesa.campanhaId(), fichaDaBia)
            .andExpect(jsonPath("$.estado.atuais.PV").value(10));
    }

    @Test
    void jogadorNaoEditaNpc() throws Exception {
        Mesa mesa = montarMesa();
        String npc = criarFicha(mesa.mestre(), mesa.campanhaId(), TipoFicha.NPC, "Conde");

        patchFicha(mesa.jogador1(), mesa.campanhaId(), npc, """
            {"ops": [{"campo": "nome", "valor": "X"}]}
            """).andExpect(status().isNotFound());
    }

    @Test
    void mestreLePjMasNaoEdita() throws Exception {
        Mesa mesa = montarMesa();
        String pj = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patchFicha(mesa.mestre(), mesa.campanhaId(), pj, """
            {"ops": [{"campo": "atuais.PV", "valor": 1}]}
            """).andExpect(status().isForbidden());

        // 403 vem antes da validação: mesmo com op inválida a resposta é 403.
        patchFicha(mesa.mestre(), mesa.campanhaId(), pj, """
            {"ops": [{"campo": "inexistente", "valor": 1}]}
            """).andExpect(status().isForbidden());

        getFicha(mesa.mestre(), mesa.campanhaId(), pj)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado.atuais.PV").value(10));
    }

    @Test
    void mestreEditaNpc() throws Exception {
        Mesa mesa = montarMesa();
        String npc = criarFicha(mesa.mestre(), mesa.campanhaId(), TipoFicha.NPC, "Conde");

        patchFicha(mesa.mestre(), mesa.campanhaId(), npc, """
                {"ops": [{"campo": "atuais.PV", "valor": 3}, {"campo": "pericias.Esquivar.ganho", "valor": 15}]}
                """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado.atuais.PV").value(3))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Esquivar')].ganho").value(15));
    }

    // ---------- Lista permitida e validação ----------

    @ParameterizedTest(name = "[{index}] {0} = {1}")
    @CsvSource(delimiter = '|', textBlock = """
        # campos fora da lista permitida
        pericias.Furtividade.valorInicial  | 5
        pericias.Furtividade.ganhoTreino   | 5
        pericias.Furtividade               | 5
        pericias..ganho                    | 5
        alcateia.ativa                     | true
        alcateia                           | 1
        abates.vampiros                    | 1
        overrides.FOR                      | 1
        regenOverrides.PV                  | 1
        atributos.XYZ                      | 50
        atributos                          | 50
        atuais.PVMAX                       | 10
        contadores.sono                    | 1
        flags.outra                        | true
        id                                 | 1
        tipo                               | "NPC"
        arquivada                          | true
        estado                             | 1
        # tipos e faixas
        atributos.FOR                      | -1
        atributos.FOR                      | 50.5
        atributos.FOR                      | "50"
        atributos.FOR                      | null
        atributos.FOR                      | 99999999999
        atuais.PV                          | -3
        contadores.sangria                 | 11
        contadores.fome                    | -1
        perdaPermanenteTam                 | -1
        flags.verbena                      | 1
        flags.verbena                      | "true"
        estagioSanidade                    | "CALMO"
        estagioSanidade                    | null
        raca                               | "ELFO"
        raca                               | 3
        nome                               | ""
        nome                               | null
        nome                               | 12
        ocupacao                           | 12
        # condições: formato da chave e valor
        condicoes.Em-Choque                | 1
        condicoes.em choque                | 1
        condicoes.em--choque               | 1
        condicoes.-em-choque               | 1
        condicoes.em_choque                | 1
        condicoes.                         | 1
        condicoes.em-choque                | -1
        condicoes.em-choque                | true
        # perícia inexistente
        pericias.Atletismo.ganho           | 5
        pericias.Furtividade.ganho         | -1
        """)
    void opInvalidaRetorna400(String campo, String valorJson) throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");
        // valorJson já é JSON: strings vêm com aspas duplas (a aspa simples é a de citação do CSV)
        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "%s", "valor": %s}]}
                """.formatted(campo, valorJson))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("ops[0]")));
    }

    @Test
    void chaveDeCondicaoComMaisDe64CaracteresRetorna400() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
            {"ops": [{"campo": "condicoes.%s", "valor": 1}]}
            """.formatted("a".repeat(65))).andExpect(status().isBadRequest());

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
            {"ops": [{"campo": "condicoes.%s", "valor": 1}]}
            """.formatted("a".repeat(64))).andExpect(status().isOk());
    }

    @Test
    void valorAusenteOuOpsVaziasRetorna400() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
            {"ops": [{"campo": "atuais.PV"}]}
            """).andExpect(status().isBadRequest());
        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
            {"ops": []}
            """).andExpect(status().isBadRequest());
        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
            {}
            """).andExpect(status().isBadRequest());
        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
            {"ops": [{"valor": 1}]}
            """).andExpect(status().isBadRequest());
    }

    @Test
    void limitesValidosSaoAceitos() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        // atual acima do "máximo" não é validado no backend (o máximo é calculado no frontend)
        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [
                  {"campo": "contadores.sangria", "valor": 0},
                  {"campo": "atuais.PV", "valor": 999},
                  {"campo": "atributos.FOR", "valor": 0},
                  {"campo": "raca", "valor": null}
                ]}
                """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado.atuais.PV").value(999))
            .andExpect(jsonPath("$.raca").doesNotExist());
    }

    // ---------- Atomicidade ----------

    @Test
    void opInvalidaNoMeioNaoAplicaNenhuma() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [
                  {"campo": "atuais.PV", "valor": 7},
                  {"campo": "pericias.Furtividade.ganho", "valor": 20},
                  {"campo": "contadores.sangria", "valor": 11},
                  {"campo": "nome", "valor": "Outro Nome"}
                ]}
                """)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("ops[2]")))
            .andExpect(content().string(containsString("contadores.sangria")));

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.nome").value("Ezra"))
            .andExpect(jsonPath("$.estado.atuais.PV").value(10))
            .andExpect(jsonPath("$.estado.contadores.sangria").value(0))
            .andExpect(jsonPath("$.pericias[?(@.nome == 'Furtividade')].ganho").value(0));
    }

    @Test
    void campoForaDaListaNoMeioNaoAplicaNenhuma() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [
                  {"campo": "atributos.FOR", "valor": 80},
                  {"campo": "pericias.Furtividade.valorInicial", "valor": 90}
                ]}
                """)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("campo não permitido")));

        getFicha(mesa.jogador1(), mesa.campanhaId(), ficha)
            .andExpect(jsonPath("$.estado.atributos.FOR").value(50));
    }

    @Test
    void opsRepetidasNoMesmoCampoAUltimaVence() throws Exception {
        Mesa mesa = montarMesa();
        String ficha = criarFicha(mesa.jogador1(), mesa.campanhaId(), TipoFicha.PJ, "Ezra");

        patchFicha(mesa.jogador1(), mesa.campanhaId(), ficha, """
                {"ops": [{"campo": "atuais.PV", "valor": 8}, {"campo": "atuais.PV", "valor": 6}]}
                """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado.atuais.PV").value(6));
    }
}
