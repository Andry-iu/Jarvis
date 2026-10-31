# Coda per progetto — 0.6.0 installata e collaudata

Implementazione dell'ambito approvato: elaborazioni accodate per progetto, elenco, annullamento e riordino dei lavori in attesa; letture disponibili durante l'elaborazione. Test Windows, vet/build, backup/rollback, installazione e collaudo MCP superati il 31 ottobre 2026. [Verbale con evidenze e limiti](VALIDATION-0.6.0-20261031.md).

## Interfaccia

- command_start e codex_turn ora accodano e restituiscono queue_id, stato e posizione. Anche i task Go legacy passano dalla coda. Usare queue_list per ottenere job_id/turn_id dopo l'avvio, poi job_status/codex_events.
- queue_add accetta project, request_id e call con operation command_start/codex_turn e i relativi argomenti; project/request_id esterni vengono ereditati. label opzionale e depends_on con ID gia esistenti. Nessuna shell arbitraria.
- queue_list mostra ID, progetto, etichetta, stato, ordine, dipendenze, pausa e job/turn attivo. Paginazione 50 predefinita, massimo 200; storico massimo 5000 operazioni, rifiuto esplicito al limite.
- queue_cancel annulla soltanto un'attivita in attesa. I lavori avviati richiedono job_cancel/codex_interrupt e verifica dello stato finale.
- queue_move sposta un ID in attesa prima di before_id nello stesso progetto. Senza before_id lo rende prossimo. Non preempie il lavoro attivo e rifiuta inversioni di dipendenze esplicite.
- queue_resume riprende il progetto dopo una pausa e revisione. Dopo riavvio lo stato di lavori avviati resta incerto e non viene ripetuto; per Codex deve essere verificata la sessione inattiva. Le sessioni da usare devono essere esplicitamente riagganciate prima dell'avvio.

## Concorrenza e controlli

Una sola elaborazione per percorso di progetto, includendo sovrapposizioni padre/figlio e alias. Progetti indipendenti possono lavorare in parallelo, massimo quattro elaborazioni sul PC. Ordine di arrivo salvo riordino esplicito; una testa bloccata non viene scavalcata. Dipendenze fallite/annullate e errori mettono il progetto in pausa.

La coda viene salvata nello stato privato fuori dai progetti. Stato starting persistito prima del dispatch, nessun replay automatico dopo esito incerto. Quota/costi ricontrollati immediatamente prima dell'avvio Codex; lettura finale 0.5.9 preservata e attesa prima di liberare il progetto. Nessun acquisto/API/fallback.

Letture file senza lock globale del progetto. Su Windows un'apertura nativa della sola foglia relativa a un parent verificato esclude handle di scrittura/cancellazione per il breve accesso. Se il file e occupato la lettura viene rifiutata, senza esporre contenuto parziale. Protezioni di percorsi, link, credenziali e limite 1 MiB invariate. Elenchi di directory non sono snapshot atomici dell'intero albero. Altri sistemi mantengono il confinamento os.Root ma non garantiscono esclusione di scrittori esterni.

Mutazioni MCP dirette e ripristini rifiutati se il relativo progetto e in elaborazione. La protezione copre i lavori inviati al broker: processi esterni, risorse globali come porte o toolchain condivise e altre istanze non configurate richiedono coordinamento dell'assistente. Nessuna deduzione automatica delle dipendenze dal testo libero.

## Verifiche eseguite

Test per ordine/riordino, dipendenze, cancellazione, idempotenza, riavvio senza replay, progetti sovrapposti/indipendenti, letture durante lavoro e rifiuto file aperto in scrittura. Vet/build, backup/rollback isolato, installazione e collaudo dal plugin superati. Nel client con catalogo precedente i nuovi queue_* sono accessibili con task_start operation=runtime e request contenente operation/machine_id e argomenti. Il refresh dei cinque strumenti diretti e Voice restano da verificare.
