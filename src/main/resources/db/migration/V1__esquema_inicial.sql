-- Esquema inicial: as tabelas que existiam antes do Flyway (geradas pelo Hibernate com ddl-auto=update).
-- Tipos e nomes de tabela/coluna iguais aos que o Hibernate espera, para passar no ddl-auto=validate.

create table usuario (
    id         uuid         not null,
    nome       varchar(255) not null,
    email      varchar(255) not null,
    senha_hash varchar(255) not null,
    constraint pk_usuario primary key (id),
    constraint uk_usuario_email unique (email)
);

create table campanha (
    id             uuid         not null,
    nome           varchar(255) not null,
    sistema        varchar(255) not null,
    descricao      text,
    max_jogadores  integer,
    codigo_convite varchar(12)  not null,
    constraint pk_campanha primary key (id),
    constraint uk_campanha_codigo_convite unique (codigo_convite)
);

create table participacao (
    id          uuid         not null,
    usuario_id  uuid         not null,
    campanha_id uuid         not null,
    papel       varchar(255) not null,
    constraint pk_participacao primary key (id),
    constraint uk_participacao_usuario_campanha unique (usuario_id, campanha_id),
    constraint ck_participacao_papel check (papel in ('MESTRE', 'JOGADOR')),
    constraint fk_participacao_usuario foreign key (usuario_id) references usuario (id),
    constraint fk_participacao_campanha foreign key (campanha_id) references campanha (id)
);

-- usuario_id já é coberto pelo unique (usuario_id, campanha_id); campanha_id precisa do seu.
create index ix_participacao_campanha on participacao (campanha_id);
