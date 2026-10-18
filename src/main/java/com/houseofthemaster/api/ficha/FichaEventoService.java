package com.houseofthemaster.api.ficha;

import com.houseofthemaster.api.usuario.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Log de alterações da ficha (MODELO-FICHA.md seção 2.4).
 *
 * Anti-flood: se o último evento do mesmo campo, da mesma ficha, for do mesmo autor e tiver menos
 * de {@code hotm.ficha.log.janela-juncao-segundos} (padrão 30 s), esse evento é atualizado (mantém o
 * "anterior" original, troca "novo" e "em") em vez de criar outro. Se a junção voltar ao valor
 * original, o evento é apagado. Ex.: PV 10 → 8 → 7 → 6 vira uma linha "PV 10 → 6".
 * Exceção: a troca de raça nunca junta (cada troca fica registrada, mesmo em menos de 30 s).
 */
@Service
public class FichaEventoService {

    public static final int LIMITE_PADRAO = 50;
    public static final int LIMITE_MAXIMO = 200;

    /** Campos cujo evento nunca é juntado com o anterior. */
    static final Set<String> CAMPOS_SEM_JUNCAO = Set.of("raca");

    private final FichaEventoRepository fichaEventoRepository;
    private final Duration janelaJuncao;

    public FichaEventoService(FichaEventoRepository fichaEventoRepository,
                              @Value("${hotm.ficha.log.janela-juncao-segundos:30}") long janelaJuncaoSegundos) {
        this.fichaEventoRepository = fichaEventoRepository;
        this.janelaJuncao = Duration.ofSeconds(janelaJuncaoSegundos);
    }

    /** Registra (ou junta) um evento por alteração efetiva. Chamado dentro da transação do PATCH. */
    public void registrar(Ficha ficha, Usuario autor, List<FichaPatchAplicador.AlteracaoAplicada> alteracoes,
                          Instant agora) {
        for (FichaPatchAplicador.AlteracaoAplicada alteracao : alteracoes) {
            FichaEvento ultimo = fichaEventoRepository
                .findFirstByFichaIdAndCampoOrderByEmDesc(ficha.getId(), alteracao.campo())
                .orElse(null);

            if (ultimo != null && !CAMPOS_SEM_JUNCAO.contains(alteracao.campo()) && podeJuntar(ultimo, autor, agora)) {
                ultimo.setNovo(alteracao.novo());
                ultimo.setEm(agora);
                if (Objects.equals(ultimo.getAnterior(), ultimo.getNovo())) {
                    fichaEventoRepository.delete(ultimo);
                }
                continue;
            }

            FichaEvento evento = new FichaEvento();
            evento.setFicha(ficha);
            evento.setAutor(autor);
            evento.setCampo(alteracao.campo());
            evento.setAnterior(alteracao.anterior());
            evento.setNovo(alteracao.novo());
            evento.setEm(agora);
            fichaEventoRepository.save(evento);
        }
    }

    /** Grava um evento avulso, sempre novo (sem junção). Usado na conclusão da criação. */
    public void registrarUnico(Ficha ficha, Usuario autor, String campo, Object anterior, Object novo, Instant agora) {
        FichaEvento evento = new FichaEvento();
        evento.setFicha(ficha);
        evento.setAutor(autor);
        evento.setCampo(campo);
        evento.setAnterior(anterior);
        evento.setNovo(novo);
        evento.setEm(agora);
        fichaEventoRepository.save(evento);
    }

    private boolean podeJuntar(FichaEvento ultimo, Usuario autor, Instant agora) {
        return ultimo.getAutor().getId().equals(autor.getId())
            && Duration.between(ultimo.getEm(), agora).compareTo(janelaJuncao) < 0;
    }

    /**
     * Página do log, mais recentes primeiro. "antes" é o cursor: só eventos com em < antes.
     *
     * Eventos do mesmo PATCH têm o mesmo instante; a página nunca corta um instante pela metade
     * (pode passar do limite para completá-lo), senão o próximo cursor pularia os que ficaram de fora.
     */
    public List<FichaEventoResponse> listar(UUID fichaId, Instant antes, Integer limite) {
        int tamanho = limite == null ? LIMITE_PADRAO : limite;
        if (tamanho < 1 || tamanho > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("limite deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }

        List<FichaEvento> pagina = antes == null
            ? fichaEventoRepository.findByFichaIdOrderByEmDescCampoAsc(fichaId, Limit.of(tamanho))
            : fichaEventoRepository.findByFichaIdAndEmBeforeOrderByEmDescCampoAsc(fichaId, antes, Limit.of(tamanho));

        if (pagina.size() == tamanho) {
            Instant ultimoInstante = pagina.get(pagina.size() - 1).getEm();
            List<FichaEvento> completa = new ArrayList<>(pagina.stream()
                .filter(e -> !e.getEm().equals(ultimoInstante))
                .toList());
            completa.addAll(fichaEventoRepository.findByFichaIdAndEmOrderByCampoAsc(fichaId, ultimoInstante));
            pagina = completa;
        }

        return pagina.stream()
            .map(e -> new FichaEventoResponse(
                e.getId(),
                e.getCampo(),
                e.getAnterior(),
                e.getNovo(),
                e.getAutor().getId(),
                e.getAutor().getNome(),
                e.getEm()
            ))
            .toList();
    }
}
