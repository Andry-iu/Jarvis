# Controllo amministratore e riavvio agente - 0.7.3

## Ambito e interfaccia
Richiesta raccolta e autorizzata il 3 novembre 2026: leggere da chat i privilegi
effettivi di Desktop1/Desktop2 e richiedere il riavvio come amministratore quando
necessario. Nuovo branch autorizzato feature/agent-admin-control-20261103.
L'autorizzazione Git ai nuovi branch e permanente per i lavori approvati.

machine_info e agent_status aggiungono privileges:
- elevated: true, false oppure null se non verificabile;
- pid: PID del processo agente realmente interrogato;
- checked_at: verifica UTC corrente;
- source: process_token;
- error: codice generico se la query fallisce, senza convertire l'ignoto in false.
Non e un controllo dell'appartenenza dell'account al gruppo Administrators:
si interroga il token del processo.

Nuovi strumenti:
- agent_restart_admin(machine_id, request_id): richiede il riavvio elevato.
- agent_restart_status(machine_id, request_id): legge il risultato persistente.

Catalogo client precedente:
{"operation":"runtime","request":{"operation":"agent_restart_admin","machine_id":"<id>","request_id":"<16-64-caratteri-univoci>"}}
{"operation":"runtime","request":{"operation":"agent_restart_status","machine_id":"<id>","request_id":"<stesso-id>"}}
La consultazione ordinaria usa machine_info/agent_status, gia esposti nei plugin.

## Semantica
Gia elevato: state=already_elevated, restarted=false, nessun riavvio.
Privilegi ignoti: errore, nessun tentativo.
Lavori attivi, accodati, incerti o stato della coda non sano: rifiuto senza stop;
nessuna cancellazione/forzatura o ripetizione automatica.
Per un agente standard viene registrata una ricevuta prima della richiesta
asincrona a Windows. L'ack awaiting_elevation NON e una conferma di riavvio.
Lo stesso request_id restituisce la ricevuta originaria senza ripetere l'effetto.
Consultare lo strumento status per l'esito, anche dopo riconnessione.

Fasi persistenti: awaiting_elevation, handoff, completed, cancelled,
failed_before_handoff, failed_after_handoff, expired.
Solo completed espone verified_elevated=true e new_pid verificato.
original_stop_requested distingue le fasi che hanno richiesto lo stop;
non certifica che un processo sia ancora vivo dopo eventi esterni.
Il file privato contiene anche nonce, SID e impronta di configurazione:
la risposta pubblica li esclude.

## Elevazione e conservazione
Si usa ShellExecuteExW con runas per l'helper integrato nello stesso eseguibile.
Windows puo richiedere un consenso UAC o credenziali locali. Nessun nuovo task,
servizio, broker persistente, password salvata o modifica alle policy UAC.
Il controllo dell'interfaccia UAC non e automatizzato.

L'helper deve risultare elevato e dello stesso utente; prima dello stop verifica:
scadenza, configurazione, home/stato/percorso, hash del binario, SID, PID e istante
di creazione del processo originale, nonche profilo di avvio/manifest esistente.
Gli eseguibili devono rispettare il layout installato root o Agent.
Il passaggio e riservato al loopback autenticato, con nonce monouso/scadenza.
Nuove mutazioni vengono bloccate durante l'attesa; le letture restano disponibili.
Un UAC annullato/fallito/scaduto prima del passaggio non arresta l'originale.
Un'approvazione tardiva non puo fermare l'agente dopo la scadenza.

Dopo il passaggio si attende l'uscita ordinata del solo agente, senza kill di
lavori o processi terzi. Si riutilizza il profilo diretto o il supervisore Start.ps1
gia configurato; versione/binario/configurazione e identita non vengono migrati.
Il successo richiede un nuovo PID con stessa identita/versione e token elevato.
Se l'avvio non e verificato si registra errore, senza dichiarare successo.
I contatori e lo stato runtime restano persistenti; nessun replay di job/sessioni.
Le applicazioni ordinarie avviate da process_start restano standard per default.

## Prove ripetibili
go test ./..., go vet ./..., go build per Windows amd64.
agent_control_test.go copre no-op, ricevute, token ignoto, lavoro attivo/incerto,
annullamento, sblocco mutazioni, dati privati esclusi e autenticazione handoff.
scripts/test-agent-admin.ps1 verifica:
- stato elevato reale e no-op (anche compatibilita/receipt);
- stato standard reale e rifiuto durante diagnostica;
- blocco mutazioni e originale ancora vivo prima dell'helper;
- passaggio reale standard -> helper elevato -> nuovo agente elevato;
- identita, PID/token, ricevuta ripetuta senza doppioni e contatori;
- chiusura dei processi e pulizia dell'intera cartella temporanea.
La seam di lancio e nel solo eseguibile go-test tramite TestMain, mai nel prodotto.
Il test fornisce un helper gia elevato: non simula i controlli del token o il
passaggio HTTP, ma non collauda il dialogo UAC interattivo nativo.

Parametri:
powershell.exe -NoProfile -File scripts\test-agent-admin.ps1 -Candidate <exe> -TestExecutable <go-test-exe> -ProductionToken <in-memoria>
Il token viene fornito come variabile nel processo di collaudo, mai salvato nei
sorgenti o negli argomenti di un nuovo processo. L'istanza installata e usata solo
per process_start standard; i processi riavviati sono esclusivamente le fixture.
Tutti gli intermedi sono nella cartella temporanea dedicata, fuori da Projects.

## Distribuzione e limiti
Updater software canonico invariato: batch separato -> scripts/update-agent.ps1,
backup/rehearsal prima della sostituzione e verifica MCP dopo.
Il riavvio amministratore e una funzione di controllo distinta dall'aggiornamento
del binario, non un percorso alternativo di distribuzione.
Installer 1.3.1/agente incluso 0.7.1 restano una release distinta.
Reboot fisico, dialogo UAC reale e catalogo diretto dei nuovi strumenti nel client
non sono certificati dalla sola suite. Compatibilita task_start/runtime prevista.
