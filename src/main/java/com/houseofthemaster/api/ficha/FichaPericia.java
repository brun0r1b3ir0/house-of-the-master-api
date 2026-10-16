package com.houseofthemaster.api.ficha;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Perícia de uma ficha. O valorInicial NÃO é gravado: vem do catálogo do sistema
 * (número fixo ou fórmula que acompanha o atributo). valor = valorInicial + ganho.
 */
@Entity
@Table(
    name = "ficha_pericia",
    uniqueConstraints = @UniqueConstraint(columnNames = {"ficha_id", "nome"})
)
@Getter
@Setter
public class FichaPericia {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ficha_id")
    private Ficha ficha;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaPericia categoria;

    /** Pontos acima do inicial, editáveis pelo dono. */
    @Column(nullable = false)
    private int ganho;

    /** Pontos ganhos por treinamento (Desgaste de Perícias). Ainda sem uso. */
    @Column(nullable = false)
    private int ganhoTreino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigemPericia origem;
}
