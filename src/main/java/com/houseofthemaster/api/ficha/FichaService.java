package com.houseofthemaster.api.ficha;

import com.houseofthemaster.api.participacao.Papel;
import com.houseofthemaster.api.participacao.Participacao;
import com.houseofthemaster.api.participacao.ParticipacaoRepository;
import com.houseofthemaster.api.sistema.CatalogoSistema;
import com.houseofthemaster.api.sistema.PericiaCatalogo;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Fichas da campanha (MODELO-FICHA.md, seções 3 e 4).
 *
 * Regras de acesso:
 * - quem não participa da campanha: 404 em tudo;
 * - ficha arquivada, de outra campanha ou que o usuário não pode ver: 404;
 * - JOGADOR vê e edita só as próprias fichas (PJ);
 * - MESTRE vê todas, mas só edita/arquiva NPC (PJ de jogador: 403).
 */
@Service
public class FichaService {

    private final FichaRepository fichaRepository;
    private final ParticipacaoRepository participacaoRepository;
    private final CatalogoSistema catalogoSistema;
    private final FichaPatchAplicador patchAplicador;
    private final FichaEventoService fichaEventoService;
    private final CriacaoValidador criacaoValidador;
    private final Clock clock;
    private final Map<String, Integer> ordemNoCatalogo = new HashMap<>();

    public FichaService(FichaRepository fichaRepository,
                        ParticipacaoRepository participacaoRepository,
                        CatalogoSistema catalogoSistema,
                        FichaPatchAplicador patchAplicador,
                        FichaEventoService fichaEventoService,
                        CriacaoValidador criacaoValidador,
                        Clock clock) {
        this.fichaRepository = fichaRepository;
        this.participacaoRepository = participacaoRepository;
        this.catalogoSistema = catalogoSistema;
        this.patchAplicador = patchAplicador;
        this.fichaEventoService = fichaEventoService;
        this.criacaoValidador = criacaoValidador;
        this.clock = clock;

        List<PericiaCatalogo> pericias = catalogoSistema.pericias();
        for (int i = 0; i < pericias.size(); i++) {
            ordemNoCatalogo.put(pericias.get(i).nome(), i);
        }
    }

    @Transactional(readOnly = true)
    public List<FichaResumoResponse> listar(UUID usuarioId, UUID campanhaId) {
        Participacao participacao = participacao(usuarioId, campanhaId);

        List<Ficha> fichas = participacao.getPapel() == Papel.MESTRE
            ? fichaRepository.findByCampanhaIdAndArquivadaFalseOrderByCriadaEm(campanhaId)
            : fichaRepository.findByCampanhaIdAndDonoIdAndArquivadaFalseOrderByCriadaEm(campanhaId, usuarioId);

        return fichas.stream()
            .map(ficha -> new FichaResumoResponse(
                ficha.getId(),
                ficha.getTipo(),
                ficha.getNome(),
                ficha.getOcupacao(),
                ficha.getRaca(),
                ficha.getDono().getId(),
                ficha.getDono().getNome(),
                ficha.getAtualizadaEm()
            ))
            .toList();
    }

    @Transactional
    public FichaResponse criar(UUID usuarioId, UUID campanhaId, CriarFichaRequest request) {
        Participacao participacao = participacao(usuarioId, campanhaId);

        TipoFicha permitido = participacao.getPapel() == Papel.MESTRE ? TipoFicha.NPC : TipoFicha.PJ;
        if (request.getTipo() != permitido) {
            throw new AccessDeniedException(participacao.getPapel() == Papel.MESTRE
                ? "O Mestre só cria fichas de NPC."
                : "Jogador só cria fichas de PJ.");
        }

        // Nesta fase a ficha é sempre do War If, para qualquer campanha.
        // Checagem futura por sistema: conferir campanha.getSistema() antes de criar a ficha
        // (e escolher o catálogo de perícias do sistema da campanha).
        Ficha ficha = new Ficha();
        ficha.setCampanha(participacao.getCampanha());
        ficha.setDono(participacao.getUsuario());
        ficha.setTipo(request.getTipo());
        ficha.setNome(request.getNome());
        ficha.setOcupacao(request.getOcupacao());
        ficha.setRaca(request.getRaca());
        ficha.setEstado(FichaDefaults.estadoInicial());
        if (request.getTipo() == TipoFicha.PJ) {
            ficha.getEstado().setCriacao(FichaDefaults.criacaoPendente());
        }
        Instant agora = agora();
        ficha.setCriadaEm(agora);
        ficha.setAtualizadaEm(agora);

        // Toda ficha recebe todas as perícias do catálogo, inclusive as exclusivas de raça (Olfato):
        // o frontend esconde as que não se aplicam e o ganho não se perde ao trocar de raça.
        for (PericiaCatalogo doCatalogo : catalogoSistema.pericias()) {
            FichaPericia pericia = new FichaPericia();
            pericia.setNome(doCatalogo.nome());
            pericia.setCategoria(doCatalogo.categoria());
            pericia.setGanho(0);
            pericia.setGanhoTreino(0);
            pericia.setOrigem(OrigemPericia.CATALOGO);
            ficha.adicionarPericia(pericia);
        }

        return paraResponse(fichaRepository.save(ficha));
    }

