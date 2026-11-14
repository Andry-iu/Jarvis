## Aggiornamenti remoti e catalogo client

`device_call` inoltra anche `update_prepare`, `update_execute`, `update_status`
e `log_read` ai dispositivi gia associati. La descrizione storica del client che
nega i batch di aggiornamento e obsoleta; il catalogo server e gia corretto.
Prima di dichiarare una capacita assente confrontare `tools/list`, sorgente e
ricevute. Non chiedere copia manuale o nuovo pairing quando il canale esiste.
Usare l'unico updater canonico e verificare risultato, non il solo dispatch.
[Catalogo operativo e formato delle chiamate](MCP-CATALOG-0.8.3.md).

## Regole utente — 14 novembre 2026: layout, elevazione e pulizia

- Percorso unico per tutte le installazioni: <Jarvis>\Agent\jarvis-mcp.exe.
  Indicazione iniziale root corretta dall'utente prima delle modifiche: non applicarla.
  Allineare Desktop1 e verificare anche Desktop2; migrazione ora esplicitamente autorizzata.
- Agente sempre amministratore al termine degli interventi. Avvio automatico dopo
  riavvio, all'accesso dell'utente, con attivita pianificata Interactive/Highest,
  come SoftPlcTest1. Verificare percorso e token elevato effettivi dopo il riavvio
  dell'agente; registrare il test di task separatamente da un reboot fisico.
- Dopo ogni prova/elaborazione, anche fallita o interrotta, arrestare i processi
  di test e rimuovere/verificare cartelle temporanee e regole firewall provvisorie
  proprie. Non lasciare residui nei percorsi operativi o in Projects; tutelare
  altre elaborazioni, stato, progetti, appunti, backup e ricevute definitive.
- Aggiornare anche D:\Jarvis\Projects\appunti.txt su entrambi i desktop,
  oltre a documentazione/continuita Git. Non dichiarare pulito o riavviato senza
  verifica. Non disabilitare globalmente firewall/notifiche o cambiare il profilo
  di rete per eliminare i prompt dei test.

# Regole operative consolidate — 1 novembre 2026

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

Ambito: Jarvis Windows MCP Agent e installer. Questo documento raccoglie le decisioni dell'utente; i verbali storici descrivono lo stato alla loro data. Non estende l'autorizzazione ad Android o ad altri progetti.

## Sviluppo, autorizzazioni e diagnosi

- Raccogliere le modifiche e l'ambito prima dello sviluppo; procedere dopo il via esplicito. Le correzioni necessarie al completamento autorizzato appartengono allo stesso lavoro. Proseguire senza richiedere di nuovo autorizzazioni gia date.
- Le procedure di Desktop1 e Desktop2 devono usare lo stesso algoritmo. Dichiarare ogni differenza, individuarne la causa e risolverla; niente percorsi alternativi nascosti. Parametri di installazione differenti devono essere registrati e verificati.
- Non cambiare implicitamente percorsi, avvio, privilegi, configurazioni di sicurezza o decisioni approvate. Verificare macchina/versione prima delle mutazioni e conservare identita, stato, ricevute, segreti e backup.
- Dopo una disconnessione o un esito incerto consultare ricevuta, processo e risultato: non ripetere automaticamente l'azione. Dispatch, completamento e risultato verificato sono stati distinti.

## Aggiornamento dell'agente

L'agente prepara ed esegue un batch CMD in un processo separato tramite `update_prepare` e `update_execute`. Il batch invoca l'unico script canonico `windows-mcp-agent/scripts/update-agent.ps1`. I due vecchi deploy sono alias di quello script.

Verificare hash candidato, macchina/configurazione, backup e prova di rollback prima della sostituzione; dopo, verificare riavvio, versione, identita e manutenzione via MCP. Arrestare soltanto il PID dell'agente la cui identita e stata verificata. `taskkill /T` terminava anche l'aggiornatore discendente: era la causa del guasto iniziale Desktop2, non un limite da aggirare.

