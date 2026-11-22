# Installer 1.2.2: tunnel, applicazione e policy con nome del dispositivo

## Ambito e identita

Richieste approvate: nome tunnel leggibile, variabile con il nome scelto per ogni computer; lo stesso per Policy name e Application name; compatibilita con rinomine manuali gia fatte in Cloudflare. Base Git verificata `2df08468896a78024ea85554e12cd613f6084bba`, branch `reconcile-windows-mcp-agent-20261031`. Agente distribuito sempre 0.6.1, binario SHA-256 `9ff4a136951f464925cc55b96e1a968174204199fa9c7ff681b998b3542742c1`, invariato. Nessuna modifica Android.

Il plugin Desktop2 e diventato disponibile durante il lavoro. Verificati direttamente da MCP: machine_id `d41455adf8721c205183224d3c69a1a5`, hostname `AUTECHVPC2`, Windows amd64, agente 0.6.1, coda sana senza attivita e job vuoti. Il nome logico Desktop2 non significa che sia un secondo PC fisico: il nome host rilevato e quello sopra. Non confondere con Desktop1 e il suo differente ID; nessuna operazione eseguita su Desktop1 in questo rilascio.

La precedente installazione completa 1.2.1 e stata riferita dall'utente tramite log: Git/Go/Codex/Node verificati, Cloudflare predisposto, Codex collegato con ChatGPT, inferenza bloccata per saldo sconosciuto e assenza di prova valida su questo computer. Il collegamento MCP Desktop2 e ora confermato dalle chiamate reali, non solo da quel log. Nessun turno Codex avviato, prova costi copiata/prorogata o modifica economica.

## Comportamento e uso

Tutti e tre i nomi visibili derivano da `DEVICE_NAME`, normalizzando lettere minuscole, spazi e underscore in trattini:

| DEVICE_NAME | Tunnel | Application name | Policy name |
|---|---|---|---|
| Desktop2 | jarvis-desktop2 | jarvis-desktop2 | jarvis-desktop2 |
| Desktop3 | jarvis-desktop3 | jarvis-desktop3 | jarvis-desktop3 |
| Notebook | jarvis-notebook | jarvis-notebook | jarvis-notebook |
| PC Ufficio | jarvis-pc-ufficio | jarvis-pc-ufficio | jarvis-pc-ufficio |

Override facoltativi nel file locale:

```dotenv
CLOUDFLARE_TUNNEL_NAME=
CLOUDFLARE_APPLICATION_NAME=
CLOUDFLARE_POLICY_NAME=
```

Vuoti o assenti usano il default; non occorre aggiornare il file privato 1.2.1. Override ammessi: lettere/numeri iniziali, poi lettere/numeri/spazi/trattini/punto/underscore, massimo 100 caratteri. Non inserire segreti nei nomi pubblici. `CLOUDFLARE_HOST_LABEL` resta indipendente: cambiare etichetta visibile non cambia dominio, URL MCP, email, audience o autenticazione.

Per nuove installazioni usare `INSTALLA.cmd`. Per un'istanza gia configurata, estrarre il nuovo pacchetto in una nuova cartella, copiarvi **privatamente** il proprio `setup.local.env` e avviare **`RINOMINA-CLOUDFLARE.cmd`**. Usa `Setup.ps1 -RenameResourcesOnly`, legge il registro esistente e verifica UUID tunnel, URL/team e AUD rispetto al Config del PC. Serve un token API Cloudflare valido con gli stessi permessi gia previsti. Nessuna reinstallazione/avvio/stop agente o Codex, nessuna creazione/cancellazione DNS/tunnel/app. Se tutti i nomi sono gia quelli desiderati, le chiamate Cloudflare sono sole GET. La sola rinomina non richiede di fermare l'agente. Il normale aggiornamento completo degli script conserva il precedente obbligo di agente fermo.

## Sicurezza, identita e ripresa

Il nome leggibile non e prova di proprieta. Il journal mantiene proprietario casuale, identita PC/SID/root, account/zona, parametri originali, hash degli intenti di creazione e ID delle risorse. Per i nuovi journal `creation_names` mantiene i nomi usati nelle richieste originali, anche dopo rinomine: i body hash restano verificabili. I vecchi journal senza il campo conservano i nomi casuali originali per la verifica, ma gli oggetti gia identificati vengono riletti tramite ID.

App e tunnel con ID noto non vengono ritrovati/adottati dal nome. L'app deve mantenere tipo, dominio, Managed OAuth e AUD; la policy deve mantenere ID, singola email allow ed exclude/require attesi. Policy riutilizzabili estranee vengono rifiutate. Un tunnel omonimo di un altro proprietario blocca il nuovo provisioning o una rinomina in conflitto: non viene adottato o modificato. Una creazione nuova con nome leggibile e risposta persa **senza ID osservato** resta incerta e non viene ripetuta/adottata per nome. Per vecchi nomi casuali univoci e intenti verificabili rimane la riconciliazione preesistente.