    @Transactional(readOnly = true)
    public FichaResponse detalhar(UUID usuarioId, UUID campanhaId, UUID fichaId) {
        Participacao participacao = participacao(usuarioId, campanhaId);
        return paraResponse(fichaVisivel(participacao, campanhaId, fichaId));
    }

    /**
     * Aplica as ops de forma atômica: se qualquer op for inválida (IllegalArgumentException = 400),
     * nenhuma é aplicada e nenhum evento é gravado. Cada campo que de fato mudou gera (ou junta) um
     * evento no log. Sem controle de versão: a última escrita vence.
     */
    @Transactional
    public FichaResponse alterar(UUID usuarioId, UUID campanhaId, UUID fichaId, PatchFichaRequest request) {
        Participacao participacao = participacao(usuarioId, campanhaId);
        Ficha ficha = fichaEditavel(participacao, campanhaId, fichaId);

        List<FichaPatchAplicador.Alteracao> alteracoes = patchAplicador.validar(ficha, request.getOps());
        List<FichaPatchAplicador.AlteracaoAplicada> mudancas = patchAplicador.aplicar(ficha, alteracoes);

        if (!mudancas.isEmpty()) {
            Instant agora = agora();
            ficha.setAtualizadaEm(agora);
            fichaEventoService.registrar(ficha, participacao.getUsuario(), mudancas, agora);
        }
        return paraResponse(ficha);
    }

    @Transactional
    public void arquivar(UUID usuarioId, UUID campanhaId, UUID fichaId) {
        Participacao participacao = participacao(usuarioId, campanhaId);
        Ficha ficha = fichaEditavel(participacao, campanhaId, fichaId);
        ficha.setArquivada(true);
        ficha.setAtualizadaEm(agora());
    }

    /**
     * Conclui a criação de personagem, numa transação: grava os 8 atributos, SUBSTITUI o ganho de todas as
     * perícias (as que não estão nos ganhos voltam a 0), marca estado.criacao como concluída e grava UM
     * evento no log. Ordem: 404 (participação/visibilidade), 403 (Mestre em PJ), 409 (não está em
     * criação), 400 (regras, ver CriacaoValidador).
     */
    @Transactional
    public FichaResponse concluirCriacao(UUID usuarioId, UUID campanhaId, UUID fichaId, ConcluirCriacaoRequest request) {
        Participacao participacao = participacao(usuarioId, campanhaId);
        Ficha ficha = fichaEditavel(participacao, campanhaId, fichaId);

        FichaEstado.Criacao criacao = ficha.getEstado().getCriacao();
        if (criacao == null || !Boolean.FALSE.equals(criacao.getConcluida())) {
            throw new IllegalStateException("Esta ficha não está em criação.");
        }

        CriacaoValidador.CriacaoValidada validada = criacaoValidador.validar(ficha.getRaca(), request);
        for (String nome : validada.ganhoPorPericia().keySet()) {
            if (ficha.getPericias().stream().noneMatch(p -> p.getNome().equals(nome))) {
                throw new IllegalArgumentException("A ficha não tem a perícia \"" + nome + "\".");
            }
        }

        Instant agora = agora();
        ficha.getEstado().setAtributos(new LinkedHashMap<>(validada.atributos()));
        for (FichaPericia pericia : ficha.getPericias()) {
            pericia.setGanho(validada.ganhoPorPericia().getOrDefault(pericia.getNome(), 0));
        }

        FichaEstado.GanhosCriacao ganhos = new FichaEstado.GanhosCriacao();
        ganhos.setProfissao(new LinkedHashMap<>(validada.ganhosProfissao()));
        ganhos.setInteresse(new LinkedHashMap<>(validada.ganhosInteresse()));
        FichaEstado.SnapshotCriacao snapshot = new FichaEstado.SnapshotCriacao();
        snapshot.setAtributos(new LinkedHashMap<>(validada.atributos()));
        snapshot.setGanhos(ganhos);

        FichaEstado.Criacao concluida = new FichaEstado.Criacao();
        concluida.setConcluida(true);
        concluida.setProfissaoAtributos(List.of(validada.principal(), validada.secundario()));
        concluida.setPericiasProfissao(validada.periciasProfissao());
        concluida.setSnapshot(snapshot);
        concluida.setConcluidaEm(agora);
        ficha.getEstado().setCriacao(concluida);
        ficha.setAtualizadaEm(agora);

        fichaEventoService.registrarUnico(ficha, participacao.getUsuario(), "criacao.concluida", false, true, agora);
        return paraResponse(ficha);
    }

