## 14 novembre 2026 — procedura uniforme approvata, 0.8.4

Utente chiarisce: coordinamento nella chat, gateway solo inoltro del comando;
stesso download Git/build/update su ogni destinatario, anche al quarto livello.
Conferma successiva: conservare trasferimento pacchetto se Git non raggiungibile.
Implementazione e test di instradamento completati; collaudo e rollout Windows
in corso. Specifica UPDATE-FROM-GIT-0.8.4.md, esiti VALIDATION-0.8.4-20261114.md.

## 14 novembre 2026 — aggiornamento automatico attraverso gateway, verifica reale

Completato aggiornamento LAN 0.8.2→0.8.3 senza passaggi manuali. Candidato
Desktop1 gia validato, trasferito in blocchi con hash/ricevute, poi unico updater
canonico con backup e rehearsal. Identita/versione/hash/elevazione/manutenzione
e pulizia verificati; appunti dei tre PC allineati. Corretto l'errore operativo:
descrizione client obsoleta scambiata per limite runtime e avvio scambiato per
esito. Cataloghi server corretti, cache client ancora vecchia e non dichiarata
aggiornata. Procedura, prove e limiti in REMOTE-UPDATE-0.8.3-20261114.md e
MCP-CATALOG-0.8.3.md. Nessun nuovo binario, build o cambio di versione.

## 14 novembre 2026 — codice obbligatorio e conto alla rovescia, candidato 0.8.3

Utente ha approvato implementazione, commit e aggiornamento documentale. Il codice
calcolato dai due agenti viene comunicato alla chat dal gateway e digitato sul
nuovo PC, senza valore atteso visibile. Richiesto durante lo sviluppo un conto
alla rovescia: 110 secondi visibili accanto al campo. Verifica locale e nel Go,
rifiuto di semplice ALLOW, input errato e risposte scadute. Tre errori chiudono.
La verifica evita il solo clic distratto; il codice deriva da dati pubblici del
protocollo, non e una password o un secondo fattore segreto. Collegamenti salvati
invariati; target precedenti richiedono aggiornamento per la nuova UI.
Sorgenti pubblicati, suite native e installer/upgrade isolati PASS su entrambi;
Desktop1/Desktop2 aggiornati e riconnessi 0.8.3 con backup/rollback e identita
conservati. Pacchetto finale verificato. Nessun nuovo consenso umano automatizzato.
Stato effettivo e limiti delle prove in VALIDATION-0.8.3-20261114.md.

## 2026-11-05 — 0.7.4 pubblicata, installata e collaudata

Contatori token per progetto/incarico/categoria, dialoghi conservati. Sorgente
runtime pubblico 6a04718e1eb5330179c6a3e621749e1b63fe22e7, identico nell'albero al
locale c662fae collaudato. Collegamento GitHub gia attivo usato dopo conferma
esplicita; regola permanente commit/push registrata. Branch
feature/development-token-meter-20261105; nessun merge su main.

Desktop1 e Desktop2 ora 0.7.4: suite native e MCP isolato PASS; updater canonico,
backup/rehearsal e MCP operativo PASS. Report development/dialogue funzionanti,
ancora privi di misure reali: nessun consumo storico inventato. Ambito limitato a
Codex inoltrato dall'agente; non questa chat/Voice. Nessuna inferenza o modifica
alle guardie. Dettagli e checksum nel verbale VALIDATION-0.7.4-20261105.md.

## 2026-11-05 — Autorizzazione permanente alla pubblicazione

L'utente conferma esplicitamente «Autorizzo sempre, registrare le regole.» dopo
la richiesta di push nel repository pubblico Andry-iu/Jarvis per la 0.7.4.
Registrato consenso stabile a commit/push degli interventi approvati e relativi
branch; niente nuova domanda a ogni rilascio. Protezioni e ambito invariati.
Ripreso il rilascio sul branch feature/development-token-meter-20261105.

## 2026-11-05 — Metodo di compilazione ripreso dagli appunti

Su richiamo dell'utente, riusato il profilo Desktop2 gia riuscito il 3 novembre:
Go 1.27.0 verificato, SDK/cache isolati, GOMAXPROCS=1, GOGC=25, -p 1. Test/vet/build
e MCP isolato PASS. Metodo riportato in testa ad AGENTS.md e reso ripetibile in
scripts/build-runtime.ps1. Abbandonati i tentativi non necessari con Go 1.27.3;
nessuna causa unica dimostrata per i suoi errori. Pubblicazione 0.7.4 bloccata dalla
revisione automatica, nessuna installazione. Dettagli VALIDATION-0.7.4-20261105.md.

## 2026-11-05 — Token dello sviluppo, conservando i dialoghi

Richiesta: distinguere il consumo del lavoro sui progetti dai dialoghi, senza
eliminare conversazioni. Modifica agente Windows 0.7.4 sul branch
feature/development-token-meter-20261105. Contratto TOKEN-METER-0.7.4.md;
esiti effettivi VALIDATION-0.7.4-20261105.md. Nessuna modifica Android/NestingOpt,
nessuna API key, inferenza o modifica delle prove economiche.

# Registro di progetto su Git

Regola approvata il 31 ottobre 2026: archivio tecnico sul computer; registro significativo nel repository di ciascun progetto.

Per ogni modifica/rilascio aggiornare docs/ACTIVITY.md del progetto (o il registro gia stabilito), insieme ai sorgenti e ai test. Il registro deve riportare data, obiettivo, decisioni e motivazioni, comportamento finale, verifiche realmente eseguite con esiti, limiti aperti e riferimenti ai commit/verbali. Mantenere distinti implementato, testato, pubblicato, installato e collaudato. Correlare tramite ID attivita solo quando utile; nessun dato account.

Non esportare automaticamente queue.json, archivio, prompt Codex, stdout/stderr, ricevute o configurazioni nel repository. Per riassunti usare i risultati verificati e scrivere un testo curato attraverso le API file con hash atteso; includerlo nel commit della modifica. Verificare il push remoto prima di dichiarare pubblicato. Se il progetto non ha remoto Git, mantenere il documento locale e indicare il limite, senza creare repository o connessioni implicitamente.

L'archivio completo continua a essere interrogabile con activity_history, job_status, request_status e codex_events sul computer corretto. Non creare commit per letture, controlli di stato o semplice archiviazione. Il registro e aggiornato dal workflow di sviluppo, non da un esportatore automatico di log.
