package com.houseofthemaster.api.campanha;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EntrarRequest {

    @NotBlank(message = "Código é obrigatório")
    private String codigo;
}