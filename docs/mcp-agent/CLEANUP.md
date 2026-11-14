# Pulizia permanente dell'area Jarvis

Regole concordate il 14 novembre 2026. Procedura autonoma PowerShell 5.1,
sorgente `windows-mcp-agent/procedures/Invoke-JarvisCleanup.ps1`, installata come
`<Jarvis>\ProcedurePermanenti\Invoke-JarvisCleanup.ps1` sul destinatario.
Nessun nome macchina, ruolo gateway o percorso di banco nel codice.

Quando l'utente dice "fai pulizia", richiamare questo script tramite il batch
MCP autorizzato, diretto o inoltrato, con un RequestId nuovo e conservato:

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass `
 -File '<Jarvis>\ProcedurePermanenti\Invoke-JarvisCleanup.ps1' `
 -HomeDir '<Jarvis>' -ExpectedMachine '<machine_id verificato>' `
 -RequestId '<GUID32>' -Execute
```

Senza `-Execute` esegue soltanto l'anteprima. `Scope` consente Backups, Logs,
Temp, Scripts, Releases (default tutti). Un RequestId esistente restituisce la ricevuta;
argomenti differenti con lo stesso ID sono rifiutati. Una ricevuta running dopo
un'interruzione richiede verifica, mai replay automatico.

## Ambito e conservazione

- Backups: conserva solo il backup verificato dell'ultimo aggiornamento per la
  macchina e l'hash dell'agente installato. Elimina le altre copie soltanto se
  nessuna copia piu recente richiede verifica. Un aggiornamento attivo/incerto
  o l'assenza di un backup valido bloccano l'esecuzione.
- Logs: elimina file non in uso e non recenti. Il margine predefinito di cinque
  minuti protegge scritture/diagnosi appena avvenute (`KeepRecentMinutes`). Il valore0 disattiva esplicitamente il solo controllo
  di recenza: usarlo su un ambito gia inventariato e verificato inattivo, per
  esempio quando i vecchi archivi contengono date future. Non cambiare i timestamp
  e non disattivare i controlli su uso, aggiornamenti, lavori, link o sorgenti.
- Temp, Scratch, Staging: elimina residui inattivi, senza attraversare junction,
  link o alberi sorgenti riconosciuti. Lavori runtime attivi preservano log/temp.
- Scripts: elimina batch GUID con ricevuta terminale e hash corrispondente.
  Per i batch legacy senza ricevuta richiede intestazione esatta corrispondente
  al nome, assenza di lavori runtime e tutti i controlli su riferimenti, link,
  file in uso e recenza. Ricevute invalide, batch preparati o con esito incerto
  restano protetti. Elimina anche gli script monouso nella cartella esplicita `Scripts\Temporary`. Gli script
  permanenti, di avvio/stop/aggiornamento e il sorgente non sono candidati.
  Script non riconosciuti restano segnalati; il solo nome non prova l'inutilita.
- Releases: elimina i pacchetti e gli eseguibili delle distribuzioni concluse;
  non conserva una release solo perche e la piu recente. Il programma attivo
  resta in Agent e il rollback nell'ultimo Backups verificato. Protegge file
  in uso/recenti, riferimenti di processi/task/servizi, alberi Git, lavori runtime
  e aggiornamenti attivi o incerti. Quando resta vuota rimuove anche Releases.
  Un aggiornamento successivo puo ricrearla come area di preparazione.
- Conserva processi/task/servizi, file bloccati, configurazioni, stato, identita,
  credenziali, ricevute durevoli e materiale dei progetti. Non arresta processi,
  non modifica firewall, avvio o registrazione delle attivita Windows.

Projects contiene i progetti di lavoro e **tutti** i loro materiali, compresi
backup, log, script e temporanei. Fuori da Projects e area Jarvis: sorgenti,
eseguibili e configurazioni Jarvis non vanno messi dentro Projects. Il vecchio
backup NestingOpt nell'area Backups Jarvis era una collocazione errata.

Lo script usa il lock canonico `Config\update.lock`. Ogni esecuzione conserva
la propria ricevuta in `State\cleanup-receipts\<RequestId>.json`, con elenco
esatto di candidati, rimossi, conservati e motivi, byte ed eventuali errori.
Rileggere questa ricevuta e machine_info dopo il lancio: avvio e completamento
sono esiti distinti. Stato partial/failed non significa pulizia completa.

Dopo ogni aggiornamento riuscito e verificato, il coordinatore esegue questa
procedura con Scope=Backups,Releases prima di dichiarare concluso l'intervento. Durante
l'aggiornamento e in caso di errore o ripristino i backup restano protetti.
La 0.8.4 non richiama automaticamente lo script dal suo codice: il passo e della
procedura operativa coordinata dalla chat. Non dichiarare implementato un hook
nell'updater o una nuova manutenzione autonoma.

## Verifica

`Test-JarvisCleanup.ps1` usa una fixture dedicata e verifica riconoscimento dei
batch legacy, anteprima, esclusione di file recenti/preparati/non riconosciuti,
ricevuta invalida, protezione dei dati permanenti e anti-replay. La fixture viene
rimossa anche dopo errori. La verifica Releases copre lavoro attivo, anteprima,
rimozione, file recenti/bloccati, worktree Git e rimozione della radice vuota,
con agente installato e backup preservati. Il precedente collaudo isolato della 1.0.0 aveva
verificato anche retention backup, log/temp, file bloccati, aggiornamento attivo,
parametri cambiati e junction; quel risultato non sostituisce il test dei legacy.
Il verbale di collaudo registra separatamente fixture, anteprime sui destinatari
e pulizia operativa gia conclusa; nessun riavvio o rilascio runtime implicito.
