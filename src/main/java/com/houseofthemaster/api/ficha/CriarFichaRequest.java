package com.houseofthemaster.api.ficha;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CriarFichaRequest {

    @NotNull(message = "Tipo é obrigatório")
    private TipoFicha tipo;

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = FichaLimites.TEXTO_MAXIMO, message = "Nome deve ter no máximo {max} caracteres")
    private String nome;

    @Size(max = FichaLimites.TEXTO_MAXIMO, message = "Ocupação deve ter no máximo {max} caracteres")
    private String ocupacao;

    private Raca raca;
}
