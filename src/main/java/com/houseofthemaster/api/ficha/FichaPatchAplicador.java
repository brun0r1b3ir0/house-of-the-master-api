package com.houseofthemaster.api.ficha;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Valida e aplica as operações do PATCH de ficha (lista permitida da Etapa 4).
 *
 * Tetos técnicos em FichaLimites. Atômico em duas fases: validar() confere TODAS as ops sem tocar na ficha (qualquer op inválida
 * lança IllegalArgumentException = 400); só depois aplicar() escreve. Faixas validadas aqui são só as
 * básicas: "atual <= máximo" é responsabilidade do frontend (o máximo depende do cálculo).
 *
 * Fora da lista (400): valorInicial, ganhoTreino e qualquer campo não listado em MODELO-FICHA.md.
 * Em overrides e regenOverrides, valor null REMOVE a exceção (apaga a chave).
 */
@Component
public class FichaPatchAplicador {

    static final Pattern CHAVE_CONDICAO = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private static final String PREFIXO_PERICIAS = "pericias.";
    private static final String SUFIXO_GANHO = ".ganho";

    /** Onde uma op lê e escreve na ficha. Valores no formato do log: Integer, String, Boolean ou null. */
    interface Alvo {
        Object ler(Ficha ficha);

        void escrever(Ficha ficha, Object valor);
    }

    /** Op já validada, pronta para aplicar. */
    record Alteracao(String campo, Alvo alvo, Object novo) {
    }

    /** Op que de fato mudou a ficha (valor novo diferente do anterior). */
    public record AlteracaoAplicada(String campo, Object anterior, Object novo) {
    }

    // ---------- Fase 1: validar tudo, sem alterar nada ----------

    /**
     * As ops são avaliadas NA ORDEM, com a raça "corrente" do PATCH: raca -> LOBISOMEM seguida de
     * pericias.Olfato.ganho passa. Trocar a raça para outra que não LOBISOMEM acrescenta, logo após a op
     * de raça, a zeragem de ganho e ganhoTreino de Olfato (registrada no log como qualquer alteração).
     * raca null, "" ou só espaços vale como HUMANO. VAMPIRO é permanente: sair dele (inclusive para
     * null/"") é 400. Entrar nele (transformação) acrescenta, logo após a op de raça, atributos.FOR = 120 e atributos.DES = 120. Com a raça corrente VAMPIRO,
     * atributos.FOR e atributos.DES só aceitam 120 (no-op); outro valor é 400.
     */
    public List<Alteracao> validar(Ficha ficha, List<OpPatch> ops) {
        List<Alteracao> alteracoes = new ArrayList<>();
        Raca racaCorrente = ficha.getRaca();
        for (int i = 0; i < ops.size(); i++) {
            OpPatch op = ops.get(i);
            Alteracao alteracao;
            try {
                alteracao = validar(ficha, op, racaCorrente);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("ops[" + i + "] (" + op.getCampo() + "): " + e.getMessage());
            }
            if (!"raca".equals(alteracao.campo())) {
                alteracoes.add(alteracao);
                continue;
            }

            Raca nova = Raca.valueOf((String) alteracao.novo());
            if (!RegrasRaca.trocaPermitida(racaCorrente, nova)) {
                throw new IllegalArgumentException(
                    "ops[" + i + "] (" + op.getCampo() + "): " + RegrasRaca.MENSAGEM_VAMPIRO_PERMANENTE);
            }
            alteracoes.add(alteracao);
            if (nova != racaCorrente && nova != Raca.LOBISOMEM) {
                alteracoes.addAll(zerarOlfato(ficha));
            }
            if (RegrasRaca.transformaEmVampiro(racaCorrente, nova)) {
                alteracoes.addAll(transformarEmVampiro());
            }
            racaCorrente = nova;
        }
        return alteracoes;
    }

