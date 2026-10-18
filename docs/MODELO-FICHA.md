# House of the Master — Modelo da Ficha (War If)

> Decisões de 15/10/2026. Base: `ficha-war-if.html`, `WARIF_calculos_ficha_personagem.md`, `especificacao_funcional_campanhas_rpg.md`. Substitui a seção "O que NÃO está pronto pra modelar" do `CONTRATO-DADOS.md`.

---

## 1. Decisões fechadas

| # | Decisão |
|---|---|
| 1 | **Atributos não têm base.** Existe só o valor (editável pelo jogador) e o efetivo (calculado). |
| 2 | **Perícias têm `valorInicial`** (imutável, definido na criação da ficha, ex.: Ciência Forense = 1) e `valor` (editável pelo jogador). Nem o Mestre altera o inicial. O inicial é só referência na tela (não é piso — ver pendências). |
| 3 | **Jogador controla os próprios contadores** de condição (Exaustão, Fome, Sede, Debilitantes, Frio, Sangria, Verbena). |
| 4 | **Estágio de Sanidade é manual** (guardado na ficha, alterado pelo jogador). Habilidades podem mexer nele. Isso também resolve a histerese descrita no texto das regras. |
| 5 | **Mestre só visualiza** fichas de jogadores. Qualquer alteração deve ser combinada com o jogador. |
| 6 | **Jogador só vê as próprias fichas.** |
| 7 | **Salvamento automático a cada ação** (sem botão "Salvar") + **Log de modificações** consultável pelo Mestre. Visão ao vivo e notificações vêm depois. |
| 8 | **Modificadores aplicados uma única vez.** Sem propagação automática Mental → Interpessoal: cada fonte lista explicitamente suas categorias. Ex.: Alerta Vermelho = Interpessoal −15 (não −30). |
| 9 | **Novas condições do sistema** ficam num segundo card recolhível, abaixo das condições clássicas. A lista é a seção Condições do Sistemático, **completa**, incluindo as que são só junções de outras (evita confusão). Algumas usam fração do valor efetivo (ex.: Confuso: INT/EDU −1/4). |
| 10 | **Frações** agem sobre o valor efetivo ("valor máximo" = efetivo) e empilham em sequência: 1/4 do total, depois 1/4 do resultado, e assim por diante. |
| 11 | **Caçador:** o bônus de +5 em Mental inclui Interpessoal. O texto do Sistemático está errado. |
| 12 | **Vampiro** é isento de Fome, Sono e Sede. |
| 13 | **Frio Moderado = −1/4 CON.** **Verbena** segue o código. **Linhagem Verdadeira** (Lobisomem) não é implementada por enquanto. |
| 14 | **Alerta Vermelho** penaliza todas as perícias de Combate e as Físicas marcadas como "exigem precisão". |
| 15 | **Esquivar** e **Língua Nativa** acompanham DES e EDU ao vivo. Por isso a ficha guarda o *ganho* acima do inicial em todas as perícias. |
| 16 | **Primórdios** é perícia sem categoria (`DESCONHECIDA`). **Lidar com Animais** é o nome oficial da perícia (o +20 do Lobisomem vale para ela). **Olfato** é exclusiva do Lobisomem. Atletismo e Investigar não existem. |
| 17 | **Perícia mágica** = a raça dá bônus nela. Não é guardada, é calculada. |
| 18 | **Esquivar** usa a DES **digitada** (não a efetiva). |
| 19 | **EH** conta as perícias pelo valor digitado, inclusive as mágicas (o bônus da raça não entra no valor digitado). |

---

## 2. Entidades

### 2.1 `Ficha`

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID | |
| campanhaId | FK Campanha | toda rota valida `ficha.campanhaId == campanhaId da URL` |
| donoId | FK Usuario | quem controla a ficha (jogador, ou Mestre no caso de NPC) |
| tipo | enum `PJ` \| `NPC` | |
| nome | String | |
| ocupacao | String | |
| raca | enum nulo: `HUMANO, CACADOR, BRUXO, VAMPIRO, LOBISOMEM, VIAJANTE` | o campo "Jogador" do mock é derivado de `dono.nome` |
| estado | JSONB | ver 2.2 |
| arquivada | boolean | ficha de quem saiu da campanha fica arquivada, não apagada |
| criadaEm / atualizadaEm | timestamp | |

### 2.2 `Ficha.estado` (JSONB, validado por DTO no Spring)

Só dados que alguém digitou. **Nada calculado é salvo** (efetivo, ½, ⅕, PV/PE/PM máximos, Corpo, MOV, Rank, EH).

