package com.houseofthemaster.api.sistema;

import java.util.List;

/**
 * Catálogo do sistema War If, versionado em src/main/resources/sistema/warif-catalogo.json.
 * É também a resposta de GET /campanhas/{cid}/sistema.
 */
public record CatalogoSistema(
        String sistema,
        int versao,
        List<PericiaCatalogo> pericias,
        List<EstagioSanidadeCatalogo> estagiosSanidade
) {
}