    /** Na transformação em Vampiro, FOR e DES passam a 120 (em estado.atributos, com eventos no log). */
    private static List<Alteracao> transformarEmVampiro() {
        return FichaEstado.ATRIBUTOS.stream()
            .filter(RegrasRaca.ATRIBUTOS_FIXOS_VAMPIRO::containsKey)
            .map(chave -> new Alteracao("atributos." + chave,
                alvoMapa(FichaEstado::getAtributos, FichaEstado::setAtributos, chave),
                RegrasRaca.ATRIBUTOS_FIXOS_VAMPIRO.get(chave)))
            .toList();
    }

    /** Ao deixar de ser Lobisomem, Olfato volta a 0 (ganho e ganhoTreino). */
    private static List<Alteracao> zerarOlfato(Ficha ficha) {
        return ficha.getPericias().stream()
            .filter(p -> RegrasPericia.OLFATO.equals(p.getNome()))
            .findFirst()
            .map(olfato -> List.of(
                new Alteracao(PREFIXO_PERICIAS + RegrasPericia.OLFATO + SUFIXO_GANHO,
                    alvo(f -> olfato.getGanho(), (f, v) -> olfato.setGanho((Integer) v)), 0),
                new Alteracao(PREFIXO_PERICIAS + RegrasPericia.OLFATO + ".ganhoTreino",
                    alvo(f -> olfato.getGanhoTreino(), (f, v) -> olfato.setGanhoTreino((Integer) v)), 0)))
            .orElse(List.of());
    }

    private Alteracao validar(Ficha ficha, OpPatch op, Raca racaCorrente) {
        String campo = op.getCampo();
        JsonNode valor = op.getValor();
        if (valor == null) {
            throw new IllegalArgumentException("valor é obrigatório.");
        }

        switch (campo) {
            case "nome":
                return new Alteracao(campo,
                    alvo(Ficha::getNome, (f, v) -> f.setNome((String) v)),
                    texto(valor, false));
            case "ocupacao":
                return new Alteracao(campo,
                    alvo(Ficha::getOcupacao, (f, v) -> f.setOcupacao((String) v)),
                    texto(valor, true));
            case "raca":
                // Não existe ficha sem raça: null, "" ou só espaços valem como HUMANO.
                return new Alteracao(campo,
                    alvo(f -> nomeOuNull(f.getRaca()), (f, v) -> f.setRaca(Raca.valueOf((String) v))),
                    valor.isNull() || (valor.isString() && valor.stringValue().isBlank())
                        ? Raca.HUMANO.name()
                        : enumeracao(valor, Raca.class, false));
            case "estagioSanidade":
                return new Alteracao(campo,
                    alvoEstado(e -> nomeOuNull(e.getEstagioSanidade()),
                        (e, v) -> e.setEstagioSanidade(EstagioSanidade.valueOf((String) v))),
                    enumeracao(valor, EstagioSanidade.class, false));
            case "perdaPermanenteTam":
                return new Alteracao(campo,
                    alvoEstado(FichaEstado::getPerdaPermanenteTam, (e, v) -> e.setPerdaPermanenteTam((Integer) v)),
                    inteiro(valor, FichaLimites.VALOR_MAXIMO));
            default:
                break;
        }

        int ponto = campo.indexOf('.');
        String grupo = ponto < 0 ? campo : campo.substring(0, ponto);
        String chave = ponto < 0 ? "" : campo.substring(ponto + 1);

        return switch (grupo) {
            case "atributos" -> validarAtributo(campo, chave, valor, racaCorrente);
            case "atuais" -> new Alteracao(campo,
                alvoMapa(FichaEstado::getAtuais, FichaEstado::setAtuais, chaveConhecida(chave, FichaEstado.ATUAIS)),
                inteiro(valor, FichaLimites.VALOR_MAXIMO));
            case "contadores" -> new Alteracao(campo,
                alvoMapa(FichaEstado::getContadores, FichaEstado::setContadores, chaveConhecida(chave, FichaEstado.CONTADORES)),
                inteiro(valor, FichaLimites.maximoDoContador(chave)));
            case "flags" -> new Alteracao(campo,
                alvoMapa(FichaEstado::getFlags, FichaEstado::setFlags, chaveConhecida(chave, FichaEstado.FLAGS)),
                booleano(valor));
            case "condicoes" -> new Alteracao(campo,
                alvoMapa(FichaEstado::getCondicoes, FichaEstado::setCondicoes, chaveDeCondicao(chave)),
                inteiro(valor, FichaLimites.VALOR_MAXIMO));
            case "abates" -> new Alteracao(campo,
                alvoMapa(FichaEstado::getAbates, FichaEstado::setAbates, chaveConhecida(chave, FichaEstado.ABATES)),
                inteiro(valor, FichaLimites.VALOR_MAXIMO));
            case "overrides" -> new Alteracao(campo,
                alvoMapaRemovivel(FichaEstado::getOverrides, FichaEstado::setOverrides,
                    chaveConhecida(chave, FichaEstado.OVERRIDES)),
                inteiroOuNulo(valor, FichaLimites.VALOR_MAXIMO));
            case "regenOverrides" -> new Alteracao(campo,
                alvoMapaRemovivel(FichaEstado::getRegenOverrides, FichaEstado::setRegenOverrides,
                    chaveConhecida(chave, FichaEstado.REGEN_OVERRIDES)),
                inteiroOuNulo(valor, FichaLimites.REGEN_OVERRIDE_MAXIMO));
            case "alcateia" -> validarAlcateia(campo, chave, valor);
            case "pericias" -> validarPericia(ficha, campo, valor, racaCorrente);
            default -> throw naoPermitido();
        };
    }

