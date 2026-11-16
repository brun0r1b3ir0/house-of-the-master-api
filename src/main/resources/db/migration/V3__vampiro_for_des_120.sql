-- Fichas VAMPIRO têm FOR e DES fixas em 120 (regra do War If).
-- Só mexe em estado.atributos.FOR e estado.atributos.DES; idempotente (não muda nada se já forem 120).
-- Não altera atualizada_em nem grava eventos no log (correção de dados, sem autor).
update ficha
set estado = jsonb_set(
                 jsonb_set(estado, '{atributos,FOR}', '120'::jsonb),
                 '{atributos,DES}', '120'::jsonb)
where raca = 'VAMPIRO'
  and jsonb_typeof(estado -> 'atributos') = 'object'
  and (estado -> 'atributos' -> 'FOR' is distinct from '120'::jsonb
       or estado -> 'atributos' -> 'DES' is distinct from '120'::jsonb);