Nessuna attivita pianificata per l'aggiornamento. Il task Windows gia previsto dall'installer serve all'avvio ordinario dopo accesso e resta distinto. Nessun replay incerto, modifica TrustedHosts o indebolimento dei limiti di sicurezza. Il setup 1.3.0 rifiuta una precedente versione operativa completa: prima va aggiornata dal suo agente tramite batch; non esiste un secondo algoritmo di aggiornamento del binario nel setup. Il recupero di installazioni incomplete riconosciute conserva le verifiche e i backup.

| Configurazione osservata | Desktop1 | Desktop2 | Nuove installazioni |
|---|---|---|---|
| Eseguibile | `D:\Jarvis\jarvis-mcp.exe` | `D:\Jarvis\Agent\jarvis-mcp.exe` | `<root scelta>\Agent\jarvis-mcp.exe` |
| Avvio corrente | diretto | supervisore `Scripts\Start.ps1` | supervisore installato |
| Aggiornamento | batch separato, script comune | batch separato, script comune | stesso script incluso in `Scripts` |

Desktop1 precede il layout strutturato dell'installer; Desktop2 segue tale layout. La differenza e di installazione, non una diversa funzione dell'agente. Nessuna migrazione implicita. Ogni nuovo PC genera identita e credenziali proprie; non clonare stato o attestazioni economiche.

## Applicazioni e desktop

- Usare gli strumenti diretti disponibili. `process_start` accetta eseguibile, argomenti e cartella; il default e un processo non amministratore anche se l'agente e elevato. Richiedere elevazione soltanto quando esplicitamente voluta dall'utente; se il token standard verificato non e disponibile, rifiutare.
- Individuare Desktop, Documenti e Download con `known_folder`, rispettando reindirizzamenti. Gestire finestre e controlli con gli strumenti MCP, documenti moderni compresi. Chiusura ordinaria, senza forzare perdita di modifiche.
- Se il compito richiede operare nell'applicazione, usare il suo dialogo Apri/Salva tramite `ui_file_dialog` e verificare percorso/contenuto. Non sostituire il salvataggio dell'app con scrittura diretta. Le sovrascritture esplicite possono essere vincolate a hash/contenuto attesi.
- Un batch applicativo serve solo quando gli strumenti disponibili non coprono l'operazione; non sostituisce senza motivo gli strumenti introdotti in 0.7.0. Ricevute e request_id impediscono duplicazioni incerte; le attese UI non devono bloccare le letture di stato globali.

## Manutenzione e installazione

Log e batch generati: conservazione predefinita 30 giorni se il valore manca o e invalido, controllo all'avvio e ogni 24 ore. Proteggere batch preparati o in esecuzione. Non rimuovere script di avvio, stato, ricevute e backup come fossero log. Consultare `maintenance_status`.

Cloudflare: nomi tunnel/applicazione/policy `jarvis-<DEVICE_NAME normalizzato>`, override facoltativi, hostname pubblico indipendente. Ripresa e rinomina tramite ID registrati; un nome uguale non prova proprieta. Nessun replay dopo risposte perse senza prova dell'esito. Configurazione locale e segreti esclusi dal pacchetto e da Git.

## Costi, Git e prove

Zero costi aggiuntivi: nessuna API key, acquisto, credito, upgrade o fallback a pagamento. Non estendere o modificare prove economiche per superare le guardie. Consultare quota con `codex_status` senza inferenza; un saldo sconosciuto non e zero. Prima di ogni turno verificare disponibilita corrente, dopo attendere `jarvis/usage/final_usage` e riferire dato corrente o errore.

Git contiene sorgenti, test, documentazione, versioni/checksum e risorse necessarie, incluso tutto l'installer e lo script di costruzione. Non aggiungere ZIP, eseguibili, DLL, PDB, cache, log grezzi, backup, stato privato o credenziali. Non allegare binari/ZIP come sostituto dei sorgenti. Nessuna modifica agli artefatti storici Android fuori ambito.

Pubblicare sul branch autorizzato `reconcile-windows-mcp-agent-20261031`, con push fast-forward e verifica SHA remoto; nessun merge implicito su main o force-push. Distinguere implementato, testato, pubblicato, installato e collaudato via MCP. Non dichiarare aggiornati i cataloghi plugin, eseguito un reboot fisico o trascorso un ciclo reale di 24 ore senza prova. Aggiornare registro attivita e continuita insieme al codice; non esportare lo stato privato come documentazione.