```json
{
  "atributos":   { "FOR": 50, "CON": 50, "TAM": 50, "DES": 50, "APA": 50, "INT": 50, "POD": 50, "EDU": 50 },
  "atuais":      { "PV": 10, "PE": 10, "PM": 10, "SAN": 100, "SRT": 100 },
  "estagioSanidade": "MENTE_SA",
  "contadores":  { "exaustao": 0, "fome": 0, "sede": 0, "debilitantes": 0, "frio": 0, "sangria": 0 },
  "perdaPermanenteTam": 0,
  "flags":       { "verbena": false, "colapsoMagico": false },
  "alcateia":    { "ativa": false, "membros": 0, "papel": "membro", "linhagem": "normal" },
  "abates":      { "vampiros": 0, "outros": 0 },
  "overrides":   {},
  "regenOverrides": {},
  "condicoes":   { "chave-da-condicao-nova": 0 },
  "criacao":     { "concluida": false }
}
```

`condicoes` guarda o valor de cada condição do catálogo novo (contador ou liga/desliga), indexada pela chave.

`criacao` (criação de personagem):
- PJ nova: `{ "concluida": false }` (o front mostra "Criação não concluída"). NPCs e fichas antigas: ausente/`null`.
- Depois de `POST .../criacao/concluir`: `{ "concluida": true, "profissaoAtributos": [principal, secundario], "periciasProfissao": [7 nomes], "snapshot": { "atributos": {...}, "ganhos": { "profissao": {...}, "interesse": {...} } }, "concluidaEm": "<ISO-8601>" }`.
- Não é editável pelo PATCH; só o endpoint de conclusão o altera.

Hibernate 6: `@JdbcTypeCode(SqlTypes.JSON)` no campo. Funciona no Postgres (`jsonb`) e no H2 dos testes.

### 2.3 `FichaPericia`

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID | |
| fichaId | FK | unique (fichaId, nome) |
| nome | String | |
| categoria | enum `FISICA, COMBATE, MENTAL, INTERPESSOAL, DESCONHECIDA` | Desconhecida (Primórdios): nenhum modificador de categoria a afeta |
| valorInicial | — | **não é gravado**: vem do catálogo (número fixo, ou fórmula que acompanha o atributo ao vivo: Esquivar = DES/2, Língua Nativa = EDU). Imutável. Ver `claude/CATALOGO-PERICIAS.md` |
| ganho | int | editável pelo dono; `valor = valorInicial + ganho` (a tela mostra o valor e converte) |
| ganhoTreino | int | pontos ganhos por treinamento; necessário para o Desgaste de Perícias (regride só até metade dos pontos ganhos) |
| origem | enum `CATALOGO` \| `CUSTOM` | CUSTOM só se for permitido (pendência) |

A coluna "Mágico" **não é guardada**: a perícia é mágica quando a raça do personagem dá bônus nela (Caçador: todas; Vampiro: Físicas; Lobisomem: Físicas, Combate, Lidar com Animais e Olfato).

### 2.4 `FichaEvento` (Log de modificações)

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID | |
| fichaId | FK | índice (fichaId, em desc) |
| autorId | FK Usuario | |
| campo | String | caminho completo da op do PATCH, ex.: `atuais.PV`, `pericias.Furtividade.ganho`, `contadores.fome` |
| anterior / novo | JSON | |
| em | timestamp | |

**Anti-flood (o Mestre não é inundado):** se o último evento do mesmo campo da ficha é do mesmo autor e tem menos de 30 s (`hotm.ficha.log.janela-juncao-segundos`), o backend atualiza esse evento (mantém `anterior` do primeiro, troca `novo` e `em`) em vez de criar outro. Ex.: PV 10→8→7→6 vira uma linha "PV 10 → 6". Se a junção voltar ao valor original, o evento é apagado. Só gera evento o campo que de fato mudou; ops repetidas no mesmo campo, no mesmo PATCH, viram um evento só. **Exceção:** a troca de `raca` nunca é juntada (trocar e voltar em menos de 30 s deixa os dois registros). A conclusão da criação gera um único evento `criacao.concluida` (`false → true`). O Mestre consulta o log quando quiser; não há push por padrão.

### 2.5 Catálogos (do sistema War If, versionados no repositório)

