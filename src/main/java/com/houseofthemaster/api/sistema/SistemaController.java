package com.houseofthemaster.api.sistema;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/campanhas/{campanhaId}/sistema")
public class SistemaController {

    private final SistemaService sistemaService;

    public SistemaController(SistemaService sistemaService) {
        this.sistemaService = sistemaService;
    }

    @GetMapping
    public ResponseEntity<?> catalogo(@PathVariable UUID campanhaId, Authentication authentication) {
        try {
            UUID usuarioId = UUID.fromString(authentication.getName());
            return ResponseEntity.ok(sistemaService.catalogo(usuarioId, campanhaId));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}
