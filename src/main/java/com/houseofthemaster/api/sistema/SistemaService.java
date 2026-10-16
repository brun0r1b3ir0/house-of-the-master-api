package com.houseofthemaster.api.sistema;

import com.houseofthemaster.api.participacao.ParticipacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class SistemaService {

    private final ParticipacaoRepository participacaoRepository;
    private final CatalogoSistema catalogoSistema;

    public SistemaService(ParticipacaoRepository participacaoRepository, CatalogoSistema catalogoSistema) {
        this.participacaoRepository = participacaoRepository;
        this.catalogoSistema = catalogoSistema;
    }

    @Transactional(readOnly = true)
    public CatalogoSistema catalogo(UUID usuarioId, UUID campanhaId) {
        if (!participacaoRepository.existsByUsuarioIdAndCampanhaId(usuarioId, campanhaId)) {
            throw new NoSuchElementException("Campanha não encontrada.");
        }

        // Nesta fase todo catálogo é o do War If, para qualquer campanha.
        // Checagem futura por sistema: usar campanha.getSistema() para escolher o catálogo
        // (ou responder 404 quando a campanha não for de um sistema com catálogo).
        return catalogoSistema;
    }
}