- **Perícias:** nome, categoria, `valorInicial`, `exigePrecisao` (Alerta Vermelho só penaliza perícias físicas/combate "que exijam precisão": Armas de Fogo, Furtividade, Prestidigitação, Arremesso etc.) e quais raças a tornam "mágica".
- **Condições novas:** chave, nome, tipo (`CONTADOR` | `LIGA_DESLIGA` | `ENUM`, este para os graus de Em Choque e o tipo de fogo de Em Chamas), min, max, `inclui` (lista de outras condições que ela implica) e pacote de modificadores (2.6). **Todas** as condições do Sistemático entram no catálogo, inclusive as compostas. Ativar uma composta mostra as incluídas como "via Inconsciente" (derivadas, não gravadas); cada condição conta uma vez só, mesmo que venha por dois caminhos.

Começam como arquivos JSON no backend. Viram tabela se o Mestre puder criar condições próprias da campanha (futuro).

### 2.6 Pacote de modificadores (v2)

Para suportar condições com fração (ex.: Confuso: INT e EDU −1/4; Enfraquecido: FOR/CON/DES −1/2):

```json
{
  "atributos": { "INT": { "soma": 0, "fracao": -0.25 } },
  "pericias":  { "FISICA": { "soma": -5 } },
  "periciasPorNome": { "Olfato": { "soma": 10 } },
  "pvFlat": 0,
  "movFlat": 0,
  "deslocamentoFracao": 0
}
```

`deslocamentoFracao` age sobre a distância em metros, não sobre o número de MOV (Lento = −1/2; Imobilizado = 0).

Ordem de aplicação (definida):

1. soma de todos os `soma` de todas as fontes;
2. `fracao` sobre o valor efetivo (resultado do passo 1). Frações de fontes diferentes acumulam em sequência: 1/4 do total, depois 1/4 do resultado, e assim por diante (equivale a multiplicar os (1 − f) de cada uma). Arredonda para baixo uma vez, no fim, para o resultado não depender da ordem;
3. valores fixos (`setAttrs`: Vampiro FOR/DES = 120, Frio Crítico CON = 1);
4. multiplicadores de Vampiro (Sangria/Verbena);
5. piso em 0.

Hoje o passo 2 só existe no Vampiro (passo 4). Condições com fração normais entram no 2. O Frio Moderado (−1/4 CON) e o Severo (−1/2 CON) passam a usar esse passo; antes subtraíam uma fração da CON digitada.

**Condições do Sistemático com efeito numérico na ficha:**

| Condição | Efeito |
|---|---|
| Atordoado | inclui Tonto · DES máx −1/4 |
| Surdo | DES máx −1/4 |
| Enfraquecido | FOR, CON e DES máx −1/2 (TAM não) |
| Confuso | INT e EDU máx −1/4 |
| Enjoado | CON −10 |
| Irritado | aplica os efeitos de Estresse Leve, mas só se o estágio atual for melhor que Estresse Leve (não soma com ele) |
| Em Chamas | APA máx −1 permanente a cada 3 de dano de queimadura acumulado; prejuízo em DES (valor não definido) |
| Lento / Imobilizado | deslocamento ×1/2 / ×0 |

As demais (Privilegiado/Desprivilegiado, Desprevenido, Tonto, Caído, Engajado, Vulnerável, Obscurecido, Cego, Mudo, Agarrado, Incapacitado, Inconsciente, Sufocado, Em Choque) são indicadores e composições sem número na ficha. Exemplos de composição: Inconsciente = Incapacitado + Cego + Mudo + Vulnerável; Em Choque (Leve/Parcial/Grave/Gravíssimo) = combinações de Enjoado, Tonto, Atordoado, Incapacitado, Confuso, Irritado e Surdo. A ficha mostra a condição ativa e, ao passar o mouse ou tocar, o texto da regra.

### 2.7 Correção da propagação Mental → Interpessoal

Remover `withInterpessoalPropagation`. Cada fonte passa a listar Interpessoal explicitamente.

- **Estágios de Sanidade:** os valores já estão listados no próprio registro, então basta tirar a propagação. Resultado: Plenitude/Apreço Int +10; Alerta Vermelho Int −15; Estado Crítico Int −20; Colapso/Inferno −20.
- **Exaustão e Sede:** o Sistemático lista só Físicas e Mentais; seguindo o texto, **Interpessoal não é afetado**.
- **Caçador:** o texto do Sistemático está errado; o bônus de +5 em Mental **inclui Interpessoal** (+5).

---

## 3. Permissões (aplicadas no backend)