    /** FOR/DES de Vampiro (raça corrente do PATCH) são fixas: só 120 é aceito, e não muda nada. */
    private static Alteracao validarAtributo(String campo, String chave, JsonNode valor, Raca racaCorrente) {
        chaveConhecida(chave, FichaEstado.ATRIBUTOS);
        Integer numero = inteiro(valor, FichaLimites.VALOR_MAXIMO);
        Integer fixo = RegrasRaca.atributoFixo(racaCorrente, chave);
        if (fixo != null && !fixo.equals(numero)) {
            throw new IllegalArgumentException(RegrasRaca.MENSAGEM_FOR_DES_VAMPIRO);
        }
        return new Alteracao(campo, alvoMapa(FichaEstado::getAtributos, FichaEstado::setAtributos, chave), numero);
    }

    private static Alteracao validarAlcateia(String campo, String chave, JsonNode valor) {
        return switch (chave) {
            case "ativa" -> new Alteracao(campo,
                alvoAlcateia(FichaEstado.Alcateia::isAtiva, (a, v) -> a.setAtiva((Boolean) v)),
                booleano(valor));
            case "membros" -> new Alteracao(campo,
                alvoAlcateia(FichaEstado.Alcateia::getMembros, (a, v) -> a.setMembros((Integer) v)),
                inteiro(valor, FichaLimites.VALOR_MAXIMO));
            case "papel" -> new Alteracao(campo,
                alvoAlcateia(FichaEstado.Alcateia::getPapel, (a, v) -> a.setPapel((String) v)),
                opcao(valor, FichaLimites.PAPEIS_ALCATEIA));
            case "linhagem" -> new Alteracao(campo,
                alvoAlcateia(FichaEstado.Alcateia::getLinhagem, (a, v) -> a.setLinhagem((String) v)),
                opcao(valor, FichaLimites.LINHAGENS_ALCATEIA));
            default -> throw naoPermitido();
        };
    }

    /**
     * Só pericias.<nome>.ganho é permitido; o nome pode conter espaços, parênteses e barras.
     * Olfato só para LOBISOMEM (raça corrente do PATCH).
     */
    private Alteracao validarPericia(Ficha ficha, String campo, JsonNode valor, Raca racaCorrente) {
        if (!campo.startsWith(PREFIXO_PERICIAS) || !campo.endsWith(SUFIXO_GANHO)
                || campo.length() <= PREFIXO_PERICIAS.length() + SUFIXO_GANHO.length()) {
            throw naoPermitido();
        }
        String nome = campo.substring(PREFIXO_PERICIAS.length(), campo.length() - SUFIXO_GANHO.length());
        if (!RegrasPericia.permitidaParaRaca(nome, racaCorrente)) {
            throw new IllegalArgumentException(RegrasPericia.MENSAGEM_OLFATO);
        }

        FichaPericia pericia = ficha.getPericias().stream()
            .filter(p -> p.getNome().equals(nome))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("a ficha não tem a perícia \"" + nome + "\"."));

