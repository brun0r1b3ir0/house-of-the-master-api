package com.houseofthemaster.api.ficha;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FichaPericiaRepository extends JpaRepository<FichaPericia, UUID> {
}
