package com.houseofthemaster.api.ficha;

import com.houseofthemaster.api.sistema.CatalogoSistema;
import com.houseofthemaster.api.sistema.PericiaCatalogo;
import com.houseofthemaster.api.sistema.ValorInicial;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regras da conclusão da criação de personagem (PROMPT-CRIACAO.md, seção 4). Só valida e calcula:
 * não altera a ficha. Qualquer regra violada lança IllegalArgumentException (400) com mensagem em texto.
 *
 * Os orçamentos e os valores iniciais (inclusive as fórmulas Esquivar = DES/2 e Língua Nativa = EDU)
 * são calculados aqui, com os atributos recebidos; números do cliente não são usados.
 *
 * VAMPIRO: FOR e DES são fixas em 120 e ficam fora da distribuição (os outros 6 somam 345); no
 * orçamento de profissão elas valem 57,5; a Esquivar começa em 60, sem depender da DES.
 */
@Component
public class CriacaoValidador {

    // Regras de jogo da criação (War If).
    static final int ATRIBUTO_MINIMO = 15;
    static final int ATRIBUTO_MAXIMO = 90;
    static final int SOMA_ATRIBUTOS = 460;
    /** Vampiro: soma dos 6 atributos distribuídos (sem FOR e DES) = 460 - 2 x 57,5. */
    static final int SOMA_ATRIBUTOS_VAMPIRO = 345;
    /**
     * Quanto FOR e DES do Vampiro valem no orçamento de profissão (no lugar dos 120 gravados). É a média
     * que o Vampiro "deixa de distribuir" nesses dois atributos: 460 - 345 = 115 = 2 x 57,5. Assim, principal
     * FOR ou DES rende 57,5 x 2 = 115 pontos, e não 240, o que deixaria o Vampiro com orçamento inflado.
     */
    static final double FOR_DES_VAMPIRO_NO_ORCAMENTO = 57.5;
    static final int PERICIAS_DA_PROFISSAO = 7;
    static final int VALOR_FINAL_MAXIMO = 80;
    static final List<String> ATRIBUTOS_PRINCIPAIS = List.of("FOR", "DES", "APA", "EDU", "INT");
    static final List<String> ATRIBUTOS_SECUNDARIOS = List.of("EDU", "INT");

    /** Resultado validado, pronto para gravar. */
    public record CriacaoValidada(
            Map<String, Integer> atributos,
            String principal,
            String secundario,
            List<String> periciasProfissao,
            Map<String, Integer> ganhosProfissao,
            Map<String, Integer> ganhosInteresse,
            /** ganho final de cada perícia que recebeu pontos (profissão + interesse). */
            Map<String, Integer> ganhoPorPericia
    ) {
    }

    private final Map<String, PericiaCatalogo> catalogo = new LinkedHashMap<>();

    public CriacaoValidador(CatalogoSistema catalogoSistema) {
        catalogoSistema.pericias().forEach(p -> catalogo.put(p.nome(), p));
    }

