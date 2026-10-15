package com.houseofthemaster.api.campanha;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CampanhaRepository extends JpaRepository<Campanha, UUID> {

    boolean existsByCodigoConvite(String codigoConvite);

    Optional<Campanha> findByCodigoConvite(String codigoConvite);
}