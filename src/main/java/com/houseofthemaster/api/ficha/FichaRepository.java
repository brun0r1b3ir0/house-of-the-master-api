package com.houseofthemaster.api.ficha;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FichaRepository extends JpaRepository<Ficha, UUID> {
}
