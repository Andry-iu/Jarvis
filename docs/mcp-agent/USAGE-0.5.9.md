# Quota e crediti MCP — candidata 0.5.9

Richiesta approvata il 31 ottobre 2026: controllo all'avvio del turno, dato attuale alla fine di ogni elaborazione Codex e consultazione in chat su richiesta, senza confronti storici.

## Comando diretto
Il comando gia esistente e codex_status con il solo machine_id restituito da machine_info. Non serve avviare una sessione o un'inferenza. La risposta conserva quota grezza e guardie, aggiungendo usage: finestre primary/secondary con percentuale residua, reset Unix e UTC; crediti con stato known/unknown e saldo originale. Saldo null resta sconosciuto, anche se una prova locale permette l'avvio del turno. Il controllo iniziale non garantisce il costo futuro di un intero lavoro.

## Lettura finale e risposta in chat
Il reader riceve turn/completed e avvia una sola lettura asincrona per turno, anche per interrupted/failed. Non esegue RPC bloccanti nel reader. La lettura usa account/read e account/rateLimits/read, senza inferenza o acquisti. Il risultato viene salvato nell'evento jarvis/usage della sessione con thread_id, turn_id, turn_status, phase=after, checked_at, available e status. Se il servizio fallisce, available=false e quota_read_failed; nessun dato precedente viene sostituito alla lettura.

codex_events espone anche final_usage per il turno finale corrente e usage_pending. Dopo turn/completed attendere la lettura finale; nel riepilogo del lavoro dire brevemente quota residua, crediti se noti e prossimo ripristino in ora locale. Se un dato manca, dirlo; inference_allowed=false non rende ignota una quota che e stata letta. La fotografia riguarda l'intero account e non attribuisce consumi al singolo lavoro. Lo storico eventi resta soggetto ai limiti di retention esistenti.

Il comando diretto conserva nome e parametri. Le descrizioni MCP sono aggiornate per facilitarne la selezione; eventuali descrizioni in cache dipendono dal client. Una nuova chat o Voice deve comunque disporre del plugin e del PC raggiungibile.

## Verifica
Codice candidato pubblicato; da verificare su Windows prima della promozione: test Go, vet, build, processo RPC simulato (completato/interrotto/fallito, duplicati, errore servizio, quota esaurita e saldo null), rollback isolato 0.5.9/0.5.8 e un turno reale dal plugin dopo installazione. Non dichiarare installato sulla sola base dei sorgenti.

La coda per progetto e una proposta separata, non implementata in questa versione.
