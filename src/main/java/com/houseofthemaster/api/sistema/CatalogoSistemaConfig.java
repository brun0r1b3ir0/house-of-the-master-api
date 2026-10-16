package com.houseofthemaster.api.sistema;

import com.houseofthemaster.api.ficha.EstagioSanidade;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Carrega o catálogo uma vez, ao subir a aplicação, e confere a estrutura.
 * Se o arquivo estiver malformado a aplicação não sobe.
 */
@Configuration
public class CatalogoSistemaConfig {

    static final String ARQUIVO = "sistema/warif-catalogo.json";

    @Bean
    public CatalogoSistema catalogoSistema() throws IOException {
        // Mapper próprio e estrito: um campo com nome errado no arquivo é erro, não é ignorado.
        JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

        try (InputStream entrada = new ClassPathResource(ARQUIVO).getInputStream()) {
            CatalogoSistema catalogo = mapper.readValue(entrada, CatalogoSistema.class);
            validar(catalogo);
            return catalogo;
        }
    }

    static void validar(CatalogoSistema catalogo) {
        if (catalogo.pericias() == null || catalogo.pericias().isEmpty()) {
            throw new IllegalStateException(ARQUIVO + ": lista de perícias vazia.");
        }

        Set<String> nomes = new HashSet<>();
        for (PericiaCatalogo pericia : catalogo.pericias()) {
            if (pericia.nome() == null || pericia.nome().isBlank()
                    || pericia.categoria() == null || pericia.valorInicial() == null) {
                throw new IllegalStateException(ARQUIVO + ": perícia incompleta: " + pericia);
            }
            if (!nomes.add(pericia.nome())) {
                throw new IllegalStateException(ARQUIVO + ": perícia repetida: " + pericia.nome());
            }
        }

        List<EstagioSanidade> chaves = catalogo.estagiosSanidade() == null ? List.of()
            : catalogo.estagiosSanidade().stream().map(EstagioSanidadeCatalogo::chave).toList();
        if (!new HashSet<>(chaves).equals(Set.of(EstagioSanidade.values()))
                || chaves.size() != EstagioSanidade.values().length) {
            throw new IllegalStateException(ARQUIVO + ": os estágios de sanidade devem ser exatamente "
                + Arrays.toString(EstagioSanidade.values()));
        }
    }
}
