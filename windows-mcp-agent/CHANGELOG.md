# Installer 1.2.1 — parametri e segreti locali, senza domande console

Aggiunto `setup.local.env`, rilevato automaticamente accanto a `INSTALLA.cmd`, con modello `setup.example.env` e guida `CONFIGURAZIONE.txt`. Lettura letterale UTF-8 con whitelist/duplicati rifiutati, ACL utente/SYSTEM, modalita senza prompt, parametri auto/manual/skip e rilevamento conflitti di ripresa. Il file compilato non entra in manifest, ZIP, Git o log; contiene in chiaro i segreti scelti dall'utente e resta esclusivamente sul PC. UAC e login ChatGPT nel browser restano possibili. Nessuna API key OpenAI o modifica alle guardie economiche.

**Eseguiti:** 20/20 regressioni Node Cloudflare nel cloud Linux, integrita manifest/ZIP e verifica esclusione file locali. **Non eseguiti:** `Test-SetupSettings.ps1`, PowerShell/Windows, ACL/no-prompt sul secondo PC, API Cloudflare reali e plugin ChatGPT. Nessuna nuova operazione su Desktop1. [Procedura e limiti](../docs/mcp-agent/INSTALLER-1.2.1.md).

# Installer 1.2.0 — Cloudflare automatico dal setup

Implementati rilevazione dominio/account/team, app Access per singola email con Managed OAuth, tunnel dedicato e DNS dopo verifica della protezione. Token API solo in memoria/stdin; registro vincolato al PC e riconciliazione senza ripetere esiti incerti. Node 24 LTS privato con checksum se manca un runtime compatibile. Agente operativo 0.6.1 e guardie economiche invariati. Nessuna nuova prova o installazione su Desktop1.

Test effettivi: **20/20 test Node simulati nel cloud**, sintassi JS e integrita pacchetto. **Non eseguiti**: PowerShell/Windows 1.2.0, API Cloudflare reali e nuovo plugin ChatGPT/Voice. Sorgenti pubblicati non significano installazione o collaudo remoto completati. Ripresa: nuovo ZIP in nuova cartella, INSTALLA.cmd con la stessa destinazione; token API inserito solo sul PC. Non cancellare il registro. [Procedura, backup/rollback e limiti](../docs/mcp-agent/INSTALLER-1.2.0.md).

# Installer 1.1.1 — correzione estrazione e ripresa Git

Segnalazione dal secondo PC: estrazione dichiarata conclusa ma verifica Git fallita. Difetto individuato: argomenti del vecchio SFX nella 1.1.0, incompatibili col parser attuale Git for Windows. Corretto in -y -o"percorso"; worker completed soltanto dopo versione nativa verificata. Ripresa limitata alla richiesta/ricevuta 1.1.0 confermata, vincolata a PC/utente/percorso/hash, senza eliminare registro o prove precedenti; nessun retry di operazioni attive/incerte. [Verbale e procedura](../docs/mcp-agent/INSTALLER-1.1.1.md).

Regressione eseguita nel cloud compilando il ciclo parser ufficiale C++: vecchi argomenti riproducono destinazione assente, nuovi argomenti conservano percorso/spazi/Unicode. Nuovi test Windows predisposti ma NON eseguiti in questa correzione. Nessuna installazione, test o mutazione aggiuntiva su Desktop1; niente nuova inferenza o modifiche alle guardie economiche. Il precedente collaudo 1.1.0 con fixture non aveva riprodotto il comportamento del vero estrattore: non prova la correzione sul secondo PC.

# Installer 1.1.0 — un solo avvio, prerequisiti e ripresa automatici

Richiesta utente: evitare comandi PowerShell manuali per preparare Codex e recuperare le installazioni. INSTALLA.cmd ora rileva/installa Git, Go e CLI Codex compatibile da fonti ufficiali con hash, gestisce login browser e verifica account/quota senza inferenza. Registro persistente per PC/utente/cartella, cache verificata, ricevute dei worker e nessun replay di esiti incerti. Ripresa della 1.0.1 fallita e dei tentativi 1.1.0 mai operativi; backup/rollback anche dei task, aggiornamento protetto degli script delle copie complete. Nome Desktop2 configurabile; binario agente 0.6.1 e servizio operativo Desktop1 invariati.

