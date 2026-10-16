# House of the Master — Catálogo de Perícias (War If), versão parcial

> Enviado pelo Bruno em 15/10/2026 ("boa parte" das perícias), com as respostas do mesmo dia. Total: 71 perícias (Combate 17, Física 12, Mental 36, Interpessoal 5, Desconhecida 1). Alimenta `FichaPericia` (ver `claude/MODELO-FICHA.md`, seção 2.3).

**Valor inicial:** número fixo, ou fórmula que acompanha o atributo ao vivo (Esquivar = DES/2, Língua Nativa = EDU). A ficha guarda só o **ganho** acima do inicial: `valor = inicial + ganho`.

**Flag `exigePrecisao`** (usada pelo Alerta Vermelho): todas as de Combate, conforme decidido. Entre as Físicas, a escolha foi minha, com o critério "exige controle motor fino": Furtividade, Prestidigitação, Chaveiro, Dirigir Automóveis e Pilotar. Ficam sem a flag: Cavalgar, Escalar, Saltar, Nadar, Encontrar, Escutar e Olfato. **Confirmar.**

**Perícia "mágica":** é a perícia em que a raça do personagem dá bônus. Não é guardada, é calculada:
- Caçador: todas (bônus em Física, Combate, Mental e Interpessoal).
- Vampiro: todas as Físicas (+20).
- Lobisomem: todas as Físicas e de Combate (+10), Lidar com Animais (+20) e Olfato.
- Humano, Bruxo e Viajante: nenhuma.

| Perícia | Categoria | Valor inicial | Exige precisão | Observação |
|---|---|---|---|---|
| Armas de Fogo (Arcos) | Combate | 15 | sim |  |
| Armas de Fogo (Lança-Chamas) | Combate | 10 | sim |  |
| Armas de Fogo (Pistolas) | Combate | 20 | sim |  |
| Armas de Fogo (Submetralhadoras) | Combate | 15 | sim |  |
| Armas de Fogo (Armas Pesadas) | Combate | 10 | sim |  |
| Armas de Fogo (Metralhadoras) | Combate | 10 | sim |  |
| Armas de Fogo (Rifles/Espingardas) | Combate | 25 | sim |  |
| Lutar (Briga) | Combate | 25 | sim |  |
| Lutar (Chicote) | Combate | 5 | sim |  |
| Lutar (Garrote) | Combate | 15 | sim |  |
| Lutar (Machado) | Combate | 15 | sim |  |
| Lutar (Motosserra) | Combate | 10 | sim |  |
| Lutar (Espada) | Combate | 20 | sim |  |
| Lutar (Lança) | Combate | 20 | sim |  |
| Lutar (Mangual) | Combate | 10 | sim |  |
| Arremessar | Combate | 20 | sim |  |
| Esquivar | Combate | DES/2 | sim | inicial = metade da DES digitada, acompanha o atributo ao vivo |
| Cavalgar | Física | 5 |  |  |
| Dirigir Automóveis | Física | 20 | sim |  |
| Escalar | Física | 20 |  |  |
| Furtividade | Física | 20 | sim |  |
| Pilotar | Física | 1 | sim |  |
| Saltar | Física | 20 |  |  |
| Chaveiro | Física | 1 | sim |  |
| Encontrar | Física | 25 |  |  |
| Escutar | Física | 20 |  |  |
| Nadar | Física | 20 |  |  |
| Prestidigitação | Física | 10 | sim |  |
| Olfato | Física | 50 |  | exclusiva do Lobisomem: toda ficha a recebe (o ganho não se perde ao trocar de raça) e o frontend só a mostra para Lobisomem; fora da lista original, valor das regras |
| Antropologia | Mental | 1 |  |  |
| Arqueologia | Mental | 1 |  |  |
| Arte/Ofício | Mental | 5 |  |  |
| Astronomia | Mental | 1 |  |  |
| Avaliação | Mental | 5 |  |  |
| Biologia | Mental | 1 |  |  |
| Botânica | Mental | 1 |  |  |
| Ciência Forense | Mental | 1 |  |  |
| Consertos Elétricos | Mental | 10 |  |  |
| Consertos Mecânicos | Mental | 10 |  |  |
| Contabilidade | Mental | 5 |  |  |
| Criptografia | Mental | 1 |  |  |
| Direito | Mental | 5 |  |  |
| Disfarce | Mental | 5 |  |  |
| Eletrônica | Mental | 1 |  |  |
| Farmácia | Mental | 1 |  |  |
| Física | Mental | 1 |  |  |
| Geologia | Mental | 1 |  |  |
| História | Mental | 5 |  |  |
| Língua Nativa (Inglês) | Mental | EDU |  | inicial = EDU digitada, acompanha o atributo ao vivo |
| Matemática | Mental | 1 |  |  |
| Medicina | Mental | 1 |  |  |
| Meteorologia | Mental | 1 |  |  |
| Mundo Natural | Mental | 10 |  |  |
| Navegação | Mental | 10 |  |  |
| Ocultismo | Mental | 5 |  |  |
| Operar Maquinário Pesado | Mental | 1 |  |  |
| Primeiros Socorros | Mental | 30 |  |  |
| Psicanálise | Mental | 1 |  |  |
| Química | Mental | 1 |  |  |
| Rastrear | Mental | 10 |  |  |
| Sobrevivência | Mental | 10 |  |  |
| Lidar com Animais | Mental | 5 |  | o +20 do Lobisomem vale para ela (nome oficial: Lidar com Animais) |
| Usar Bibliotecas | Mental | 20 |  |  |
| Usar Computadores | Mental | 5 |  |  |
| Zoologia | Mental | 1 |  |  |
| Charme | Interpessoal | 15 |  |  |
| Intimidação | Interpessoal | 15 |  |  |
| Lábia | Interpessoal | 5 |  |  |
| Persuasão | Interpessoal | 10 |  |  |
| Psicologia | Interpessoal | 10 |  |  |
| Primórdios | Desconhecida | 0 |  | sem categoria: nenhum modificador de categoria a afeta |

## Em aberto

- Bruxo e Viajante realmente não têm perícia mágica? (O mock antigo marcava Ocultismo.)
- Caçador: Primórdios (sem categoria) também conta como mágica?
- Faltam as demais perícias ("boa parte" foi enviada).
