package com.houseofthemaster.api.campanha;

import com.houseofthemaster.api.participacao.Papel;
import com.houseofthemaster.api.participacao.Participacao;
import com.houseofthemaster.api.participacao.ParticipacaoRepository;
import com.houseofthemaster.api.usuario.Usuario;
import com.houseofthemaster.api.usuario.UsuarioRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.List;

@Service
public class CampanhaService {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CampanhaRepository campanhaRepository;
    private final ParticipacaoRepository participacaoRepository;
    private final UsuarioRepository usuarioRepository;

    public CampanhaService(CampanhaRepository campanhaRepository,
                           ParticipacaoRepository participacaoRepository,
                           UsuarioRepository usuarioRepository) {
        this.campanhaRepository = campanhaRepository;
        this.participacaoRepository = participacaoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public CampanhaResponse criar(UUID usuarioId, CriarCampanhaRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new BadCredentialsException("Usuário não encontrado."));

        Campanha campanha = new Campanha();
        campanha.setNome(request.getNome());
        campanha.setSistema(request.getSistema());
        campanha.setDescricao(request.getDescricao());
        campanha.setMaxJogadores(request.getMaxJogadores());
        campanha.setCodigoConvite(gerarCodigoUnico());
        campanha = campanhaRepository.save(campanha);

        Participacao participacao = new Participacao();
        participacao.setUsuario(usuario);
        participacao.setCampanha(campanha);
        participacao.setPapel(Papel.MESTRE);
        participacaoRepository.save(participacao);

        return new CampanhaResponse(
            campanha.getId(),
            campanha.getNome(),
            campanha.getSistema(),
            campanha.getDescricao(),
            campanha.getMaxJogadores(),
            campanha.getCodigoConvite(),
            Papel.MESTRE
        );
    }

    @Transactional(readOnly = true)
    public List<CampanhaResumoResponse> listarDoUsuario(UUID usuarioId) {
        return participacaoRepository.findByUsuarioId(usuarioId).stream()
            .map(participacao -> {
                Campanha campanha = participacao.getCampanha();
                long jogadores = participacaoRepository
                    .countByCampanhaIdAndPapel(campanha.getId(), Papel.JOGADOR);

                String codigo = participacao.getPapel() == Papel.MESTRE
                    ? campanha.getCodigoConvite()
                    : null;

                return new CampanhaResumoResponse(
                    campanha.getId(),
                    campanha.getNome(),
                    participacao.getPapel(),
                    jogadores,
                    campanha.getMaxJogadores(),
                    campanha.getSistema(),
                    codigo
                );
            })
            .toList();
    }
    
    @Transactional
    public CampanhaResumoResponse entrar(UUID usuarioId, String codigo) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new BadCredentialsException("Usuário não encontrado."));

        Campanha campanha = campanhaRepository.findByCodigoConvite(codigo.trim().toUpperCase())
            .orElseThrow(() -> new NoSuchElementException("Código de convite inválido."));

        if (participacaoRepository.existsByUsuarioIdAndCampanhaId(usuarioId, campanha.getId())) {
            throw new IllegalStateException("Você já participa dessa campanha.");
        }

        long jogadores = participacaoRepository
            .countByCampanhaIdAndPapel(campanha.getId(), Papel.JOGADOR);

        if (campanha.getMaxJogadores() != null && jogadores >= campanha.getMaxJogadores()) {
            throw new IllegalStateException("Essa campanha já está cheia.");
        }

        Participacao participacao = new Participacao();
        participacao.setUsuario(usuario);
        participacao.setCampanha(campanha);
        participacao.setPapel(Papel.JOGADOR);
        participacaoRepository.save(participacao);

        return new CampanhaResumoResponse(
            campanha.getId(),
            campanha.getNome(),
            Papel.JOGADOR,
            jogadores + 1,
            campanha.getMaxJogadores(),
            campanha.getSistema(),
            null
        );
    }

    @Transactional(readOnly = true)
    public CampanhaDetalheResponse detalhar(UUID usuarioId, UUID campanhaId) {
        Participacao participacao = participacaoRepository
            .findByUsuarioIdAndCampanhaId(usuarioId, campanhaId)
            .orElseThrow(() -> new NoSuchElementException("Campanha não encontrada."));

        Campanha campanha = participacao.getCampanha();
        long jogadores = participacaoRepository
            .countByCampanhaIdAndPapel(campanha.getId(), Papel.JOGADOR);

        String codigo = participacao.getPapel() == Papel.MESTRE
            ? campanha.getCodigoConvite()
            : null;

        return new CampanhaDetalheResponse(
            campanha.getId(),
            campanha.getNome(),
            campanha.getDescricao(),
            campanha.getSistema(),
            participacao.getPapel(),
            jogadores,
            campanha.getMaxJogadores(),
            codigo
        );
    }    

    private String gerarCodigoUnico() {
        String codigo;
        do {
            codigo = trecho(4) + "-" + trecho(4);
        } while (campanhaRepository.existsByCodigoConvite(codigo));
        return codigo;
    }

    private String trecho(int tamanho) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tamanho; i++) {
            sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

}