    public CriacaoValidada validar(Raca raca, ConcluirCriacaoRequest request) {
        if (raca == null) {
            throw erro("Defina a raça da ficha antes de concluir a criação.");
        }

        Map<String, Integer> atributos = validarAtributos(request.getAtributos(), raca);

        ConcluirCriacaoRequest.Profissao profissao = request.getProfissao();
        if (profissao == null) {
            throw erro("Informe a profissão (principal, secundario e as 7 perícias).");
        }
        if (!ATRIBUTOS_PRINCIPAIS.contains(profissao.getPrincipal())) {
            throw erro("profissao.principal deve ser um de " + ATRIBUTOS_PRINCIPAIS + ".");
        }
        if (!ATRIBUTOS_SECUNDARIOS.contains(profissao.getSecundario())) {
            throw erro("profissao.secundario deve ser um de " + ATRIBUTOS_SECUNDARIOS + ".");
        }
        List<String> periciasProfissao = validarPericiasDaProfissao(profissao.getPericias(), raca);

        ConcluirCriacaoRequest.Ganhos ganhos = request.getGanhos();
        Map<String, Integer> ganhosProfissao = pontos(ganhos == null ? null : ganhos.getProfissao(),
            "ganhos.profissao", raca);
        Map<String, Integer> ganhosInteresse = pontos(ganhos == null ? null : ganhos.getInteresse(),
            "ganhos.interesse", raca);
        for (String nome : ganhosProfissao.keySet()) {
            if (!periciasProfissao.contains(nome)) {
                throw erro("ganhos.profissao: \"" + nome + "\" não está entre as 7 perícias da profissão.");
            }
        }

        // Valor final de cada perícia que recebeu pontos.
        Map<String, Integer> ganhoPorPericia = new LinkedHashMap<>();
        ganhosProfissao.forEach((nome, pts) -> ganhoPorPericia.merge(nome, pts, Integer::sum));
        ganhosInteresse.forEach((nome, pts) -> ganhoPorPericia.merge(nome, pts, Integer::sum));
        Map<String, Integer> valorFinal = new HashMap<>();
        for (PericiaCatalogo pericia : catalogo.values()) {
            int inicial = valorInicial(pericia.valorInicial(), atributos, raca);
            int pontos = ganhoPorPericia.getOrDefault(pericia.nome(), 0);
            if (pontos > 0 && inicial + pontos > VALOR_FINAL_MAXIMO) {
                throw erro(pericia.nome() + ": valor final " + (inicial + pontos) + " passa de " + VALOR_FINAL_MAXIMO
                    + " (inicial " + inicial + " + " + pontos + " pontos).");
            }
            valorFinal.put(pericia.nome(), inicial + pontos);
        }

        // Orçamentos (calculados aqui, com os atributos recebidos).
        // Exato: os valores são inteiros ou 57,5, e cada um é multiplicado por 2.
        int orcamentoProfissao = (int) (valorNoOrcamento(profissao.getPrincipal(), atributos, raca) * 2
            + valorNoOrcamento(profissao.getSecundario(), atributos, raca) * 2);
        int orcamentoInteresse = atributos.get("INT") * 2;
        int gastoProfissao = soma(ganhosProfissao);
        int gastoInteresse = soma(ganhosInteresse);
        if (gastoProfissao > orcamentoProfissao) {
            throw erro("ganhos.profissao: " + gastoProfissao + " pontos gastos, mas o orçamento de profissão é "
                + orcamentoProfissao + ".");
        }
        if (gastoInteresse > orcamentoInteresse) {
            throw erro("ganhos.interesse: " + gastoInteresse + " pontos gastos, mas o orçamento de interesse é "
                + orcamentoInteresse + ".");
        }

        // Sobra só é aceita quando o teto de 80 impede de colocar o resto em qualquer perícia elegível.
        exigirSobraInevitavel("profissão", orcamentoProfissao - gastoProfissao, periciasProfissao, valorFinal);
        List<String> elegiveisInteresse = catalogo.keySet().stream()
            .filter(nome -> elegivel(nome, raca))
            .toList();
        exigirSobraInevitavel("interesse", orcamentoInteresse - gastoInteresse, elegiveisInteresse, valorFinal);

        return new CriacaoValidada(atributos, profissao.getPrincipal(), profissao.getSecundario(),
            periciasProfissao, ganhosProfissao, ganhosInteresse, ganhoPorPericia);
    }

    // ---------- Partes ----------