Rinomina tunnel: PATCH solo `name` sull'UUID registrato. App/policy richiedono PUT secondo API Cloudflare: il client conserva le impostazioni lette e la policy inline con il suo ID; cambia il solo nome richiesto. Verifica nuovamente gli oggetti immediatamente prima del PUT e dopo, controllando OAuth/AUD/regole e valori opzionali. Se rileva variazioni concorrenti o campi policy non gestibili, si ferma. L'API non offre qui una transazione che escluda modifiche amministrative contemporanee: evitare modifiche simultanee da dashboard mentre si esegue il comando.

Ogni rinomina ha intento e ricevuta per risorsa, con nome precedente/nuovo e fingerprint degli altri campi. Dopo risposta persa, una GET deve provare l'esito prima di continuare; se il nome non risulta aggiornato non viene reinviata automaticamente una PATCH/PUT incerta. Le rinomine sono tre operazioni distinte: un errore successivo non annulla una precedente riuscita e non elimina protezioni Access. Il journal resta per la ripresa. Non vengono toccati DNS, ingress, token runtime o altre macchine.

Il token API rimane nel file privato dell'utente e in memoria/stdin, senza copie nel journal/log/Git/ZIP. Segreti locali esclusi dal manifest e dai payload; campioni distribuiti vuoti. Nessun costo aggiuntivo, API OpenAI a consumo o inferenza.

## Verifiche eseguite e limiti

- Cloud Linux: `node --check` e **45/45 test Node passati**, incluse tutte le regressioni precedenti e naming per altri PC, override, migrazione journal, rinomine manuali gia fatte, ID stabili, impostazioni optional/OAuth/email preservate, policy riutilizzabili rifiutate, collisioni, esiti incerti non ripetuti e ispezione senza mutazioni.
- Desktop2: sorgenti di test in `D:\Jarvis\Projects\Installer-122-check-60b981ed823a`, separati dai file operativi. Batch `f2bb653fef4b413b83fbabff28c13f8d` con byte/hash verificati; il runner analizza tutti i file PS con parser nativo, esegue Test-SetupSettings e 45 test Node, poi richiede l'ispezione Cloudflare in sola lettura. Nessun setup completo/reinstallazione avviati.
- Il canale stdout dei figli lanciati dal batch non e stato riportato nel log rediretto (file vuoto). La diagnosi successiva `installer122-process-state.log` conferma che il primo runner non e piu attivo, Windows PowerShell `5.1.19041.7802` a 64 bit e risultato nomi gia scritto. La scrittura del risultato si trova **dopo** tutti i controlli bloccanti di parser/Test-SetupSettings/exit code Node: e evidenza del loro superamento, senza inventare un log per-assertione non disponibile. Nessun replay del primo batch.
- Riscontro reale `installer122-observed-names.log`: lettura Cloudflare del 1 novembre 2026, **09:43:43 CET** (`08:43:43Z`), `read_only=true`, `matches=true`; tunnel, applicazione e policy tutti chiamati `jarvis-desktop2`. Nessuna PUT/PATCH inviata da questo collaudo. Il controllo passa attraverso il lettore UTF-8/ACL e il ponte stdin reali Windows; conferma le rinomine dichiarate dall’utente senza ricreare risorse. Una nuova machine_info successiva conferma lo stesso ID/host e agente 0.6.1 ancora raggiungibile. Il log stdout non prova il controllo finale dei fingerprint del runner: non lo si presenta come evidenza acquisita. Nessun codice di collaudo ha istruzioni per fermare o reinstallare l’agente.
- Non collaudati in questo rilascio: nuova installazione completa 1.2.2, PUT/PATCH reali di rinomina (l'ispezione richiesta e sola lettura), riavvio del PC, turni Codex, Voice o ulteriori computer.

## Backup, rollback e ricostruzione

Staging cloud separato dalla 1.2.1, copia dei soli file elencati nel manifest; il file privato presente accanto alla vecchia copia non viene letto, copiato o impacchettato. `script-history.json` conserva gli hash pubblicati 1.2.1. Aggiornamento completo script: stesso backup verificato e marker pending. La sola rinomina non installa file nel runtime operativo.

Per tornare a nomi precedenti, impostare nei tre override i valori delle ricevute private `renames.<risorsa>.from` e rieseguire il comando con un token valido, soltanto dopo aver riconciliato eventuali operazioni incerte. Verificare gli ID; non ricreare/cancellare risorse e non eliminare il journal per forzare retry. Per rollback script valgono i backup/receipt descritti nelle release precedenti, senza sostituire binario, identita, credenziali o prove economiche.

```text
node --test windows-mcp-agent/installer/Test-Cloudflare.cjs
powershell -NoProfile -File windows-mcp-agent/installer/Test-SetupSettings.ps1
powershell -NoProfile -File windows-mcp-agent/scripts/build-desktop2-installer.ps1 -AgentPath <binario-0.6.1-verificato> -OutputDirectory <nuova-cartella>
```

Sorgenti runtime nuovi: `Cloudflare-Client.ps1`, configuratore nomi e aggiornamenti al provisioner/settings. Il configuratore nomi e un entrypoint del payload di setup, non uno script da installare nel runtime. Pubblicare sorgenti/test/docs/checksum su Git e verificare il nuovo SHA remoto prima della consegna.

ZIP distribuito SHA-256: `d2d5a88b211280e89531bbaee4738aa31de69452b8d40200521a3728733dbc5b`.
