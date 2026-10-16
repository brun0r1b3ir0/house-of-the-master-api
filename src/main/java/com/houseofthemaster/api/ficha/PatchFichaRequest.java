package com.houseofthemaster.api.ficha;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PatchFichaRequest {

    @NotEmpty(message = "Informe ao menos uma operação")
    @Size(max = FichaLimites.OPS_MAXIMAS, message = "No máximo {max} operações por requisição")
    private List<@Valid OpPatch> ops;
}
