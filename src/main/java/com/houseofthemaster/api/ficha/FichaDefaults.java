package com.houseofthemaster.api.ficha;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Valores iniciais de uma ficha nova. PROVISÓRIOS: este é o único lugar a mudar.
 * (As perícias iniciais vêm do catálogo do sistema, com ganho 0.)
 */
public final class FichaDefaults {

    public static final int ATRIBUTO = 50;
    public static final Map<String, Integer> ATUAIS = Map.of("PV", 10, "PE", 10, "PM", 10, "SAN", 100, "SRT", 100);
    public static final EstagioSanidade ESTAGIO_SANIDADE = EstagioSanidade.MENTE_SA;
    public static final int CONTADOR = 0;

    private FichaDefaults() {
    }

    public static FichaEstado estadoInicial() {
        FichaEstado estado = new FichaEstado();
        estado.setAtributos(preencher(FichaEstado.ATRIBUTOS, ATRIBUTO));

        Map<String, Integer> atuais = new LinkedHashMap<>();
        FichaEstado.ATUAIS.forEach(chave -> atuais.put(chave, ATUAIS.get(chave)));
        estado.setAtuais(atuais);

        estado.setEstagioSanidade(ESTAGIO_SANIDADE);
        estado.setContadores(preencher(FichaEstado.CONTADORES, CONTADOR));
        estado.setPerdaPermanenteTam(0);

        Map<String, Boolean> flags = new LinkedHashMap<>();
        FichaEstado.FLAGS.forEach(chave -> flags.put(chave, false));
        estado.setFlags(flags);

        estado.setAlcateia(alcateiaInicial());

        estado.setAbates(preencher(FichaEstado.ABATES, 0));
        estado.setOverrides(new LinkedHashMap<>());
        estado.setRegenOverrides(new LinkedHashMap<>());
        estado.setCondicoes(new LinkedHashMap<>());
        return estado;
    }

    public static FichaEstado.Alcateia alcateiaInicial() {
        FichaEstado.Alcateia alcateia = new FichaEstado.Alcateia();
        alcateia.setAtiva(false);
        alcateia.setMembros(0);
        alcateia.setPapel("membro");
        alcateia.setLinhagem("normal");
        return alcateia;
    }

    private static Map<String, Integer> preencher(Iterable<String> chaves, int valor) {
        Map<String, Integer> mapa = new LinkedHashMap<>();
        chaves.forEach(chave -> mapa.put(chave, valor));
        return mapa;
    }
}
