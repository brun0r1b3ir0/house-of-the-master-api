package com.houseofthemaster.api.ficha;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FichaRepository extends JpaRepository<Ficha, UUID> {

    /** Ficha ativa (não arquivada) da campanha, com dono e perícias já carregados. */
    @EntityGraph(attributePaths = {"dono", "pericias"})
    Optional<Ficha> findByIdAndCampanhaIdAndArquivadaFalse(UUID id, UUID campanhaId);

    @EntityGraph(attributePaths = "dono")
    List<Ficha> findByCampanhaIdAndArquivadaFalseOrderByCriadaEm(UUID campanhaId);

    @EntityGraph(attributePaths = "dono")
    List<Ficha> findByCampanhaIdAndDonoIdAndArquivadaFalseOrderByCriadaEm(UUID campanhaId, UUID donoId);
}