[Specifica, procedura e limiti](../docs/mcp-agent/INSTALLER-1.1.0.md). Cloudflare remoto (DNS/tunnel/Access/Managed OAuth), plugin e accessi account restano passaggi distinti: nessuna falsa dichiarazione di collegamento completo, nessun costo extra o modifica alle guardie. Collaudo secondo PC fisico e installazione reale dei runtime da assenti restano da eseguire; i test Windows isolati distinguono fixture da letture reali.

# Installer 1.0.3 — 31 ottobre 2026

Richiede CLI Codex compatibile prima di creare l'installazione. Priorita alla CLI nativa, percorso esplicito supportato; COLLEGA-CODEX per ripresa 1.0.1 o abilitazione su copia completa e controllo account/quota senza inferenza. Nessun arresto automatico, nessuna riabilitazione implicita del task disabilitato, nessuna modifica alle guardie dei costi. Binario agente 0.6.1 invariato. Verbale docs/mcp-agent/INSTALLER-DESKTOP2-1.0.3.md.

# Windows MCP changelog

## 0.6.1 — installata e collaudata, 31 ottobre 2026

Attese Codex senza mutex globale; nessun watchdog temporale dei turni e nessun avviso temporizzato. Coda operativa compatta con archivio immutabile locale, dipendenze storiche e activity_history; job storici caricati su richiesta. Coordinamento read/write di risorse dichiarate e identita Git comune. Registro significativo di progetto su Git distinto dallo stato privato. Vedere docs/mcp-agent/IMPROVEMENTS-0.6.1.md; test Windows/vet/build, prova oltre cinque minuti, MCP isolato, backup/rollback, installazione e turno Codex reale superati. Verbale VALIDATION-0.6.1-20261031.md in docs/mcp-agent.

## 0.6.0 — installata e collaudata, 31 ottobre 2026

- Coda persistente per progetto, percorsi sovrapposti protetti, massimo quattro progetti indipendenti attivi.
- queue_add/list/cancel/move/resume, etichette, ID e dipendenze esplicite; riordino senza preemption.
- command_start/codex_turn e Go legacy accodano; usare queue_list per ricavare job/turn dopo dispatch.
- Letture senza lock globale, esclusione nativa Windows del file aperto in scrittura; mutazioni dirette di progetti attivi rifiutate.
- Pausa su errore/riavvio, nessun replay automatico degli esiti incerti; verifica quota all'avvio effettivo e fotografia finale 0.5.9 preservata.
- Test Windows/vet/build, collaudo MCP isolato con riavvio, backup/rollback, installazione e prove reali dalla nuova chat superati.
- Corretto lo script del collaudo crash per gestire i figli gia terminati e isolare ogni esecuzione; nessuna modifica alle guardie economiche.
- Turno Codex accodato completato, final_usage presente; automazione quota esistente verificata senza duplicati. Voice/altri PC/reboot e refresh dei cinque strumenti diretti client restano non verificati. Verbale docs/mcp-agent/VALIDATION-0.6.0-20261031.md.


## 0.5.9 — installata e collaudata, 31 ottobre 2026

- codex_status resta il comando MCP diretto per richieste su quota residua e crediti; aggiunto riepilogo usage con percentuali residue, reset UTC e saldo noto/sconosciuto.
- Una lettura automatica dopo turn/completed (completato, interrotto o fallito), salvata come jarvis/usage e disponibile anche in codex_events.final_usage; usage_pending indica la lettura in corso.
- Nessun confronto storico. Nessuna nuova lettura iniziale: restano le guardie gia presenti. Dati leggibili anche quando un nuovo turno sarebbe bloccato; saldo nullo mai trasformato in zero.
- A fine elaborazione il client deve riferire brevemente i valori aggiornati o l'indisponibilita, senza riusare valori precedenti.
- Test tramite processo RPC simulato per notifiche duplicate, assenza di deadlock, esiti finali, saldo nullo e servizio non disponibile. Test Windows/vet/build superati; backup/rollback e installazione verificati. Turno reale dal plugin con evento finale jarvis/usage e final_usage verificati.

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




