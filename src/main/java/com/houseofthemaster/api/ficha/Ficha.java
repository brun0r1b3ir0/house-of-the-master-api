package com.houseofthemaster.api.ficha;

import com.houseofthemaster.api.campanha.Campanha;
import com.houseofthemaster.api.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ficha")
@Getter
@Setter
public class Ficha {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campanha_id")
    private Campanha campanha;

    /** Quem controla a ficha: o jogador (PJ) ou o Mestre (NPC). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dono_id")
    private Usuario dono;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoFicha tipo;

    @Column(nullable = false)
    private String nome;

    private String ocupacao;

    @Enumerated(EnumType.STRING)
    private Raca raca;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private FichaEstado estado;

    @Column(nullable = false)
    private boolean arquivada;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant criadaEm;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant atualizadaEm;

    @OneToMany(mappedBy = "ficha", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FichaPericia> pericias = new ArrayList<>();

    public void adicionarPericia(FichaPericia pericia) {
        pericia.setFicha(this);
        pericias.add(pericia);
    }
}
