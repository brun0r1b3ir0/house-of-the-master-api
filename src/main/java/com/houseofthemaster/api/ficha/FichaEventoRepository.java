package com.houseofthemaster.api.ficha;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FichaEventoRepository extends JpaRepository<FichaEvento, UUID> {

    /** Último evento de um campo da ficha (de qualquer autor), para a junção anti-flood. */
    @EntityGraph(attributePaths = "autor")
    Optional<FichaEvento> findFirstByFichaIdAndCampoOrderByEmDesc(UUID fichaId, String campo);

    @EntityGraph(attributePaths = "autor")
    List<FichaEvento> findByFichaIdOrderByEmDescCampoAsc(UUID fichaId, Limit limite);

    @EntityGraph(attributePaths = "autor")
    List<FichaEvento> findByFichaIdAndEmBeforeOrderByEmDescCampoAsc(UUID fichaId, Instant antes, Limit limite);

    /** Todos os eventos de um mesmo instante (para a página não cortar um PATCH ao meio). */
    @EntityGraph(attributePaths = "autor")
    List<FichaEvento> findByFichaIdAndEmOrderByCampoAsc(UUID fichaId, Instant em);
}
