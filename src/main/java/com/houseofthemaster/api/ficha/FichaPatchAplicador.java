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
 * Atômico em duas fases: validar() confere TODAS as ops sem tocar na ficha (qualquer op inválida
 * lança IllegalArgumentException = 400); só depois aplicar() escreve. Faixas validadas aqui são só as
 * básicas: "atual <= máximo" é responsabilidade do frontend (o máximo depende do cálculo).
 *
 * Fora da lista nesta fase (400): valorInicial, ganhoTreino, alcateia, abates, overrides, regenOverrides.
 */
@Component
public class FichaPatchAplicador {

    static final int TAMANHO_MAXIMO_TEXTO = 255;
    static final int SANGRIA_MAXIMA = 10;
    static final int TAMANHO_MAXIMO_CHAVE_CONDICAO = 64;
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

    public List<Alteracao> validar(Ficha ficha, List<OpPatch> ops) {
        List<Alteracao> alteracoes = new ArrayList<>();
        for (int i = 0; i < ops.size(); i++) {
            OpPatch op = ops.get(i);
            try {
                alteracoes.add(validar(ficha, op));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("ops[" + i + "] (" + op.getCampo() + "): " + e.getMessage());
            }
        }
        return alteracoes;
    }

    private Alteracao validar(Ficha ficha, OpPatch op) {
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
                return new Alteracao(campo,
                    alvo(f -> nomeOuNull(f.getRaca()), (f, v) -> f.setRaca(v == null ? null : Raca.valueOf((String) v))),
                    enumeracao(valor, Raca.class, true));
            case "estagioSanidade":
                return new Alteracao(campo,
                    alvoEstado(e -> nomeOuNull(e.getEstagioSanidade()),
                        (e, v) -> e.setEstagioSanidade(EstagioSanidade.valueOf((String) v))),
                    enumeracao(valor, EstagioSanidade.class, false));
            case "perdaPermanenteTam":
                return new Alteracao(campo,
                    alvoEstado(FichaEstado::getPerdaPermanenteTam, (e, v) -> e.setPerdaPermanenteTam((Integer) v)),
                    inteiro(valor, null));
            default:
                break;
        }

        int ponto = campo.indexOf('.');
        String grupo = ponto < 0 ? campo : campo.substring(0, ponto);
        String chave = ponto < 0 ? "" : campo.substring(ponto + 1);

        return switch (grupo) {
            case "atributos" -> new Alteracao(campo,
                alvoMapa(FichaEstado::getAtributos, FichaEstado::setAtributos, chaveConhecida(chave, FichaEstado.ATRIBUTOS)),
                inteiro(valor, null));
            case "atuais" -> new Alteracao(campo,
                alvoMapa(FichaEstado::getAtuais, FichaEstado::setAtuais, chaveConhecida(chave, FichaEstado.ATUAIS)),
                inteiro(valor, null));
            case "contadores" -> new Alteracao(campo,
                alvoMapa(FichaEstado::getContadores, FichaEstado::setContadores, chaveConhecida(chave, FichaEstado.CONTADORES)),
                inteiro(valor, "sangria".equals(chave) ? SANGRIA_MAXIMA : null));
            case "flags" -> new Alteracao(campo,
                alvoMapa(FichaEstado::getFlags, FichaEstado::setFlags, chaveConhecida(chave, FichaEstado.FLAGS)),
                booleano(valor));
            case "condicoes" -> new Alteracao(campo,
                alvoMapa(FichaEstado::getCondicoes, FichaEstado::setCondicoes, chaveDeCondicao(chave)),
                inteiro(valor, null));
            case "pericias" -> validarPericia(ficha, campo, valor);
            default -> throw naoPermitido();
        };
    }

    /** Só pericias.<nome>.ganho é permitido; o nome pode conter espaços, parênteses e barras. */
    private Alteracao validarPericia(Ficha ficha, String campo, JsonNode valor) {
        if (!campo.startsWith(PREFIXO_PERICIAS) || !campo.endsWith(SUFIXO_GANHO)
                || campo.length() <= PREFIXO_PERICIAS.length() + SUFIXO_GANHO.length()) {
            throw naoPermitido();
        }
        String nome = campo.substring(PREFIXO_PERICIAS.length(), campo.length() - SUFIXO_GANHO.length());

        FichaPericia pericia = ficha.getPericias().stream()
            .filter(p -> p.getNome().equals(nome))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("a ficha não tem a perícia \"" + nome + "\"."));

        return new Alteracao(campo,
            alvo(f -> pericia.getGanho(), (f, v) -> pericia.setGanho((Integer) v)),
            inteiro(valor, null));
    }

    // ---------- Fase 2: aplicar ----------

    /** Aplica as alterações já validadas e devolve só as que mudaram algum valor. */
    public List<AlteracaoAplicada> aplicar(Ficha ficha, List<Alteracao> alteracoes) {
        List<AlteracaoAplicada> aplicadas = new ArrayList<>();
        for (Alteracao alteracao : alteracoes) {
            Object anterior = alteracao.alvo().ler(ficha);
            if (Objects.equals(anterior, alteracao.novo())) {
                continue;
            }
            alteracao.alvo().escrever(ficha, alteracao.novo());
            aplicadas.add(new AlteracaoAplicada(alteracao.campo(), anterior, alteracao.novo()));
        }
        return aplicadas;
    }

    // ---------- Valores ----------

    private static Integer inteiro(JsonNode valor, Integer maximo) {
        if (!valor.isIntegralNumber() || !valor.canConvertToInt()) {
            throw new IllegalArgumentException("deve ser um número inteiro.");
        }
        int numero = valor.intValue();
        if (numero < 0) {
            throw new IllegalArgumentException("deve ser maior ou igual a 0.");
        }
        if (maximo != null && numero > maximo) {
            throw new IllegalArgumentException("deve estar entre 0 e " + maximo + ".");
        }
        return numero;
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
        if (texto.length() > TAMANHO_MAXIMO_TEXTO) {
            throw new IllegalArgumentException("deve ter no máximo " + TAMANHO_MAXIMO_TEXTO + " caracteres.");
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
        if (chave.length() > TAMANHO_MAXIMO_CHAVE_CONDICAO || !CHAVE_CONDICAO.matcher(chave).matches()) {
            throw new IllegalArgumentException("chave de condição inválida: use letras minúsculas, números e hífens"
                + " (ex.: em-choque), com até " + TAMANHO_MAXIMO_CHAVE_CONDICAO + " caracteres.");
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

    private static String nomeOuNull(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }
}
