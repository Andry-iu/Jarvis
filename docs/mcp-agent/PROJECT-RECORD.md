# Registro di progetto su Git

Regola approvata il 31 ottobre 2026: archivio tecnico sul computer; registro significativo nel repository di ciascun progetto.

Per ogni modifica/rilascio aggiornare docs/ACTIVITY.md del progetto (o il registro gia stabilito), insieme ai sorgenti e ai test. Il registro deve riportare data, obiettivo, decisioni e motivazioni, comportamento finale, verifiche realmente eseguite con esiti, limiti aperti e riferimenti ai commit/verbali. Mantenere distinti implementato, testato, pubblicato, installato e collaudato. Correlare tramite ID attivita solo quando utile; nessun dato account.

Non esportare automaticamente queue.json, archivio, prompt Codex, stdout/stderr, ricevute o configurazioni nel repository. Per riassunti usare i risultati verificati e scrivere un testo curato attraverso le API file con hash atteso; includerlo nel commit della modifica. Verificare il push remoto prima di dichiarare pubblicato. Se il progetto non ha remoto Git, mantenere il documento locale e indicare il limite, senza creare repository o connessioni implicitamente.

L'archivio completo continua a essere interrogabile con activity_history, job_status, request_status e codex_events sul computer corretto. Non creare commit per letture, controlli di stato o semplice archiviazione. Il registro e aggiornato dal workflow di sviluppo, non da un esportatore automatico di log.
