# Token meter 0.7.4

## Ambito e conservazione

Il registro osserva esclusivamente notifiche del Codex app-server avviato da
Jarvis per turni inoltrati con codex_turn. Nessuna scansione, eliminazione o
modifica dei dialoghi. Lo stato privato runtime-v1/token-usage/<thread_id>.json
contiene identificatori, progetto, categoria, modello richiesto, tempi, esiti e
contatori. Non contiene prompt, risposte, audio, credenziali o dati account.
Nessuna conversione da quota percentuale a token e nessuna chiamata a modelli.

Questa chat ChatGPT, Voice, trascrizioni, TTS, Nestingbot e altri client non sono
osservati automaticamente. Non e una contabilita dell'intero account e non
ricostruisce consumi passati. Per altri provider occorrono ulteriori adattatori
nel punto delle rispettive chiamate; non inclusi in questa release.

## Uso

Al dispatch codex_turn aggiungere usage_category=development per sviluppo,
dialogue per conversazione, other per altri impieghi. Il valore omesso diventa
unclassified, escluso dal totale sviluppo. La categoria appartiene all'intero
turno, compresi steering, ragionamento e tool; cambiare turno per cambiare
categoria. Le richieste gia accodate senza categoria restano unclassified.

Lettura senza inferenza, anche quando Codex e disabilitato o le guardie bloccano:

```json
{"operation":"runtime","request":{"operation":"codex_token_usage","machine_id":"<machine_id>","project":"<progetto>","usage_category":"development","limit":50,"offset":0}}
```

Usare task_start con il payload sopra se il catalogo client e precedente.
Se il catalogo client di codex_turn non espone usage_category, usare anche per
il dispatch la compatibilita task_start/runtime, con request completa
(operation=codex_turn, machine_id, project, thread_id, text, request_id univoco e
usage_category=development). Il vecchio comando diretto senza categoria produce
unclassified e non un consumo di sviluppo inventato.

usage_category accetta anche all nel solo report. thread_id e facoltativo;
limit 1..200, offset >=0. Riepiloghi sull'intero insieme filtrato, dettagli
paginati. Ogni computer conserva il proprio registro; nessuna somma implicita
fra PC. Per confrontare giorni o modelli usare i riferimenti nei dettagli.

## Contatori e affidabilita

thread/tokenUsage/updated.tokenUsage.total e uno snapshot cumulativo del thread.
Il delta rispetto alla base osservata del turno sostituisce il valore precedente:
notifiche ripetute e aggiornamenti intermedi non si sommano. Input include cache;
output include reasoning. Total = input + output; i sottoinsiemi non si aggiungono.
Cache/ragionamento assenti restano null. Il modello e quello richiesto, non una
certificazione del modello effettivamente fatturato. Non si calcola un costo.

Le righe misurate includono anche consumo parziale di turni attivi e consumo
osservato di turni falliti/interrotti. summary.observed_tokens e null senza righe
misurate; measured_runs/unmeasured_runs/active_runs ne chiariscono la copertura.
Un turno senza evento non vale zero. Il report restituisce le letture correnti;
final_usage.token_usage e l'istantanea raccolta durante la lettura quota finale.
Un evento tardivo puo aggiornarla successivamente nel report corrente.

Riprendere una sessione scarta la base corrente ma conserva tutti i record. Il
primo turno ripreso resta non attribuibile se manca una base osservata; uno
snapshot valido puo stabilire la base del successivo. Il preflight include i turni
per rilevare avanzamenti da altri client: un confine cambiato o sconosciuto non
viene attribuito. Non condividere contemporaneamente lo stesso thread con altri
client: lo steering di un turno resta consumo di quel turno.

Snapshot malformati, regressioni/reset e aggiornamenti tardivi del precedente
che rendono ambiguo il confine producono tokens=null e coverage esplicita. I
valori cumulativi non vengono importati come consumi storici. Un dispatch senza
esito resta incerto, blocca la ripetizione automatica e richiede ispezione/resume
esplicito. La stessa request_id non viene ricontata o rilanciata.

Scritture atomiche private; file corrotti conservati, senza reset automatico.
In caso di registro indisponibile il report fallisce esplicitamente e un nuovo
turno non viene inviato. Nessun cambio alle guardie economiche o acquisto crediti.
Limiti: 16 MiB per ledger, 2000 ledger e 100000 righe per report; selezionare thread_id
per ridurre l'insieme. Non viene applicata cancellazione automatica al registro.
Backup e rollback canonici includono lo stato; il rollback non azzera i contatori.

## Verifiche

Suite Go: duplicati, delta multipli, categorie, paginazione, persistenza, privacy,
baseline ignota, evento mancante, confine esterno, evento tardivo, reset, snapshot
malformati, null, corruzione preservata, overflow e nessun replay. Integrazione
reader con processo figlio RPC simulato; nessun modello o rete. Verbale con esiti
Windows, pubblicazione e installazione: VALIDATION-0.7.4-20261105.md.

## Compilazione e ripresa: procedura esistente

Seguire il profilo Windows gia riuscito per 0.7.3 e documentato nel relativo
verbale: SDK ufficiale Go 1.27.0 verificato, cache nuova per ogni esecuzione,
GOMAXPROCS=1, GOGC=25 e -p 1. scripts/build-runtime.ps1 raccoglie gli stessi
comandi test/vet/build, con gestione stderr gia presente in build-runtime-059.ps1.
Non usare nuovamente Go 1.27.3 come primo tentativo su Desktop2.
Il SDK resta temporaneo; non modificare toolchain permanente o agent.env.

```powershell
powershell.exe -NoProfile -File scripts\build-runtime.ps1 -GoExe <SDK1270> -GitExe <git> -SourceCommit <sha> -Candidate <exe-separato> -Log <log>
powershell.exe -NoProfile -File scripts\test-token-meter-mcp.ps1 -Candidate <exe-separato> -TestHome <cartella-nuova> -Log <log>
```

La prima procedura impone Go 1.27.0 e crea/rimuove solo la propria cache unica;
la seconda usa un agente isolato con Codex disabilitato, non un'installazione.
Distribuzione sempre scripts/update-agent.ps1 via update_prepare/update_execute,
con sorgenti gia pubblicati, hash/identita, backup e rehearsal prima dello stop.
Non introdurre percorsi di aggiornamento diversi fra PC.
