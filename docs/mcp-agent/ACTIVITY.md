# 31 ottobre 2026 — installer Desktop2 1.0.0

Richiesta utente: installazione indipendente del secondo PC. Aggiunti wizard locale, credenziali/ID nuovi, task interattivo, configurazione tunnel locale, verifiche/ripristino non distruttivo e pacchetto con binario 0.6.1 verificato. Suite Windows isolata completata. Account Cloudflare/Access e nuovo plugin restano passaggi dell'utente; nessun secondo PC ancora configurato e nessuna modifica al servizio Desktop1. Sorgenti/test/manifest e builder su Git; [verbale e limiti](INSTALLER-DESKTOP2-1.0.0.md).

# Registro significativo — Jarvis Windows MCP

## 31 ottobre 2026 — 0.6.1

Problema: attese Codex trattenevano il lock globale e lo scheduler; i turni avevano uno stop fisso di cinque minuti; la coda tratteneva tutti i terminali; progetti distinti potevano condividere risorse senza coordinamento.

Decisioni approvate: nessuno stop temporale e nessun avviso temporizzato; archivio tecnico locale per computer e registro significativo sul Git del progetto; coordinamento read/write delle risorse note. Implementati RPC fuori lock, avvio asincrono, archivio prima della compattazione con dipendenze storiche, activity_history, identita Git comune e risorse dichiarate. Guardia economica invariata.

Sorgenti binario 164cb980bd1a936749d41b8692979e98b929cb04, branch reconcile-windows-mcp-agent-20261031. Test/vet/build Windows passati. Prova RPC simulata di 310 secondi e collaudo MCP isolato passati. Backup verificato, rollback isolato e installazione 0.6.1 su Desktop1 riusciti. Turno Codex reale JARVIS_MCP_061_OK e quota finale archiviata. Nessuna modifica Android o ad altri progetti. Dettagli, checksum e limiti in VALIDATION-0.6.1-20261031.md.

Prossimi collaudi distinti: altri PC, riavvio completo del computer e disponibilita dei nuovi strumenti diretti in altri client. Risorse esterne non dichiarate richiedono coordinamento; nessun controllo universale del sistema operativo.
