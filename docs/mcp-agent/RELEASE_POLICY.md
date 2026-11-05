# Regola obbligatoria: GitHub aggiornato a ogni rilascio

## Autorizzazione permanente — 5 novembre 2026

Alla richiesta di autorizzare il push della 0.7.4 nel repository pubblico
Andry-iu/Jarvis, ramo feature/development-token-meter-20261105, l'utente ha
risposto: «Autorizzo sempre, registrare le regole.»

Per gli interventi Jarvis gia approvati, commit e push dei sorgenti/test/documenti
su Andry-iu/Jarvis sono autorizzati stabilmente, anche sui relativi branch di
lavoro. Non chiedere nuovamente il consenso per ogni push o nome del branch.
Questa autorizzazione comprende la pubblicazione del lavoro 0.7.4 corrente.
Conservare ambito, verifiche e procedure gia approvati; nessun merge implicito
su main, force-push, segreto, log grezzo o stato privato pubblicato. Le richieste
di nuove funzioni restano distinte dall'autorizzazione alla pubblicazione.

Questa regola vale per **Jarvis Android** e **Jarvis Windows MCP Agent**.

1. Ogni release deve comprendere nello stesso repository: **sorgenti integrali modificati, test, documentazione, changelog, versione e stato di avanzamento**. L'agente MCP deve restare in sottocartella separata dall'app Android.
2. Prima di dichiarare un rilascio completato: verificare compilazione e test effettivamente svolti; eseguire commit e **push remoto**; rileggere il commit/branch remoti, riportando SHA e link. Se il push non riesce, la release va indicata come **non pubblicata**, senza affermare il contrario.
3. Una release non deve dipendere unicamente dai file di una chat: tutti i sorgenti devono essere recuperabili dal repository; per il Windows MCP Agent l'utente richiede soltanto sorgenti, test, documentazione e risorse, incluso l'installer: niente ZIP o binari generati nel commit o come sostituto dei sorgenti.
4. Aggiornare `docs/mcp-agent/STATUS.md` e la documentazione di continuita con: cosa e completato, cosa e verificato, bug aperti, prossimi passi, comandi di ripresa e compatibilita.
5. Non committare token, API key, password, certificati privati, credenziali di firma, segreti Cloudflare o valori sensibili. Usare nomi di variabili e placeholder.
6. Prima di ogni modifica lavorare sul branch/commit remoto attuale; evitare force push e sovrascritture implicite. Ogni cambiamento sostanziale richiede autorizzazione.
7. Nel report di rilascio separare esplicitamente: **implementato**, **testato**, **pubblicato su Git**, **installato**, **collaudato da ChatGPT**. Non sono sinonimi.

Regola approvata dall'utente il 2026-10-31. Il rispetto della regola va dimostrato con SHA remoto per ogni release.

## Decisioni successive — 1 novembre 2026

Per Windows MCP applicare [OPERATING-RULES.md](OPERATING-RULES.md). Il branch autorizzato e `reconcile-windows-mcp-agent-20261031`; non applicare implicitamente il rilascio Android su main. L'utente ha autorizzato aggiornamento completo e pubblicazione: le normali verifiche/correzioni necessarie non richiedono una seconda conferma.

Aggiornamento identico sui due PC: agente, batch CMD separato, unico updater canonico, backup/rollback e riscontro MCP. Nessun task di aggiornamento; il task di avvio e distinto. Spiegare differenze e correggere cause, senza aggiramenti. Nuove installazioni sotto Agent; percorsi esistenti preservati. Privilegi applicazioni non amministratore per default; elevazione solo esplicita.

Il verbale di ogni installer riporta versione agente, commit sorgente, hash e dimensione candidato, hash di tutti i file payload e test realmente eseguiti. La presenza su Git non significa installazione in produzione. Mai dichiarare verifiche plugin/reboot/24 ore non osservate.