    /**
     * Os 8 atributos de 15 a 90 com soma 460. VAMPIRO: só os outros 6 (sem FOR e DES), de 15 a 90 com soma
     * 345; FOR e DES são opcionais no corpo e, se vierem, têm de ser 120. Devolve os 8, com FOR = DES = 120
     * no Vampiro.
     */
    private static Map<String, Integer> validarAtributos(Map<String, JsonNode> recebidos, Raca raca) {
        List<String> distribuidos = FichaEstado.ATRIBUTOS.stream()
            .filter(chave -> RegrasRaca.atributoFixo(raca, chave) == null)
            .toList();
        boolean vampiro = distribuidos.size() < FichaEstado.ATRIBUTOS.size();
        if (recebidos == null || !FichaEstado.ATRIBUTOS.containsAll(recebidos.keySet())
                || !recebidos.keySet().containsAll(distribuidos)) {
            throw erro(vampiro
                ? "Informe os 6 atributos do Vampiro: " + String.join(", ", distribuidos)
                    + " (FOR e DES são fixas em " + RegrasRaca.FOR_DES_VAMPIRO + ")."
                : "Informe exatamente os 8 atributos: " + String.join(", ", FichaEstado.ATRIBUTOS) + ".");
        }
        Map<String, Integer> atributos = new LinkedHashMap<>();
        int soma = 0;
        for (String chave : FichaEstado.ATRIBUTOS) {
            Integer fixo = RegrasRaca.atributoFixo(raca, chave);
            JsonNode valor = recebidos.get(chave);
            if (fixo != null) {
                if (valor != null && !(valor.isIntegralNumber() && valor.canConvertToInt() && valor.intValue() == fixo)) {
                    throw erro("atributos." + chave + ": " + RegrasRaca.MENSAGEM_FOR_DES_VAMPIRO);
                }
                atributos.put(chave, fixo);
                continue;
            }
            if (valor == null || !valor.isIntegralNumber() || !valor.canConvertToInt()) {
                throw erro("atributos." + chave + " deve ser um número inteiro.");
            }
            int numero = valor.intValue();
            if (numero < ATRIBUTO_MINIMO || numero > ATRIBUTO_MAXIMO) {
                throw erro("atributos." + chave + " deve estar entre " + ATRIBUTO_MINIMO + " e " + ATRIBUTO_MAXIMO
                    + " (recebido: " + numero + ").");
            }
            atributos.put(chave, numero);
            soma += numero;
        }
        int somaExigida = vampiro ? SOMA_ATRIBUTOS_VAMPIRO : SOMA_ATRIBUTOS;
        if (soma != somaExigida) {
            throw erro(vampiro
                ? "A soma dos 6 atributos do Vampiro (" + String.join(", ", distribuidos) + ") deve ser exatamente "
                    + somaExigida + " (recebido: " + soma + ")."
                : "A soma dos 8 atributos deve ser exatamente " + somaExigida + " (recebido: " + soma + ").");
        }
        return atributos;
    }

    /** Valor do atributo no orçamento de profissão: o gravado, exceto FOR/DES do Vampiro (57,5). */
    private static double valorNoOrcamento(String atributo, Map<String, Integer> atributos, Raca raca) {
        return RegrasRaca.atributoFixo(raca, atributo) != null ? FOR_DES_VAMPIRO_NO_ORCAMENTO : atributos.get(atributo);
    }

    private List<String> validarPericiasDaProfissao(List<String> nomes, Raca raca) {
        if (nomes == null || nomes.size() != PERICIAS_DA_PROFISSAO) {
            throw erro("profissao.pericias: escolha exatamente " + PERICIAS_DA_PROFISSAO + " perícias (recebidas: "
                + (nomes == null ? 0 : nomes.size()) + ").");
        }
        if (new HashSet<>(nomes).size() != nomes.size()) {
            throw erro("profissao.pericias: as 7 perícias devem ser distintas.");
        }
        boolean temInterpessoal = false;
        for (String nome : nomes) {
            PericiaCatalogo pericia = periciaDoCatalogo(nome, "profissao.pericias");
            exigirElegivel(nome, raca, "profissao.pericias");
            temInterpessoal |= pericia.categoria() == CategoriaPericia.INTERPESSOAL;
        }
        if (!temInterpessoal) {
            throw erro("profissao.pericias: escolha ao menos 1 perícia INTERPESSOAL.");
        }
        return List.copyOf(nomes);
    }

