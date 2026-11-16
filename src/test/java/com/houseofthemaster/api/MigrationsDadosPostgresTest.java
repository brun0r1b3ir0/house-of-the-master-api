package com.houseofthemaster.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migrations que corrigem DADOS, testadas contra dados reais: cada teste migra até a versão anterior,
 * insere fichas, migra até a versão testada e confere o que mudou; depois roda o mesmo SQL de novo e
 * confere que nada muda (idempotente). Sem Spring: Flyway e JDBC direto. Um único container para a classe
 * (menos memória); cada teste usa o próprio schema, para começar de um banco vazio.
 */
@ExtendWith(DockerDisponivelOuFalhaNoCi.class)
@Testcontainers
class MigrationsDadosPostgresTest {

    private static final OffsetDateTime CRIADA_EM = OffsetDateTime.parse("2026-10-01T12:00:00Z");

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    // ---------- V3: FOR/DES do Vampiro = 120 ----------

    @Test
    void v3CorrigeSoForEDesDoVampiroForaDe120EEIdempotente() throws Exception {
        String schema = "teste_v3";
        flyway(schema, "2").migrate();
        UUID vampiro50 = UUID.randomUUID();
        UUID vampiro120 = UUID.randomUUID();
        UUID humano50 = UUID.randomUUID();
        try (Connection conexao = conectar(schema)) {
            Dono dono = inserirDono(conexao);
            inserirFicha(conexao, vampiro50, dono, "VAMPIRO", estado(50));
            inserirFicha(conexao, vampiro120, dono, "VAMPIRO", estado(120));
            inserirFicha(conexao, humano50, dono, "HUMANO", estado(50));
        }

        assertThat(flyway(schema, "3").migrate().migrationsExecuted).isEqualTo(1);

        try (Connection conexao = conectar(schema)) {
            // Vampiro 50/50: só FOR e DES mudaram; o resto do estado (inclusive o snapshot) e atualizada_em, não.
            assertThat(valor(conexao, vampiro50, "estado #>> '{atributos,FOR}'")).isEqualTo("120");
            assertThat(valor(conexao, vampiro50, "estado #>> '{atributos,DES}'")).isEqualTo("120");
            assertThat(valor(conexao, vampiro50, "estado = jsonb_set(jsonb_set(cast(? as jsonb),"
                + " '{atributos,FOR}', '120'), '{atributos,DES}', '120')", estado(50))).isEqualTo("true");
            assertThat(valor(conexao, vampiro50, "estado #>> '{criacao,snapshot,atributos,FOR}'")).isEqualTo("50");
            assertThat(valor(conexao, vampiro50, "atualizada_em = cast(? as timestamptz)", CRIADA_EM.toString()))
                .isEqualTo("true");

            // Vampiro já em 120 e humano: estado idêntico ao inserido.
            assertThat(valor(conexao, vampiro120, "estado = cast(? as jsonb)", estado(120))).isEqualTo("true");
            assertThat(valor(conexao, humano50, "estado = cast(? as jsonb)", estado(50))).isEqualTo("true");

            // Rodar o mesmo SQL de novo não muda nenhuma linha.
            assertThat(executarScript(conexao, "db/migration/V3__vampiro_for_des_120.sql")).isZero();
        }
    }

    // ---------- V4: raça nula vira HUMANO; coluna NOT NULL com default HUMANO ----------

