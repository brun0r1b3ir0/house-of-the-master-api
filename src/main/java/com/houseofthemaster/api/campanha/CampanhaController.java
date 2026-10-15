package com.houseofthemaster.api.campanha;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.NoSuchElementException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/campanhas")
public class CampanhaController {

    private final CampanhaService campanhaService;

    public CampanhaController(CampanhaService campanhaService) {
        this.campanhaService = campanhaService;
    }

    @PostMapping
    public ResponseEntity<?> criar(@Valid @RequestBody CriarCampanhaRequest request,
                                   Authentication authentication) {
        try {
            UUID usuarioId = UUID.fromString(authentication.getName());
            CampanhaResponse resposta = campanhaService.criar(usuarioId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(resposta);

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<CampanhaResumoResponse>> listar(Authentication authentication) {
        UUID usuarioId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(campanhaService.listarDoUsuario(usuarioId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detalhar(@PathVariable UUID id, Authentication authentication) {
        try {
            UUID usuarioId = UUID.fromString(authentication.getName());
            return ResponseEntity.ok(campanhaService.detalhar(usuarioId, id));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("/entrar")
    public ResponseEntity<?> entrar(@Valid @RequestBody EntrarRequest request,
                                    Authentication authentication) {
        try {
            UUID usuarioId = UUID.fromString(authentication.getName());
            return ResponseEntity.ok(campanhaService.entrar(usuarioId, request.getCodigo()));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }
}