    /** Log da ficha: visível a quem pode ver a ficha (dono da PJ e Mestre). */
    @Transactional(readOnly = true)
    public List<FichaEventoResponse> listarEventos(UUID usuarioId, UUID campanhaId, UUID fichaId,
                                                   Instant antes, Integer limite) {
        Participacao participacao = participacao(usuarioId, campanhaId);
        Ficha ficha = fichaVisivel(participacao, campanhaId, fichaId);
        return fichaEventoService.listar(ficha.getId(), antes, limite);
    }

    /** Instante atual na precisão do banco (microssegundos), para o valor em memória bater com o gravado. */
    private Instant agora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    // ---------- Acesso ----------

    private Participacao participacao(UUID usuarioId, UUID campanhaId) {
        return participacaoRepository.findByUsuarioIdAndCampanhaId(usuarioId, campanhaId)
            .orElseThrow(() -> new NoSuchElementException("Campanha não encontrada."));
    }

    /** Ficha ativa desta campanha que o participante pode ver; senão 404. */
    Ficha fichaVisivel(Participacao participacao, UUID campanhaId, UUID fichaId) {
        Ficha ficha = fichaRepository.findByIdAndCampanhaIdAndArquivadaFalse(fichaId, campanhaId)
            .orElseThrow(() -> new NoSuchElementException("Ficha não encontrada."));

        boolean ehDono = ficha.getDono().getId().equals(participacao.getUsuario().getId());
        if (participacao.getPapel() != Papel.MESTRE && !ehDono) {
            throw new NoSuchElementException("Ficha não encontrada.");
        }
        return ficha;
    }

    /** Ficha que o participante pode alterar; 404 se não pode vê-la, 403 se só pode ler. */
    Ficha fichaEditavel(Participacao participacao, UUID campanhaId, UUID fichaId) {
        Ficha ficha = fichaVisivel(participacao, campanhaId, fichaId);

        if (participacao.getPapel() == Papel.MESTRE && ficha.getTipo() == TipoFicha.PJ) {
            throw new AccessDeniedException("O Mestre só visualiza fichas de jogadores.");
        }
        return ficha;
    }

    // ---------- Conversão ----------

    private FichaResponse paraResponse(Ficha ficha) {
        List<FichaPericiaResponse> pericias = ficha.getPericias().stream()
            .sorted(Comparator
                .comparing((FichaPericia p) -> ordemNoCatalogo.getOrDefault(p.getNome(), Integer.MAX_VALUE))
                .thenComparing(FichaPericia::getNome))
            .map(p -> new FichaPericiaResponse(
                p.getNome(), p.getCategoria(), p.getGanho(), p.getGanhoTreino(), p.getOrigem()))
            .toList();

        return new FichaResponse(
            ficha.getId(),
            ficha.getCampanha().getId(),
            ficha.getTipo(),
            ficha.getNome(),
            ficha.getOcupacao(),
            ficha.getRaca(),
            ficha.getDono().getId(),
            ficha.getDono().getNome(),
            ficha.getCriadaEm(),
            ficha.getAtualizadaEm(),
            ficha.getEstado(),
            pericias
        );
    }
}
