package com.houseofthemaster.api.usuario;

public record LoginResponse(String token, UsuarioResponse usuario) {
}