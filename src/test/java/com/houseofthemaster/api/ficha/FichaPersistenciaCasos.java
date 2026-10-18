package com.houseofthemaster.api.ficha;

import com.houseofthemaster.api.campanha.Campanha;
import com.houseofthemaster.api.usuario.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Casos de persistência da ficha (estado em JSON, perícias e eventos).
 * Rodam no H2 (FichaPersistenciaTest) e no Postgres real com as migrations (MigrationsPostgresTest).
 * Cada teste roda numa transação desfeita no fim, então nada fica no banco.
 */
@Transactional
public abstract class FichaPersistenciaCasos {

    @Autowired
    protected EntityManager em;

    @Autowired
    protected EntityManagerFactory emf;

    @Test
    void hibernateUsaJackson3ParaJson() {
        Object mapper = emf.unwrap(SessionFactoryImplementor.class)
            .getSessionFactoryOptions().getJsonFormatMapper();

        assertThat(mapper).isInstanceOf(Jackson3JsonFormatMapper.class);
    }

    @Test
    void estadoCompletoVaiEVoltaIgual() {
        FichaEstado estado = estadoCompleto();
        Ficha ficha = novaFicha(estado);
        em.persist(ficha);
        em.flush();
        em.clear();

        Ficha lida = em.find(Ficha.class, ficha.getId());

        assertThat(lida.getEstado()).isEqualTo(estadoCompleto());
        assertThat(lida.getEstado().getAtributos()).containsEntry("FOR", 50).containsEntry("EDU", 70);
        assertThat(lida.getEstado().getEstagioSanidade()).isEqualTo(EstagioSanidade.ALERTA_VERMELHO);
        assertThat(lida.getEstado().getFlags()).containsEntry("verbena", true);
        assertThat(lida.getEstado().getAlcateia().getPapel()).isEqualTo("alfa");
        assertThat(lida.getEstado().getCondicoes()).containsEntry("em-choque", 2);
    }

    @Test
    void criacaoConcluidaVaiEVoltaComInstantTipado() {
        FichaEstado estado = estadoCompleto();
        FichaEstado.GanhosCriacao ganhos = new FichaEstado.GanhosCriacao();
        ganhos.setProfissao(new LinkedHashMap<>(Map.of("Furtividade", 60)));
        ganhos.setInteresse(new LinkedHashMap<>(Map.of("Medicina", 50)));
        FichaEstado.SnapshotCriacao snapshot = new FichaEstado.SnapshotCriacao();
        snapshot.setAtributos(new LinkedHashMap<>(estado.getAtributos()));
        snapshot.setGanhos(ganhos);
        FichaEstado.Criacao criacao = new FichaEstado.Criacao();
        criacao.setConcluida(true);
        criacao.setProfissaoAtributos(java.util.List.of("DES", "EDU"));
        criacao.setPericiasProfissao(java.util.List.of("Furtividade", "Charme"));
        criacao.setSnapshot(snapshot);
        criacao.setConcluidaEm(Instant.parse("2026-10-18T10:00:00.123456Z"));
        estado.setCriacao(criacao);
        Ficha ficha = novaFicha(estado);
        em.persist(ficha);
        em.flush();
        em.clear();

        FichaEstado.Criacao lida = em.find(Ficha.class, ficha.getId()).getEstado().getCriacao();

        assertThat(lida).isEqualTo(criacao);
        assertThat(lida.getConcluidaEm()).isEqualTo(Instant.parse("2026-10-18T10:00:00.123456Z"));
    }

    @Test
    void valoresNulosNoEstadoSobrevivem() {
        FichaEstado estado = new FichaEstado();
        Map<String, Integer> atuais = new HashMap<>();
        atuais.put("PV", 10);
        atuais.put("PE", null);
        estado.setAtuais(atuais);
        // os demais campos ficam nulos de propósito
        Ficha ficha = novaFicha(estado);
        ficha.setOcupacao(null);
        ficha.setRaca(null);
        em.persist(ficha);
        em.flush();
        em.clear();

        Ficha lida = em.find(Ficha.class, ficha.getId());

        assertThat(lida.getEstado()).isEqualTo(estado);
        assertThat(lida.getEstado().getAtuais()).containsEntry("PV", 10).containsEntry("PE", null);
        assertThat(lida.getEstado().getAtributos()).isNull();
        assertThat(lida.getEstado().getEstagioSanidade()).isNull();
        assertThat(lida.getEstado().getAlcateia()).isNull();
        assertThat(lida.getOcupacao()).isNull();
        assertThat(lida.getRaca()).isNull();
    }

