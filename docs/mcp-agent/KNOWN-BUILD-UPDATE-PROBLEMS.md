# Problemi riscontrati e procedure riuscite

Questa pagina raccoglie esperienza operativa da riusare, non nuovi tentativi da
ripetere. Gli appunti privati mantengono inventario e ricevute per ogni computer.

| Problema osservato | Metodo riuscito e vincolo corrente |
|---|---|
| Vecchi pacchetti con data di modifica futura vengono protetti come recenti | Non cambiare timestamp o dedurre uso dal solo LastWriteTime. Dopo inventario esatto, creazione precedente e assenza di riferimenti/lavori, usare Scope=Releases e KeepRecentMinutes=0; disattiva solo la recenza, con tutte le altre guardie conservate. Caso verificato nella pulizia1.1.1; test dedicato su timestamp futuro. |
| Desktop2: arresto interno del compilatore Go1.27.3, codice 0xc0000005; errori package not in std non provavano file assenti | Go1.27.0 ufficiale con hash verificato, cache isolata, GOMAXPROCS=1, GOGC=25, -p1; test, vet e build Windows superati. SDK di sistema preservato. Causa interna del compilatore non isolata. |
| Updater Desktop2 terminato con il genitore da taskkill /T | Batch CMD separato e stop del solo PID agente verificato; stesso updater canonico su tutti i PC, mai task di aggiornamento. |
| Riavvio su installazione con supervisore | Start.ps1 esistente, proprietario/percorso del manifest verificati, attesa rilascio runner.lock prima del nuovo avvio; conservare identita, configurazione e token elevato. |
| Finestra di abbinamento creata ma invisibile nella 0.8.1 | CREATE_NO_WINDOW senza HideWindow/SW_HIDE sul solo helper GUI; prova con launcher Go reale, non ProcessStartInfo sostitutivo. I batch di aggiornamento restano senza console. |
| Avvisi firewall ripetuti per copie di test su percorsi diversi | Fixture solo loopback e pulizia di processi/cartelle/regole proprie. Non disabilitare notifiche/firewall o cambiare profilo rete. |
| Descrizione client di device_call obsoleta | Confrontare tools/list del server e sorgenti: inoltro aggiornamenti gia presente. Distinguere catalogo server e cache client, senza attribuire refresh non eseguiti. |
| Preflight/dispatch riferiti come aggiornamento concluso | Conferma soltanto da ricevuta canonica, riconnessione autenticata e identita/versione/hash, manutenzione e pulizia. |
| Primo collaudo 0.8.4: File.Replace con backup null rifiutato da Windows PowerShell | Percorso di backup esplicito per la scrittura atomica dello stato. Tentativo fermo prima della compilazione; agente0.8.3 preservato, stato originale conservato e fallimento verificato prima di un nuovo ID. Successiva preparazione 0.8.4 PASS. |
| Stato bootstrap scritto da Windows PowerShell con UTF-8 BOM | Parser accetta il BOM dei record precedenti e runner scrive senza BOM. Conservare ricevute e non rilanciare un bootstrap completato. |
| SoftPlcTest1: token della shell elevato, test di avvio standard fallito | Test controlla il token reale e richiede il rifiuto sicuro se manca un token standard. Successiva suite nativa PASS; nessun allentamento del codice di avvio applicazioni. |
| Processo riavviato con ambiente di build temporaneo | Ripristino TEMP/TMP/PATH e variabili Git/test prima dell'updater. Audit Desktop2 conferma ambiente ripristinato e assenza dei checkout temporanei propri. |
| Catalogo client Desktop2 vecchio dopo aggiornamento server | Impostazioni app ChatGPT, Refresh tools, poi scheda strumenti: nuovi tool e descrizione inoltro osservati. Una chiamata al solo server non prova il refresh client. |
| Desktop1: ricevuta locale completed ma MCP esterno -32603; rinnovo login Cloudflare HTTP403 | Accesso autenticato tornato disponibile alle20:02UTC senza ulteriori rinnovi o deploy da questa ripresa; causa non isolata. Audit gateway/target e appunti poi completati. Non attribuire il recupero a un intervento non provato, ne ripetere credenziali/deploy alla cieca. |

Il comando nuovo usa scripts/build-runtime.ps1, cioe il profilo gia riuscito,
e scripts/update-agent.ps1, cioe il motore gia collaudato. Non scegliere un SDK
nuovo perche appare in PATH o ricostruire ogni volta una procedura diversa.
Modalita da Git ordinaria; se Git non e raggiungibile dal destinatario, mantenere
il pacchetto verificato via MCP. Le due acquisizioni convergono sullo stesso
updater e sulle stesse verifiche. Dettagli UPDATE-FROM-GIT-0.8.4.md.

