# Regola obbligatoria: GitHub aggiornato a ogni rilascio

Questa regola vale per **Jarvis Android** e **Jarvis Windows MCP Agent**.

1. Ogni release deve comprendere nello stesso repository: **sorgenti integrali modificati, test, documentazione, changelog, versione e stato di avanzamento**. L'agente MCP deve restare in sottocartella separata dall'app Android.
2. Prima di dichiarare un rilascio completato: verificare compilazione e test effettivamente svolti; eseguire commit e **push remoto**; rileggere il commit/branch remoti, riportando SHA e link. Se il push non riesce, la release va indicata come **non pubblicata**, senza affermare il contrario.
3. Una release non deve dipendere unicamente dai file di una chat: tutti i sorgenti devono essere recuperabili dal repository; allegare eventuali binari come artefatti di release quando possibile, non sostituire i sorgenti con un eseguibile.
4. Aggiornare `docs/mcp-agent/STATUS.md` e la documentazione di continuita con: cosa e completato, cosa e verificato, bug aperti, prossimi passi, comandi di ripresa e compatibilita.
5. Non committare token, API key, password, certificati privati, credenziali di firma, segreti Cloudflare o valori sensibili. Usare nomi di variabili e placeholder.
6. Prima di ogni modifica lavorare sul branch/commit remoto attuale; evitare force push e sovrascritture implicite. Ogni cambiamento sostanziale richiede autorizzazione.
7. Nel report di rilascio separare esplicitamente: **implementato**, **testato**, **pubblicato su Git**, **installato**, **collaudato da ChatGPT**. Non sono sinonimi.

Regola approvata dall'utente il 2026-10-31. Il rispetto della regola va dimostrato con SHA remoto per ogni release.