    @Test
    void instantDentroDoEstadoViraTextoIso8601() {
        Instant instante = Instant.parse("2026-10-16T18:30:15.123456Z");
        FichaEstado estado = estadoCompleto();
        estado.getOverrides().put("aplicadoEm", instante);
        Ficha ficha = novaFicha(estado);
        em.persist(ficha);
        em.flush();
        em.clear();

        Object lido = em.find(Ficha.class, ficha.getId()).getEstado().getOverrides().get("aplicadoEm");

        // Em Map<String, Object> não há tipo para voltar a Instant: volta o texto ISO-8601.
        assertThat(lido).isEqualTo("2026-10-16T18:30:15.123456Z");
        assertThat(Instant.parse((String) lido)).isEqualTo(instante);
    }

    @Test
    void timestampsDaFichaSaoGravadosEVoltamIguais() {
        Ficha ficha = novaFicha(estadoCompleto());
        em.persist(ficha);
        em.flush();
        Instant criadaEm = ficha.getCriadaEm();
        Instant atualizadaEm = ficha.getAtualizadaEm();
        em.clear();

        Ficha lida = em.find(Ficha.class, ficha.getId());

        assertThat(criadaEm).isNotNull();
        assertThat(lida.getCriadaEm()).isEqualTo(criadaEm.truncatedTo(ChronoUnit.MICROS));
        assertThat(lida.getAtualizadaEm()).isEqualTo(atualizadaEm.truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void lerFichaSemAlterarNaoGeraUpdate() {
        Ficha ficha = novaFicha(estadoCompleto());
        em.persist(ficha);
        em.flush();
        em.clear();

        Ficha lida = em.find(Ficha.class, ficha.getId());
        lida.getPericias().size();

        // se o estado JSON parecesse "sujo" após a leitura, o Hibernate faria um UPDATE no próximo flush
        assertThat(em.unwrap(Session.class).isDirty()).isFalse();

        lida.getEstado().getAtuais().put("PV", 3);
        assertThat(em.unwrap(Session.class).isDirty()).isTrue();
    }

    @Test
    void periciasSaoGravadasEmCascataComAFicha() {
        Ficha ficha = novaFicha(estadoCompleto());
        ficha.adicionarPericia(pericia("Furtividade", CategoriaPericia.FISICA, 15));
        ficha.adicionarPericia(pericia("Primórdios", CategoriaPericia.DESCONHECIDA, 0));
        em.persist(ficha);
        em.flush();
        em.clear();

        Ficha lida = em.find(Ficha.class, ficha.getId());

        assertThat(lida.getPericias()).hasSize(2);
        FichaPericia furtividade = lida.getPericias().stream()
            .filter(p -> p.getNome().equals("Furtividade")).findFirst().orElseThrow();
        assertThat(furtividade.getCategoria()).isEqualTo(CategoriaPericia.FISICA);
        assertThat(furtividade.getGanho()).isEqualTo(15);
        assertThat(furtividade.getGanhoTreino()).isZero();
        assertThat(furtividade.getOrigem()).isEqualTo(OrigemPericia.CATALOGO);
    }

    @Test
    void eventoGuardaValoresEscalaresEmJson() {
        Ficha ficha = novaFicha(estadoCompleto());
        em.persist(ficha);
        FichaEvento numero = evento(ficha, "atuais.PV", 10, 6);
        FichaEvento texto = evento(ficha, "estagioSanidade", "MENTE_SA", "ALERTA_VERMELHO");
        FichaEvento booleano = evento(ficha, "flags.verbena", false, true);
        FichaEvento nulo = evento(ficha, "raca", null, "VAMPIRO");
        em.flush();
        em.clear();

        assertThat(em.find(FichaEvento.class, numero.getId()).getNovo()).isEqualTo(6);
        assertThat(em.find(FichaEvento.class, numero.getId()).getAnterior()).isEqualTo(10);
        assertThat(em.find(FichaEvento.class, texto.getId()).getNovo()).isEqualTo("ALERTA_VERMELHO");
        assertThat(em.find(FichaEvento.class, booleano.getId()).getNovo()).isEqualTo(true);
        assertThat(em.find(FichaEvento.class, nulo.getId()).getAnterior()).isNull();
    }

    // ---------- Auxiliares ----------

    private Ficha novaFicha(FichaEstado estado) {
        Usuario dono = new Usuario();
        dono.setNome("Jogador");
        dono.setEmail(UUID.randomUUID() + "@email.com");
        dono.setSenhaHash("hash");
        em.persist(dono);

        Campanha campanha = new Campanha();
        campanha.setNome("War If");
        campanha.setSistema("War If");
        campanha.setCodigoConvite(UUID.randomUUID().toString().substring(0, 9));
        em.persist(campanha);

        Ficha ficha = new Ficha();
        ficha.setCampanha(campanha);
        ficha.setDono(dono);
        ficha.setTipo(TipoFicha.PJ);
        ficha.setNome("Ezra");
        ficha.setOcupacao("Detetive");
        ficha.setRaca(Raca.VAMPIRO);
        ficha.setEstado(estado);
        ficha.setCriadaEm(Instant.parse("2026-10-16T12:00:00.123456Z"));
        ficha.setAtualizadaEm(Instant.parse("2026-10-16T12:05:00.654321Z"));
        return ficha;
    }

    private static FichaPericia pericia(String nome, CategoriaPericia categoria, int ganho) {
        FichaPericia pericia = new FichaPericia();
        pericia.setNome(nome);
        pericia.setCategoria(categoria);
        pericia.setGanho(ganho);
        pericia.setOrigem(OrigemPericia.CATALOGO);
        return pericia;
    }

    private FichaEvento evento(Ficha ficha, String campo, Object anterior, Object novo) {
        FichaEvento evento = new FichaEvento();
        evento.setFicha(ficha);
        evento.setAutor(ficha.getDono());
        evento.setCampo(campo);
        evento.setAnterior(anterior);
        evento.setNovo(novo);
        evento.setEm(Instant.now());
        em.persist(evento);
        return evento;
    }

    private static FichaEstado estadoCompleto() {
        FichaEstado estado = new FichaEstado();
        estado.setAtributos(new LinkedHashMap<>(Map.of(
            "FOR", 50, "CON", 50, "TAM", 50, "DES", 60, "APA", 50, "INT", 50, "POD", 50, "EDU", 70)));
        estado.setAtuais(new LinkedHashMap<>(Map.of("PV", 10, "PE", 10, "PM", 10, "SAN", 100, "SRT", 100)));
        estado.setEstagioSanidade(EstagioSanidade.ALERTA_VERMELHO);
        estado.setContadores(new LinkedHashMap<>(Map.of(
            "exaustao", 1, "fome", 0, "sede", 0, "debilitantes", 0, "frio", 2, "sangria", 10)));
        estado.setPerdaPermanenteTam(0);
        estado.setFlags(new LinkedHashMap<>(Map.of("verbena", true, "colapsoMagico", false)));
        FichaEstado.Alcateia alcateia = new FichaEstado.Alcateia();
        alcateia.setAtiva(true);
        alcateia.setMembros(3);
        alcateia.setPapel("alfa");
        alcateia.setLinhagem("normal");
        estado.setAlcateia(alcateia);
        estado.setAbates(new LinkedHashMap<>(Map.of("vampiros", 1, "outros", 0)));
        estado.setOverrides(new LinkedHashMap<>());
        estado.setRegenOverrides(new LinkedHashMap<>());
        estado.setCondicoes(new LinkedHashMap<>(Map.of("em-choque", 2)));
        return estado;
    }
}
