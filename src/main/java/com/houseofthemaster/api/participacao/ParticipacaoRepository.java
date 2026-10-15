package com.houseofthemaster.api.participacao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface ParticipacaoRepository extends JpaRepository<Participacao, UUID> {

    List<Participacao> findByUsuarioId(UUID usuarioId);

    long countByCampanhaIdAndPapel(UUID campanhaId, Papel papel);

    boolean existsByUsuarioIdAndCampanhaId(UUID usuarioId, UUID campanhaId);

    Optional<Participacao> findByUsuarioIdAndCampanhaId(UUID usuarioId, UUID campanhaId);
}