package com.houseofthemaster.api.campanha;

import com.houseofthemaster.api.participacao.Participacao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "campanha")
@Getter
@Setter
public class Campanha {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String sistema;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    private Integer maxJogadores;

    @Column(nullable = false, unique = true, length = 12)
    private String codigoConvite;

    @OneToMany(mappedBy = "campanha")
    private List<Participacao> participacoes;
}