    @Test
    void v4ConverteRacaNulaEmHumanoTornaNotNullEEIdempotente() throws Exception {
        String schema = "teste_v4";
        flyway(schema, "3").migrate();
        UUID semRaca = UUID.randomUUID();
        UUID vampiro = UUID.randomUUID();
        UUID bruxo = UUID.randomUUID();
        Dono dono;
        try (Connection conexao = conectar(schema)) {
            dono = inserirDono(conexao);
            inserirFicha(conexao, semRaca, dono, null, estado(50));
            inserirFicha(conexao, vampiro, dono, "VAMPIRO", estado(120));
            inserirFicha(conexao, bruxo, dono, "BRUXO", estado(50));
        }

        assertThat(flyway(schema, "4").migrate().migrationsExecuted).isEqualTo(1);

        try (Connection conexao = conectar(schema)) {
            // Sem raça vira HUMANO; o estado e atualizada_em não mudam.
            assertThat(valor(conexao, semRaca, "raca")).isEqualTo("HUMANO");
            assertThat(valor(conexao, semRaca, "estado = cast(? as jsonb)", estado(50))).isEqualTo("true");
            assertThat(valor(conexao, semRaca, "atualizada_em = cast(? as timestamptz)", CRIADA_EM.toString()))
                .isEqualTo("true");

            // As outras não mudam.
            assertThat(valor(conexao, vampiro, "raca")).isEqualTo("VAMPIRO");
            assertThat(valor(conexao, vampiro, "estado = cast(? as jsonb)", estado(120))).isEqualTo("true");
            assertThat(valor(conexao, bruxo, "raca")).isEqualTo("BRUXO");
            assertThat(valor(conexao, bruxo, "estado = cast(? as jsonb)", estado(50))).isEqualTo("true");

            // Default HUMANO quando o insert não informa a raça; null explícito é recusado (NOT NULL).
            UUID semColuna = UUID.randomUUID();
            try (PreparedStatement comando = conexao.prepareStatement("""
                    insert into ficha (id, campanha_id, dono_id, tipo, nome, estado, criada_em, atualizada_em)
                    values (?, ?, ?, 'PJ', 'Ficha', cast(? as jsonb), ?, ?)
                    """)) {
                comando.setObject(1, semColuna);
                comando.setObject(2, dono.campanha());
                comando.setObject(3, dono.usuario());
                comando.setString(4, estado(50));
                comando.setObject(5, CRIADA_EM);
                comando.setObject(6, CRIADA_EM);
                comando.executeUpdate();
            }
            assertThat(valor(conexao, semColuna, "raca")).isEqualTo("HUMANO");
            assertThatThrownBy(() -> inserirFicha(conexao, UUID.randomUUID(), dono, null, estado(50)))
                .isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState())
                .isEqualTo("23502"); // not_null_violation

            // Rodar o mesmo SQL de novo não muda nenhuma linha (e os alter table não falham).
            assertThat(executarScript(conexao, "db/migration/V4__raca_humano_not_null.sql")).isZero();
            assertThat(valor(conexao, semRaca, "raca")).isEqualTo("HUMANO");
            assertThat(valor(conexao, bruxo, "raca")).isEqualTo("BRUXO");
        }
    }

    // ---------- Auxiliares ----------

    private record Dono(UUID usuario, UUID campanha) {
    }

    /** estado com outros campos além dos atributos, para conferir que nada além do esperado muda. */
    private static String estado(int forEDes) {
        return """
            {"atributos": {"FOR": %1$d, "CON": 55, "TAM": 60, "DES": %1$d, "APA": 45, "INT": 70, "POD": 65, "EDU": 60},
             "atuais": {"PV": 11, "SAN": 65},
             "condicoes": {"em-choque": 1},
             "criacao": {"concluida": true, "snapshot": {"atributos": {"FOR": 50, "DES": 50}}}}
            """.formatted(forEDes);
    }

    private static Dono inserirDono(Connection conexao) throws Exception {
        UUID usuario = UUID.randomUUID();
        UUID campanha = UUID.randomUUID();
        try (Statement comando = conexao.createStatement()) {
            comando.executeUpdate("insert into usuario (id, nome, email, senha_hash) values ('" + usuario
                + "', 'Ana', 'ana@teste.com', 'x')");
            comando.executeUpdate("insert into campanha (id, nome, sistema, codigo_convite) values ('" + campanha
                + "', 'Mesa', 'WAR_IF', 'ABC123')");
        }
        return new Dono(usuario, campanha);
    }

    private static void inserirFicha(Connection conexao, UUID id, Dono dono, String raca, String estado)
            throws Exception {
        try (PreparedStatement comando = conexao.prepareStatement("""
                insert into ficha (id, campanha_id, dono_id, tipo, nome, raca, estado, criada_em, atualizada_em)
                values (?, ?, ?, 'PJ', 'Ficha', ?, cast(? as jsonb), ?, ?)
                """)) {
            comando.setObject(1, id);
            comando.setObject(2, dono.campanha());
            comando.setObject(3, dono.usuario());
            comando.setString(4, raca);
            comando.setString(5, estado);
            comando.setObject(6, CRIADA_EM);
            comando.setObject(7, CRIADA_EM);
            comando.executeUpdate();
        }
    }

    /** Valor de uma expressão SQL (como texto) para a ficha; os parâmetros preenchem os "?" da expressão. */
    private static String valor(Connection conexao, UUID ficha, String expressao, String... parametros)
            throws Exception {
        try (PreparedStatement consulta = conexao.prepareStatement(
                "select cast(" + expressao + " as text) from ficha where id = ?")) {
            int i = 1;
            for (String parametro : parametros) {
                consulta.setString(i++, parametro);
            }
            consulta.setObject(i, ficha);
            try (ResultSet resultado = consulta.executeQuery()) {
                assertThat(resultado.next()).isTrue();
                return resultado.getString(1);
            }
        }
    }

    /** Roda o script da migration (pode ter vários comandos) e devolve o total de linhas alteradas. */
    private static int executarScript(Connection conexao, String caminho) throws Exception {
        String sql = new ClassPathResource(caminho).getContentAsString(StandardCharsets.UTF_8);
        int total = 0;
        try (Statement comando = conexao.createStatement()) {
            boolean temResultado = comando.execute(sql);
            while (true) {
                if (!temResultado) {
                    int linhas = comando.getUpdateCount();
                    if (linhas == -1) {
                        break;
                    }
                    total += linhas;
                }
                temResultado = comando.getMoreResults();
            }
        }
        return total;
    }

    private static Flyway flyway(String schema, String alvo) {
        return Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas(schema)
            .locations("classpath:db/migration")
            .target(alvo)
            .load();
    }

    private static Connection conectar(String schema) throws Exception {
        Connection conexao = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
            postgres.getPassword());
        conexao.setSchema(schema);
        return conexao;
    }
}