## Caso 22/11/2026 — avvio automatico Desktop1/Desktop2 dopo agente 0.8.8

**Causa verificata:** l'eseguibile lanciato direttamente con `jarvis-mcp-as-admin.bat` funzionava, ma la catena di avvio gestita `Start.ps1 -> Common.ps1 -> Invoke-LocalTool` falliva. `Get-LocalToken` leggeva `secrets.env` come una sola stringa e pretendeva un token base64 di almeno 40 caratteri, mentre l'agente Go supportava chiavi di almeno 24 caratteri e file env con piu variabili; alcuni file legacy hanno BOM anche all'inizio di righe successive. Non attribuire questa differenza ai privilegi: entrambi gli avvii erano elevati. Non rigenerare o alterare i segreti per aggirare l'incompatibilita.

**Correzione sorgente:** leggere il file per righe, riconoscere il token senza esporlo, gestire BOM e configurazioni legacy, rifiutare token mancanti/duplicati o caratteri di controllo; aggiornare anche il parser Go per BOM su righe successive. PR [#3](https://github.com/Andry-iu/Jarvis/pull/3), merge commit `d90a00c2bc98b4417307bb431930a8604e1a1a22`. CI Windows PowerShell 5.1, Go (test/vet/build) e controllo integrita pacchetto: PASS.

**Ostacoli e procedura riutilizzabile:**
- **Avvio manuale e avvio automatico non equivalenti.** Il batch manuale esegue direttamente `Agent\jarvis-mcp.exe`; il task usa `Scripts\Start.ps1`, supervisore, controlli di hash, chiamata MCP locale e tunnel Cloudflare. Confrontare l'intera catena, non sostituire semplicemente il task con il batch. Il file `running.json` puo contenere PID obsoleti: verificare listener, percorso del processo e `runner.lock` live.
- **PowerShell ExecutionPolicy.** Una prova diagnostica che importa `Common.ps1` senza `-ExecutionPolicy Bypass` produce `PSSecurityException`; e un errore del metodo di test, non prova di un difetto dell'agente o della correzione. Replicare le opzioni del task.
- **Distribuzione remota.** `update_prepare` verifica ID univoco di 32 caratteri esadecimali, prima riga `REM JARVIS-UPDATE-ID`, CRLF e SHA-256; `update_execute` avvia soltanto. Il primo batch su Desktop1 e fallito (exit 1) per uso accidentale di `$home`, variabile PowerShell riservata; correggere in `$root`, usare **nuovo** ID e consultare ricevuta, log e hash. Non dedurre successo dal solo dispatch; non ripetere un ID con risultato incerto.
- **Manifest installatore incoerente.** Il controllo introdotto ha trovato hash/dimensioni superati per `Start.ps1` e `update-agent.ps1`. Riallineare `package.json` e `script-history.json` ai sorgenti effettivi, conservare hash storici autorizzati, aggiungere test di contratto alla CI. Non modificare `secrets.env`.
- **Dipendenze di sviluppo locali non affidabili.** Su Desktop2 Codex non avviava la sessione per errore nel `config.toml`; su Desktop1 risultava non disponibile l'eseguibile Codex configurato. Nessuna riparazione Codex e stata fatta: usare GitHub Actions per test e compilazione, senza attribuire il blocco alla modifica Jarvis.
- **Handoff e tunnel:** non interrompere simultaneamente entrambi i desktop. Confermare backup, una sola istanza, appartenenza del PID, task, proprieta elevate, risposta MCP, stato supervisore e tunnel dopo ciascun riavvio; prevedere fallback controllato.

**Stato fisico verificato al termine dell'intervento:** `Common.ps1` e stato sostituito con backup su Desktop1 (receipt `a792bec6d4204fce9702f798ce242a31`) e Desktop2 (receipt `90dac1131ebd40df8b158a7c9f283c12`); entrambe exit 0, hash della patch e parser token controllati; agenti 0.8.8 ancora raggiungibili. Nessun token, configurazione Cloudflare o eseguibile installato e stato modificato. **Non ancora dimostrato** il cold start da task su entrambi i desktop: Desktop1 era ancora avviato manualmente e Desktop2 aveva supervisore attivo. Non dichiarare completata la correzione dell'avvio automatico senza prova dopo stop/login o riavvio.

Nota procedurale: utilizzare le procedure gia collaudate, preferire diagnosi mirate e registrare in Git i problemi incontrati e i loro rimedi; evitare prove ripetute e messaggi di avanzamento non sostanziali.
