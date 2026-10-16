-- Fichas (MODELO-FICHA.md, seções 2.1 a 2.4). Nada calculado é guardado.

create table ficha (
    id            uuid                        not null,
    campanha_id   uuid                        not null,
    dono_id       uuid                        not null,
    tipo          varchar(255)                not null,
    nome          varchar(255)                not null,
    ocupacao      varchar(255),
    raca          varchar(255),
    estado        jsonb                       not null,
    arquivada     boolean                     not null default false,
    criada_em     timestamp(6) with time zone not null,
    atualizada_em timestamp(6) with time zone not null,
    constraint pk_ficha primary key (id),
    constraint ck_ficha_tipo check (tipo in ('PJ', 'NPC')),
    constraint ck_ficha_raca check (raca in ('HUMANO', 'CACADOR', 'BRUXO', 'VAMPIRO', 'LOBISOMEM', 'VIAJANTE')),
    constraint fk_ficha_campanha foreign key (campanha_id) references campanha (id),
    constraint fk_ficha_dono foreign key (dono_id) references usuario (id)
);

create index ix_ficha_campanha on ficha (campanha_id);
create index ix_ficha_dono on ficha (dono_id);

-- valorInicial não é gravado: vem do catálogo do sistema.
create table ficha_pericia (
    id           uuid         not null,
    ficha_id     uuid         not null,
    nome         varchar(255) not null,
    categoria    varchar(255) not null,
    ganho        integer      not null default 0,
    ganho_treino integer      not null default 0,
    origem       varchar(255) not null,
    constraint pk_ficha_pericia primary key (id),
    -- também serve de índice para ficha_id
    constraint uk_ficha_pericia_ficha_nome unique (ficha_id, nome),
    constraint ck_ficha_pericia_categoria check (categoria in ('FISICA', 'COMBATE', 'MENTAL', 'INTERPESSOAL', 'DESCONHECIDA')),
    constraint ck_ficha_pericia_origem check (origem in ('CATALOGO', 'CUSTOM')),
    constraint fk_ficha_pericia_ficha foreign key (ficha_id) references ficha (id)
);

create table ficha_evento (
    id       uuid                        not null,
    ficha_id uuid                        not null,
    autor_id uuid                        not null,
    campo    varchar(255)                not null,
    anterior jsonb,
    novo     jsonb,
    em       timestamp(6) with time zone not null,
    constraint pk_ficha_evento primary key (id),
    constraint fk_ficha_evento_ficha foreign key (ficha_id) references ficha (id),
    constraint fk_ficha_evento_autor foreign key (autor_id) references usuario (id)
);

-- Log paginado da ficha (ordem decrescente por em).
create index ix_ficha_evento_ficha_em on ficha_evento (ficha_id, em desc);
-- Junção anti-flood: último evento da mesma ficha + campo.
create index ix_ficha_evento_ficha_campo_em on ficha_evento (ficha_id, campo, em desc);