| Ação | Jogador dono | Mestre | Outro jogador |
|---|---|---|---|
| Listar fichas da campanha | só as suas | todas (PJ + NPC) | só as suas |
| Ver ficha de PJ | sim | sim (leitura) | **404** |
| Editar ficha de PJ | sim | **não** | 404 |
| Criar ficha | PJ próprio | NPC | — |
| Ver log de PJ | sim (da própria) | sim | 404 |
| Alterar `valorInicial` | não | não | — |
| Editar NPC | — | sim | 404 |

Padrão já usado no projeto: quem não deve saber que o recurso existe recebe 404. A validação de participação na campanha vem antes de tudo.

---

## 4. API

| Método | Rota | Descrição |
|---|---|---|
| GET | `/campanhas/{cid}/fichas` | lista conforme permissão |
| POST | `/campanhas/{cid}/fichas` | cria ficha (e as `FichaPericia` iniciais a partir do catálogo) |
| GET | `/campanhas/{cid}/fichas/{fid}` | ficha completa (estado + perícias) |
| PATCH | `/campanhas/{cid}/fichas/{fid}` | altera campos, ver abaixo |
| DELETE | `/campanhas/{cid}/fichas/{fid}` | arquiva |
| GET | `/campanhas/{cid}/fichas/{fid}/eventos?antes=...&limite=...` | log paginado, mais recentes primeiro (`antes` = cursor ISO-8601; `limite` padrão 50, máximo 200) |
| GET | `/campanhas/{cid}/sistema` | catálogos (perícias, condições novas) |
| POST | `/campanhas/{cid}/fichas/{fid}/criacao/concluir` | conclui a criação de personagem (atômico; ver 4.1) |

PATCH recebe uma lista de operações, cada uma com **campo da lista permitida** + valor:

```json
{ "ops": [ { "campo": "atuais.PV", "valor": 7 }, { "campo": "pericias.Furtividade.ganho", "valor": 20 } ] }
```

**Lista permitida (final).** Tetos e valores aceitos ficam em `FichaLimites` (backend); são limites técnicos, não regras de jogo.

| Campo | Valor aceito |
|---|---|
| `nome` | texto não vazio, até 120 caracteres |
| `ocupacao` | texto até 120 caracteres, ou `null` |
| `raca` | `HUMANO`, `CACADOR`, `BRUXO`, `VAMPIRO`, `LOBISOMEM`, `VIAJANTE` ou `null` |
| `estagioSanidade` | um dos 8 estágios (`PLENITUDE` … `INFERNO`) |
| `atributos.<FOR\|CON\|TAM\|DES\|APA\|INT\|POD\|EDU>` | inteiro 0–9999 |
| `atuais.<PV\|PE\|PM\|SAN\|SRT>` | inteiro 0–9999 (sem validar contra o máximo) |
| `contadores.<exaustao\|fome\|sede\|debilitantes\|frio>` | inteiro 0–9999 |
| `contadores.sangria` | inteiro 0–10 |
| `perdaPermanenteTam` | inteiro 0–9999 |
| `flags.<verbena\|colapsoMagico>` | `true` / `false` |
| `condicoes.<chave>` | inteiro 0–9999; chave `^[a-z0-9]+(-[a-z0-9]+)*$`, até 64 caracteres |
| `pericias.<nome>.ganho` | inteiro 0–9999; a perícia precisa existir na ficha. `pericias.Olfato.ganho` só se a raça (corrente no PATCH) for `LOBISOMEM` |
| `alcateia.ativa` | `true` / `false` |
| `alcateia.membros` | inteiro 0–9999 |
| `alcateia.papel` | `membro`, `alfa`, `alfa-secundario` |
| `alcateia.linhagem` | `normal`, `verdadeira` |
| `abates.<vampiros\|outros>` | inteiro 0–9999 |
| `overrides.<PV\|PE\|PM\|MOV\|Corpo\|Conhecimento>` | inteiro 0–9999, ou `null` para **remover** a exceção (a chave sai do mapa) |
| `regenOverrides.<PV\|PE\|PM>` | inteiro 0–99, ou `null` para **remover** a exceção |

