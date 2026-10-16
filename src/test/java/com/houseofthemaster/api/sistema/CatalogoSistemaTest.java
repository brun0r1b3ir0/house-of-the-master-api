package com.houseofthemaster.api.sistema;

import com.houseofthemaster.api.ficha.CategoriaPericia;
import com.houseofthemaster.api.ficha.EstagioSanidade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Confere o arquivo do catálogo contra o CATALOGO-PERICIAS.md. */
class CatalogoSistemaTest {

    private static CatalogoSistema catalogo;
    private static Map<String, PericiaCatalogo> porNome;

    @BeforeAll
    static void carregar() throws Exception {
        catalogo = new CatalogoSistemaConfig().catalogoSistema();
        porNome = catalogo.pericias().stream()
            .collect(Collectors.toMap(PericiaCatalogo::nome, Function.identity()));
    }

    @Test
    void temAs71PericiasComOsTotaisPorCategoria() {
        Map<CategoriaPericia, Long> porCategoria = catalogo.pericias().stream()
            .collect(Collectors.groupingBy(PericiaCatalogo::categoria, Collectors.counting()));

        assertThat(catalogo.pericias()).hasSize(71);
        assertThat(porCategoria).containsExactlyInAnyOrderEntriesOf(Map.of(
            CategoriaPericia.COMBATE, 17L,
            CategoriaPericia.FISICA, 12L,
            CategoriaPericia.MENTAL, 36L,
            CategoriaPericia.INTERPESSOAL, 5L,
            CategoriaPericia.DESCONHECIDA, 1L));
    }

    @Test
    void valoresIniciaisEspeciais() {
        assertThat(porNome.get("Esquivar").valorInicial())
            .isEqualTo(new ValorInicial(null, FormulaValorInicial.DES_METADE));
        assertThat(porNome.get("Língua Nativa (Inglês)").valorInicial())
            .isEqualTo(new ValorInicial(null, FormulaValorInicial.EDU));
        assertThat(porNome.get("Primórdios").valorInicial()).isEqualTo(new ValorInicial(0, null));
        assertThat(porNome.get("Primórdios").categoria()).isEqualTo(CategoriaPericia.DESCONHECIDA);
        assertThat(porNome.get("Ciência Forense").valorInicial()).isEqualTo(new ValorInicial(1, null));
    }

    @Test
    void apenasEsquivarELinguaNativaUsamFormula() {
        List<String> comFormula = catalogo.pericias().stream()
            .filter(p -> p.valorInicial().formula() != null)
            .map(PericiaCatalogo::nome)
            .toList();

        assertThat(comFormula).containsExactlyInAnyOrder("Esquivar", "Língua Nativa (Inglês)");
    }

    @Test
    void olfatoEstaNoCatalogo() {
        assertThat(porNome.get("Olfato").categoria()).isEqualTo(CategoriaPericia.FISICA);
        assertThat(porNome.get("Olfato").valorInicial()).isEqualTo(new ValorInicial(50, null));
        assertThat(porNome.get("Lidar com Animais")).isNotNull();
        assertThat(porNome).doesNotContainKeys("Atletismo", "Investigar");
    }

    @Test
    void exigePrecisaoEmTodasDeCombateENasFisicasMarcadas() {
        assertThat(catalogo.pericias().stream()
            .filter(p -> p.categoria() == CategoriaPericia.COMBATE))
            .allMatch(PericiaCatalogo::exigePrecisao);

        List<String> fisicasComPrecisao = catalogo.pericias().stream()
            .filter(p -> p.categoria() == CategoriaPericia.FISICA && p.exigePrecisao())
            .map(PericiaCatalogo::nome)
            .toList();
        assertThat(fisicasComPrecisao).containsExactlyInAnyOrder(
            "Furtividade", "Prestidigitação", "Chaveiro", "Dirigir Automóveis", "Pilotar");

        assertThat(catalogo.pericias().stream()
            .filter(p -> p.categoria() != CategoriaPericia.COMBATE && p.categoria() != CategoriaPericia.FISICA))
            .noneMatch(PericiaCatalogo::exigePrecisao);
    }

    @Test
    void temOs8EstagiosDeSanidadeEmOrdem() {
        assertThat(catalogo.estagiosSanidade()).extracting(EstagioSanidadeCatalogo::chave)
            .containsExactly(EstagioSanidade.values());
        assertThat(catalogo.estagiosSanidade()).extracting(EstagioSanidadeCatalogo::ordem)
            .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void periciaRepetidaImpedeASubida() {
        PericiaCatalogo furtividade = porNome.get("Furtividade");
        CatalogoSistema repetido = new CatalogoSistema("War If", 1,
            List.of(furtividade, furtividade), catalogo.estagiosSanidade());

        assertThatThrownBy(() -> CatalogoSistemaConfig.validar(repetido))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("repetida");
    }

    @Test
    void estagioFaltandoImpedeASubida() {
        CatalogoSistema incompleto = new CatalogoSistema("War If", 1,
            catalogo.pericias(), catalogo.estagiosSanidade().subList(0, 7));

        assertThatThrownBy(() -> CatalogoSistemaConfig.validar(incompleto))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void valorInicialNaoAceitaNumeroEFormulaJuntosNemNenhum() {
        assertThatThrownBy(() -> new ValorInicial(5, FormulaValorInicial.EDU))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValorInicial(null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
