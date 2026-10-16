package com.houseofthemaster.api.ficha;

import com.houseofthemaster.api.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Linha do log de modificações de uma ficha (MODELO-FICHA.md seção 2.4). */
@Entity
@Table(name = "ficha_evento")
@Getter
@Setter
public class FichaEvento {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ficha_id")
    private Ficha ficha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id")
    private Usuario autor;

    /** Caminho completo da op do PATCH, ex.: atuais.PV, pericias.Furtividade.ganho. */
    @Column(nullable = false)
    private String campo;

    /** Valor escalar em JSON (número, texto, booleano ou null). */
    @JdbcTypeCode(SqlTypes.JSON)
    private Object anterior;

    @JdbcTypeCode(SqlTypes.JSON)
    private Object novo;

    @Column(nullable = false)
    private Instant em;
}
