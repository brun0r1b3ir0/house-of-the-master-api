package com.houseofthemaster.api.campanha;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CriarCampanhaRequest {

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    @NotBlank(message = "Sistema é obrigatório")
    private String sistema;

    private String descricao;

    @Min(value = 1, message = "Máximo de jogadores deve ser pelo menos 1")
    private Integer maxJogadores;
}