        return new Alteracao(campo,
            alvo(f -> pericia.getGanho(), (f, v) -> pericia.setGanho((Integer) v)),
            inteiro(valor, FichaLimites.VALOR_MAXIMO));
    }

    // ---------- Fase 2: aplicar ----------

    /**
     * Aplica as alterações já validadas, em ordem, e devolve o saldo por campo: do valor original ao
     * final. Ops repetidas no mesmo campo viram uma alteração só; campo que termina igual ao original
     * não aparece (nem gera evento, nem avança atualizadaEm).
     */
    public List<AlteracaoAplicada> aplicar(Ficha ficha, List<Alteracao> alteracoes) {
        Map<String, Object> originais = new LinkedHashMap<>();
        Map<String, Object> finais = new LinkedHashMap<>();
        for (Alteracao alteracao : alteracoes) {
            Object atual = alteracao.alvo().ler(ficha);
            originais.putIfAbsent(alteracao.campo(), atual);
            if (!Objects.equals(atual, alteracao.novo())) {
                alteracao.alvo().escrever(ficha, alteracao.novo());
            }
            finais.put(alteracao.campo(), alteracao.novo());
        }

        List<AlteracaoAplicada> saldo = new ArrayList<>();
        originais.forEach((campo, original) -> {
            Object fim = finais.get(campo);
            if (!Objects.equals(original, fim)) {
                saldo.add(new AlteracaoAplicada(campo, original, fim));
            }
        });
        return saldo;
    }

    // ---------- Valores ----------

    private static Integer inteiro(JsonNode valor, int maximo) {
        if (!valor.isIntegralNumber() || !valor.canConvertToInt()) {
            throw new IllegalArgumentException("deve ser um número inteiro.");
        }
        int numero = valor.intValue();
        if (numero < 0 || numero > maximo) {
            throw new IllegalArgumentException("deve estar entre 0 e " + maximo + ".");
        }
        return numero;
    }

    /** Inteiro na faixa, ou null (usado para remover uma exceção). */
    private static Integer inteiroOuNulo(JsonNode valor, int maximo) {
        if (valor.isNull()) {
            return null;
        }
        try {
            return inteiro(valor, maximo);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage() + " Use null para remover a exceção.");
        }
    }

    private static String opcao(JsonNode valor, List<String> permitidas) {
        if (!valor.isString() || !permitidas.contains(valor.stringValue())) {
            throw new IllegalArgumentException("deve ser um de " + permitidas + ".");
        }
        return valor.stringValue();
    }

    private static Boolean booleano(JsonNode valor) {
        if (!valor.isBoolean()) {
            throw new IllegalArgumentException("deve ser true ou false.");
        }
        return valor.booleanValue();
    }

    private static String texto(JsonNode valor, boolean aceitaNulo) {
        if (valor.isNull() && aceitaNulo) {
            return null;
        }
        if (!valor.isString()) {
            throw new IllegalArgumentException(aceitaNulo ? "deve ser um texto ou null." : "deve ser um texto.");
        }
        String texto = valor.stringValue();
        if (!aceitaNulo && texto.isBlank()) {
            throw new IllegalArgumentException("não pode ficar em branco.");
        }
        if (texto.length() > FichaLimites.TEXTO_MAXIMO) {
            throw new IllegalArgumentException("deve ter no máximo " + FichaLimites.TEXTO_MAXIMO + " caracteres.");
        }
        return texto;
    }

    private static <E extends Enum<E>> String enumeracao(JsonNode valor, Class<E> tipo, boolean aceitaNulo) {
        if (valor.isNull() && aceitaNulo) {
            return null;
        }
        String permitidos = Arrays.toString(tipo.getEnumConstants());
        if (!valor.isString()) {
            throw new IllegalArgumentException("deve ser um de " + permitidos + (aceitaNulo ? " ou null." : "."));
        }
        try {
            return Enum.valueOf(tipo, valor.stringValue()).name();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("deve ser um de " + permitidos + (aceitaNulo ? " ou null." : "."));
        }
    }

    // ---------- Chaves ----------

    private static String chaveConhecida(String chave, List<String> permitidas) {
        if (!permitidas.contains(chave)) {
            throw naoPermitido();
        }
        return chave;
    }

    private static String chaveDeCondicao(String chave) {
        if (chave.length() > FichaLimites.CHAVE_CONDICAO_MAXIMA || !CHAVE_CONDICAO.matcher(chave).matches()) {
            throw new IllegalArgumentException("chave de condição inválida: use letras minúsculas, números e hífens"
                + " (ex.: em-choque), com até " + FichaLimites.CHAVE_CONDICAO_MAXIMA + " caracteres.");
        }
        return chave;
    }

    private static IllegalArgumentException naoPermitido() {
        return new IllegalArgumentException("campo não permitido.");
    }

    // ---------- Alvos ----------

    private static Alvo alvo(Function<Ficha, Object> ler, BiConsumer<Ficha, Object> escrever) {
        return new Alvo() {
            @Override
            public Object ler(Ficha ficha) {
                return ler.apply(ficha);
            }

            @Override
            public void escrever(Ficha ficha, Object valor) {
                escrever.accept(ficha, valor);
            }
        };
    }

    private static Alvo alvoEstado(Function<FichaEstado, Object> ler, BiConsumer<FichaEstado, Object> escrever) {
        return alvo(f -> ler.apply(f.getEstado()), (f, v) -> escrever.accept(f.getEstado(), v));
    }

    @SuppressWarnings("unchecked")
    private static <T> Alvo alvoMapa(Function<FichaEstado, Map<String, T>> mapa,
                                     BiConsumer<FichaEstado, Map<String, T>> definirMapa,
                                     String chave) {
        return alvoEstado(
            e -> mapa.apply(e) == null ? null : mapa.apply(e).get(chave),
            (e, v) -> {
                if (mapa.apply(e) == null) {
                    definirMapa.accept(e, new LinkedHashMap<>());
                }
                mapa.apply(e).put(chave, (T) v);
            });
    }

    /** Como alvoMapa, mas escrever null apaga a chave (remove a exceção) em vez de gravar null. */
    private static Alvo alvoMapaRemovivel(Function<FichaEstado, Map<String, Object>> mapa,
                                          BiConsumer<FichaEstado, Map<String, Object>> definirMapa,
                                          String chave) {
        return alvoEstado(
            e -> mapa.apply(e) == null ? null : mapa.apply(e).get(chave),
            (e, v) -> {
                if (v == null) {
                    if (mapa.apply(e) != null) {
                        mapa.apply(e).remove(chave);
                    }
                    return;
                }
                if (mapa.apply(e) == null) {
                    definirMapa.accept(e, new LinkedHashMap<>());
                }
                mapa.apply(e).put(chave, v);
            });
    }

    /** Campo da alcateia; se a ficha não tiver alcateia, ela é criada com os valores iniciais. */
    private static Alvo alvoAlcateia(Function<FichaEstado.Alcateia, Object> ler,
                                     BiConsumer<FichaEstado.Alcateia, Object> escrever) {
        return alvoEstado(
            e -> e.getAlcateia() == null ? null : ler.apply(e.getAlcateia()),
            (e, v) -> {
                if (e.getAlcateia() == null) {
                    e.setAlcateia(FichaDefaults.alcateiaInicial());
                }
                escrever.accept(e.getAlcateia(), v);
            });
    }

    private static String nomeOuNull(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }
}
