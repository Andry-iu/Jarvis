# Windows MCP changelog

## 0.5.9 — candidata, 31 ottobre 2026

- codex_status resta il comando MCP diretto per richieste su quota residua e crediti; aggiunto riepilogo usage con percentuali residue, reset UTC e saldo noto/sconosciuto.
- Una lettura automatica dopo turn/completed (completato, interrotto o fallito), salvata come jarvis/usage e disponibile anche in codex_events.final_usage; usage_pending indica la lettura in corso.
- Nessun confronto storico. Nessuna nuova lettura iniziale: restano le guardie gia presenti. Dati leggibili anche quando un nuovo turno sarebbe bloccato; saldo nullo mai trasformato in zero.
- A fine elaborazione il client deve riferire brevemente i valori aggiornati o l'indisponibilita, senza riusare valori precedenti.
- Test tramite processo RPC simulato per notifiche duplicate, assenza di deadlock, esiti finali, saldo nullo e servizio non disponibile. Collaudo Windows/installazione ancora in corso.

## 0.5.8 — 31 ottobre 2026

- Plugin aggiornato e strumenti diretti verificati.
- Riscontro costi indipendente fornito dall'utente, associato ad account/PC e con scadenza; saldo API nullo mai accettato da solo. Crediti positivi/flag ignoti/quote esaurite restano bloccati.
- Codex reale collaudato: risposte, storico, modifica file, steer, interrupt e resume dopo riavvio.
- Installazione elevata, backup 0.5.7 e stato, rollback e riconnessione verificati. Nessuna API key, acquisto o modifica alle impostazioni di pagamento.

## 0.5.7 — 31 ottobre 2026

- Identita stabile, catalogo progetti, ricevute con request_id e job persistenti, lock OS e ACL dello stato.
- Lettura/scrittura/ricerca file, creazione annidata e rinomina/spostamento di file e cartelle, hash atteso, backup e ripristino protetti.
- Profili fissi di sviluppo Go/Git/.NET/CMake/MSBuild, output limitato, timeout e annullamento tramite Job Object Windows. Test/build Go e build .NET verificati.
- Cattura JPEG MCP diretta con monitor, regione, origine e fattori di scala; protezioni desktop 0.5.6 mantenute.
- Routing autenticato a peer configurati, verifica identita, stato online/offline e inoltro limitato.
- Ponte Codex App Server locale, protocollo fissato, account/quote/sessioni/eventi/approvazioni. Letture reali verificate; turni bloccati da saldo nullo e revisione costi, non collaudati con inferenza.
- Catalogo server aggiornato e percorso di compatibilita verificato dal plugin. Refresh del catalogo client e prova nuova chat/Voice ancora necessari.
- Sorgenti e collaudi su branch separato; backup/rollback, installazione, amministratore e riconnessione verificati. Nessun costo API/acquisto aggiuntivo avviato.

## 0.5.6

Controllo desktop semantico senza coordinate, snapshot monouso e revalidazione; vedere ../docs/mcp-agent/DESKTOP-0.5.6.md.
