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