- Qualquer outro campo → 400, inclusive `pericias.X.valorInicial` e `pericias.X.ganhoTreino`.
- Até 100 ops por PATCH; caminho do campo até 200 caracteres.
- Atômico: se qualquer op for inválida, nenhuma é aplicada (a resposta indica qual: `ops[i]`).
- Ordem das verificações: participação (404), permissão (403), validação (400).
- Ops avaliadas NA ORDEM: `raca → LOBISOMEM` seguida de `pericias.Olfato.ganho` passa; a ordem inversa dá 400.
- Trocar a `raca` para algo diferente de `LOBISOMEM` zera `ganho` e `ganhoTreino` de Olfato na mesma transação (com eventos no log). `ganhoTreino` continua fora da lista do PATCH.
- Log: só gera evento o campo que de fato mudou; remover uma exceção gera evento com `novo = null`; `null` numa exceção que não existe não muda nada.
- "Atual ≤ máximo" é clampado no frontend (o máximo depende do cálculo).
- Como só o dono escreve em PJ, não há disputa de edição; sem controle de versão por enquanto (última escrita vence).

### 4.1 Conclusão da criação

`POST /campanhas/{cid}/fichas/{fid}/criacao/concluir`

```json
{
  "atributos": { "FOR": 50, "CON": 50, "TAM": 60, "DES": 70, "APA": 50, "INT": 70, "POD": 50, "EDU": 60 },
  "profissao": { "principal": "DES", "secundario": "EDU", "pericias": ["... 7 nomes"] },
  "ganhos": { "profissao": { "Furtividade": 60 }, "interesse": { "Medicina": 50 } }
}
```

Respostas: 200 com a ficha completa (mesmo formato do GET). Erros em texto simples, nesta ordem: 404 (não participante, outro jogador, ficha arquivada ou de outra campanha), 403 (Mestre numa PJ), 409 "Esta ficha não está em criação." (já concluída, NPC, ficha antiga sem `criacao`), 400 (regras).

Regras (o servidor recalcula tudo com os atributos recebidos):
- 8 atributos, cada um 15–90, soma exatamente 460; a raça da ficha precisa estar definida.
- `principal` ∈ {FOR, DES, APA, EDU, INT}; `secundario` ∈ {EDU, INT}; iguais (EDU+EDU, INT+INT) valem 4× o atributo.
- `profissao.pericias`: 7 nomes distintos do catálogo, ao menos 1 INTERPESSOAL.
- Primórdios nunca recebe pontos na criação (nem entre as 7, nem em `ganhos`); Olfato só para LOBISOMEM.
- `ganhos.profissao` só nas 7; `ganhos.interesse` em qualquer perícia permitida à raça; pontos inteiros de 1 a 80.
- Valor final = valorInicial (Esquivar = ⌊DES/2⌋, Língua Nativa = EDU, com os atributos digitados) + pontos ≤ 80, para cada perícia que recebe pontos.
- Orçamentos: profissão = 2×principal + 2×secundario; interesse = 2×INT. Gasto ≤ orçamento. Sobra só é aceita quando o teto de 80 impede de colocar o resto em qualquer perícia elegível; senão, 400 "Faltam gastar N pontos de …".

Efeito (uma transação): grava os 8 atributos; o `ganho` de cada perícia passa a ser profissão + interesse (as demais voltam a 0; ganhos anteriores são substituídos); `estado.criacao` vira concluída (formato em 2.2); um único evento `criacao.concluida` no log.

---

## 5. Onde fica o cálculo

Uma única implementação em JS puro, `src/lib/warif/` no frontend, portada do `WARIF_calculos_ficha_personagem.md` **com testes** (Vitest), usada pela ficha do jogador e pela visão do Mestre. O Spring guarda, valida formato e faixas e aplica permissões. Se um dia for preciso validar regras no servidor, portar para Java reaproveitando os mesmos casos de teste.

---

## 6. Ajustes no design (`ficha-war-if.html`)

- Atributos: coluna "(Padrão) Efetivo" → "Valor" (campo) + "Efetivo". Atributos não têm base.
- Perícias: coluna "Base" → "Valor"; "(Padrão)" → "(Inicial)".
- Estágios de Sanidade: manter os rádios (agora estado real). Mostrar os efeitos do estágio selecionado numa linha visível (tooltip `title` não funciona no toque).
- Adicionar o card **"Novas condições"** recolhível, abaixo das condições clássicas.
- Corrigir os números do mock: com atributos 50, PV = 10, PE = 10 (mock mostra 5); Corpo Passivo do vampiro com FOR 120 + TAM 50 = 170 → Corpo 2 (+1D6).
- Mestre abre a ficha em modo somente leitura (campos desabilitados, sem botões +/−) e com acesso ao Log.

---

## 7. Fases

