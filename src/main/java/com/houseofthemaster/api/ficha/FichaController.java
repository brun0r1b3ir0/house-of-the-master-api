package com.houseofthemaster.api.ficha;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/campanhas/{campanhaId}/fichas")
public class FichaController {

    private final FichaService fichaService;

    public FichaController(FichaService fichaService) {
        this.fichaService = fichaService;
    }

    @GetMapping
    public ResponseEntity<?> listar(@PathVariable UUID campanhaId, Authentication authentication) {
        try {
            UUID usuarioId = UUID.fromString(authentication.getName());
            return ResponseEntity.ok(fichaService.listar(usuarioId, campanhaId));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> criar(@PathVariable UUID campanhaId,
                                   @Valid @RequestBody CriarFichaRequest request,
                                   Authentication authentication) {
        try {
            UUID usuarioId = UUID.fromString(authentication.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(fichaService.criar(usuarioId, campanhaId, request));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @GetMapping("/{fichaId}")
    public ResponseEntity<?> detalhar(@PathVariable UUID campanhaId,
                                      @PathVariable UUID fichaId,
                                      Authentication authentication) {
        try {
            UUID usuarioId = UUID.fromString(authentication.getName());
            return ResponseEntity.ok(fichaService.detalhar(usuarioId, campanhaId, fichaId));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PatchMapping("/{fichaId}")
    public ResponseEntity<?> alterar(@PathVariable UUID campanhaId,
                                     @PathVariable UUID fichaId,
                                     @Valid @RequestBody PatchFichaRequest request,
                                     Authentication authentication) {
        try {
            UUID usuarioId = UUID.fromString(authentication.getName());
            return ResponseEntity.ok(fichaService.alterar(usuarioId, campanhaId, fichaId, request));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/{fichaId}/eventos")
    public ResponseEntity<?> eventos(@PathVariable UUID campanhaId,
                                     @PathVariable UUID fichaId,
                                     @RequestParam(required = false) Instant antes,
                                     @RequestParam(required = false) Integer limite,
                                     Authentication authentication) {
        try {
            UUID usuarioId = UUID.fromString(authentication.getName());
            return ResponseEntity.ok(fichaService.listarEventos(usuarioId, campanhaId, fichaId, antes, limite));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/{fichaId}/criacao/concluir")
    public ResponseEntity<?> concluirCriacao(@PathVariable UUID campanhaId,
                                             @PathVariable UUID fichaId,
                                             @RequestBody ConcluirCriacaoRequest request,
                                             Authentication authentication) {
        try {
            UUID usuarioId = UUID.fromString(authentication.getName());
            return ResponseEntity.ok(fichaService.concluirCriacao(usuarioId, campanhaId, fichaId, request));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @DeleteMapping("/{fichaId}")
    public ResponseEntity<?> arquivar(@PathVariable UUID campanhaId,
                                      @PathVariable UUID fichaId,
                                      Authentication authentication) {
        try {
            UUID usuarioId = UUID.fromString(authentication.getName());
            fichaService.arquivar(usuarioId, campanhaId, fichaId);
            return ResponseEntity.noContent().build();

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }
}
