package com.houseofthemaster.api.participacao;

import com.houseofthemaster.api.campanha.Campanha;
import com.houseofthemaster.api.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
    name = "participacao",
    uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "campanha_id"})
)
@Getter
@Setter
public class Participacao {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(optional = false)
    @JoinColumn(name = "campanha_id")
    private Campanha campanha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Papel papel;
}