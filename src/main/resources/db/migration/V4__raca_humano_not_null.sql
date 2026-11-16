-- Não existe ficha sem raça: quem não tem raça sobrenatural é HUMANO (regra do War If).
-- Idempotente: o update não muda nada se não houver raça nula/vazia; set default e set not null
-- podem ser repetidos sem efeito. Não altera atualizada_em nem grava eventos no log.
update ficha
set raca = 'HUMANO'
where raca is null
   or btrim(raca) = '';

alter table ficha alter column raca set default 'HUMANO';
alter table ficha alter column raca set not null;