1. **Ficha Geral:** `Ficha`, `FichaPericia`, catálogo de perícias, condições clássicas, autosave (PATCH), permissões, log com anti-flood. Módulo de cálculo + testes.
2. **Novas condições:** depende da ordem de aplicação (2.6) e das divergências da seção 9.
3. **Abas Combate, Habilidades, Treinamento, Inventário e aba da raça.**
4. **Ao vivo para o Mestre** via SSE (o navegador não fala direto com o Spring, então passa por uma rota do Next) e, por último, **notificações** com filtros.

Antes da fase 1: considerar Flyway (hoje `ddl-auto=update`), porque as tabelas começam a multiplicar.

---

## 8. Pendências

- `valorInicial` é só referência ou piso (o valor da perícia pode ficar abaixo dele)?
- Jogador pode criar perícias próprias (`CUSTOM`)?
- Quantas fichas PJ por jogador em uma campanha?
- Catálogo de perícias recebido (parcial) em `claude/CATALOGO-PERICIAS.md`. As Físicas com `exigePrecisao` foram escolhidas por mim (confirmar); itens em aberto estão no fim daquele documento.
- Exaustão e Sede: confirmar que não afetam Interpessoal (o Sistemático não lista).
- Vampiro isento de Sono: o contador de Exaustão fica desativado para ele? (Exaustão também pode vir de outras fontes.) O Frio continua valendo para ele.
- Em Chamas: qual é o prejuízo em DES?
- Onde guardar os Pontos de Mestre (a cada 2 PP gastos pelos jogadores, o Mestre ganha 1): é um contador da campanha.
- Destino da ficha quando o jogador sai ou é removido: sugerido arquivar, Mestre mantém leitura.

---

## 9. Divergências entre os cálculos antigos e o Sistemático

Cruzei `WARIF_calculos_ficha_personagem.md` com o Sistemático (War If — Sistemática). A maior parte bate: sanidade, exaustão, fome, sede, debilitantes, sangria, colapso mágico, EH, MOV, Rank e crédito. **Decisões (15/10):** 1 → ¼ · 2 → todas as de Combate + Físicas marcadas (lista a enviar) · 3 → isento de Fome, Sono e Sede (Frio não) · 4 → manter o código · 5 → não implementar · 6 a 8 → ainda em aberto.

O que difere:

| # | Assunto | Código antigo | Sistemático | Sugestão |
|---|---|---|---|---|
| 1 | Frio Moderado | CON − ⌊CON base / 3⌋ | CON −1/4 | seguir o texto |
| 2 | Alerta Vermelho | −10 em todas as perícias Físicas e de Combate | só as que exigem precisão | flag `exigePrecisao` no catálogo |
| 3 | Vampiro e Frio/Sono | imune só a Fome e Sede | imune a Frio, Fome, Sede e Sono | esconder Frio; Exaustão: ver pendência |
| 4 | Verbena | atributos (menos TAM/APA) ×1/2, todas as perícias ×1/2, MOV ×3/4 | atributos e perícias **físicas** à metade, por 1d6+1 h | seguir o texto; a exceção TAM/APA e o MOV ×3/4 não aparecem no texto |
| 5 | Lobisomem, Linhagem Verdadeira | valores definidos | "características ainda desconhecidas" | não implementar até existir regra |
| 6 | Cura do Lobisomem | só com alcateia | sem alcateia +1 PV/30 min; com alcateia +1 PV/10 min; alfas +1 PV/min | incluir a base |
| 7 | Faixas de Rank | magia S 21+, PB S 361+ | magia S 21–25, PB S 361–400 | usar o texto |
| 8 | Durabilidade | categorias de arma + "frágil" ÷2 | armadura = redução × 10, escudo 20/30, material; sem "frágil" | revisar na fase do inventário |

**Regras que o código antigo não tinha** (entram nas fases 3 em diante):
- **Classes Humanas** (Habilidoso a Extraordinário): dependem de quantas perícias têm 50%+ e dos EH, e dão PE por hora, PE máximo e ação bônus.
- **Pontos de Protagonismo** (jogador) e **Pontos de Mestre** (contador da campanha).
- **Requisito de habilidade:** cada habilidade exige um mínimo (30/40/50/60%) em uma perícia ou atributo.
- **Treinamento e desgaste:** +1% na metade do tempo, +2% completo, teto de +10% por Level Up, regressão até metade dos pontos ganhos (`ganhoTreino`). No Vampiro, o teto de treino depende do valor na transformação.
- **Bruxo:** Além do Limite (1 PV por 4 PM), dano auto-infligido, canalização e o **construtor de magias por Pontos de Conjuração** (até 125 PC). O construtor é um módulo próprio, fora da fase 1.
