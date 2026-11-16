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
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration V3 (FOR/DES do Vampiro = 120) contra dados reais: migra até a V2, insere fichas, migra até a V3
 * e confere que só o vampiro fora de 120 mudou, e só em estado.atributos.FOR/DES. Depois roda o mesmo
 * SQL de novo e confere que nada muda (idempotente). Sem Spring: Flyway e JDBC direto num Postgres vazio.
 */
@ExtendWith(DockerDisponivelOuFalhaNoCi.class)
@Testcontainers
class MigrationV3VampiroPostgresTest {

    private static final String V3 = "db/migration/V3__vampiro_for_des_120.sql";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    private static final UUID VAMPIRO_50 = UUID.randomUUID();
    private static final UUID VAMPIRO_120 = UUID.randomUUID();
    private static final UUID HUMANO_50 = UUID.randomUUID();

    @Test
    void corrigeSoForEDesDoVampiroForaDe120EEIdempotente() throws Exception {
        flyway("2").migrate();

        try (Connection conexao = conectar()) {
            UUID usuario = UUID.randomUUID();
            UUID campanha = UUID.randomUUID();
            executar(conexao, "insert into usuario (id, nome, email, senha_hash) values ('" + usuario
                + "', 'Ana', 'ana@teste.com', 'x')");
            executar(conexao, "insert into campanha (id, nome, sistema, codigo_convite) values ('" + campanha
                + "', 'Mesa', 'WAR_IF', 'ABC123')");
            inserirFicha(conexao, VAMPIRO_50, campanha, usuario, "VAMPIRO", 50);
            inserirFicha(conexao, VAMPIRO_120, campanha, usuario, "VAMPIRO", 120);
            inserirFicha(conexao, HUMANO_50, campanha, usuario, "HUMANO", 50);
        }

        assertThat(flyway("3").migrate().migrationsExecuted).isEqualTo(1);

        try (Connection conexao = conectar()) {
            // Vampiro 50/50: só FOR e DES mudaram; o resto do estado (inclusive o snapshot) e atualizada_em, não.
            assertThat(texto(conexao, VAMPIRO_50, "estado #>> '{atributos,FOR}'")).isEqualTo("120");
            assertThat(texto(conexao, VAMPIRO_50, "estado #>> '{atributos,DES}'")).isEqualTo("120");
            assertThat(texto(conexao, VAMPIRO_50, "estado = jsonb_set(jsonb_set(cast(? as jsonb),"
                + " '{atributos,FOR}', '120'), '{atributos,DES}', '120')", estado(50))).isEqualTo("true");
            assertThat(texto(conexao, VAMPIRO_50, "estado #>> '{criacao,snapshot,atributos,FOR}'")).isEqualTo("50");
            assertThat(texto(conexao, VAMPIRO_50, "atualizada_em = cast(? as timestamptz)", CRIADA_EM.toString()))
                .isEqualTo("true");

            // Vampiro já em 120 e humano: estado idêntico ao inserido.
            assertThat(texto(conexao, VAMPIRO_120, "estado = cast(? as jsonb)", estado(120))).isEqualTo("true");
            assertThat(texto(conexao, HUMANO_50, "estado = cast(? as jsonb)", estado(50))).isEqualTo("true");

            // Rodar o mesmo SQL de novo não muda nenhuma linha.
            String sql = new ClassPathResource(V3).getContentAsString(StandardCharsets.UTF_8);
            try (Statement comando = conexao.createStatement()) {
                assertThat(comando.executeUpdate(sql)).isZero();
            }
        }
    }

    private static final OffsetDateTime CRIADA_EM = OffsetDateTime.parse("2026-10-01T12:00:00Z");

    /** estado com outros campos além dos atributos, para conferir que só FOR/DES mudam. */
    private static String estado(int forEDes) {
        return """
            {"atributos": {"FOR": %1$d, "CON": 55, "TAM": 60, "DES": %1$d, "APA": 45, "INT": 70, "POD": 65, "EDU": 60},
             "atuais": {"PV": 11, "SAN": 65},
             "condicoes": {"em-choque": 1},
             "criacao": {"concluida": true, "snapshot": {"atributos": {"FOR": 50, "DES": 50}}}}
            """.formatted(forEDes);
    }

    private static void inserirFicha(Connection conexao, UUID id, UUID campanha, UUID dono, String raca, int forEDes)
            throws Exception {
        try (PreparedStatement comando = conexao.prepareStatement("""
                insert into ficha (id, campanha_id, dono_id, tipo, nome, raca, estado, criada_em, atualizada_em)
                values (?, ?, ?, 'PJ', 'Ficha', ?, cast(? as jsonb), ?, ?)
                """)) {
            comando.setObject(1, id);
            comando.setObject(2, campanha);
            comando.setObject(3, dono);
            comando.setString(4, raca);
            comando.setString(5, estado(forEDes));
            comando.setObject(6, CRIADA_EM);
            comando.setObject(7, CRIADA_EM);
            comando.executeUpdate();
        }
    }

    /** Valor de uma expressão SQL (como texto) para a ficha; os parâmetros preenchem os "?" da expressão. */
    private static String texto(Connection conexao, UUID ficha, String expressao, String... parametros)
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

    private static void executar(Connection conexao, String sql) throws Exception {
        try (Statement comando = conexao.createStatement()) {
            comando.executeUpdate(sql);
        }
    }

    private static Flyway flyway(String alvo) {
        return Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .target(alvo)
            .load();
    }

    private static Connection conectar() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