    /** Pontos de um bolso: perícias existentes, elegíveis para a raça, inteiros de 1 a 80. */
    private Map<String, Integer> pontos(Map<String, JsonNode> recebidos, String bolso, Raca raca) {
        Map<String, Integer> pontos = new LinkedHashMap<>();
        if (recebidos == null) {
            return pontos;
        }
        recebidos.forEach((nome, valor) -> {
            periciaDoCatalogo(nome, bolso);
            exigirElegivel(nome, raca, bolso);
            // teto de 80 aqui também evita estouro de int nas somas
            if (valor == null || !valor.isIntegralNumber() || !valor.canConvertToInt()
                    || valor.intValue() < 1 || valor.intValue() > VALOR_FINAL_MAXIMO) {
                throw erro(bolso + ": os pontos de \"" + nome + "\" devem ser um número inteiro entre 1 e "
                    + VALOR_FINAL_MAXIMO + ".");
            }
            pontos.put(nome, valor.intValue());
        });
        return pontos;
    }

    private static void exigirSobraInevitavel(String bolso, int sobra, List<String> elegiveis,
                                              Map<String, Integer> valorFinal) {
        if (sobra <= 0) {
            return;
        }
        long espaco = elegiveis.stream()
            .mapToLong(nome -> Math.max(0, VALOR_FINAL_MAXIMO - valorFinal.get(nome)))
            .sum();
        long faltam = Math.min(sobra, espaco);
        if (faltam > 0) {
            throw erro("Faltam gastar " + faltam + " pontos de " + bolso + ": sobram " + sobra
                + " e ainda há perícias elegíveis abaixo de " + VALOR_FINAL_MAXIMO + ".");
        }
    }

    // ---------- Auxiliares ----------

    private PericiaCatalogo periciaDoCatalogo(String nome, String onde) {
        PericiaCatalogo pericia = nome == null ? null : catalogo.get(nome);
        if (pericia == null) {
            throw erro(onde + ": a perícia \"" + nome + "\" não existe no sistema.");
        }
        return pericia;
    }

    private static boolean elegivel(String nome, Raca raca) {
        return !RegrasPericia.PRIMORDIOS.equals(nome) && RegrasPericia.permitidaParaRaca(nome, raca);
    }

    private static void exigirElegivel(String nome, Raca raca, String onde) {
        if (RegrasPericia.PRIMORDIOS.equals(nome)) {
            throw erro(onde + ": Primórdios não pode receber pontos na criação.");
        }
        if (!RegrasPericia.permitidaParaRaca(nome, raca)) {
            throw erro(onde + ": " + RegrasPericia.MENSAGEM_OLFATO);
        }
    }

    /**
     * Valor inicial com os atributos DIGITADOS (Esquivar = DES/2 arredondado para baixo; Língua Nativa = EDU).
     * Vampiro: Esquivar cravada em 60 (RegrasRaca.ESQUIVAR_INICIAL_VAMPIRO), sem depender da DES.
     */
    static int valorInicial(ValorInicial valorInicial, Map<String, Integer> atributos, Raca raca) {
        if (valorInicial.fixo() != null) {
            return valorInicial.fixo();
        }
        return switch (valorInicial.formula()) {
            case DES_METADE -> raca == Raca.VAMPIRO ? RegrasRaca.ESQUIVAR_INICIAL_VAMPIRO : atributos.get("DES") / 2;
            case EDU -> atributos.get("EDU");
        };
    }

    private static int soma(Map<String, Integer> pontos) {
        return pontos.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static IllegalArgumentException erro(String mensagem) {
        return new IllegalArgumentException(mensagem);
    }
}
