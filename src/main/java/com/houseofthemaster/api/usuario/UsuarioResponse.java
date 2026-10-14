package com.houseofthemaster.api.usuario;

import lombok.Getter;

import java.util.UUID;

@Getter
public class UsuarioResponse {

    private final UUID id;
    private final String nome;
    private final String email;

    public UsuarioResponse(Usuario usuario) {
        this.id = usuario.getId();
        this.email = usuario.getEmail();
        this.nome = usuario.getNome();
    }
}