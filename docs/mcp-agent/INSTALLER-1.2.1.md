# Installer 1.2.1: impostazioni e segreti da file locale

## Ambito e stato, 2026-11-01

Richiesta utente: impostare variabili/secret in un file letto dall'installer per evitare domande console. Base remota verificata `5090a186d84cd4e83d086c09d603bde7aa7f763e`, branch `reconcile-windows-mcp-agent-20261031`. Agente distribuito sempre 0.6.1, SHA-256 `9ff4a136951f464925cc55b96e1a968174204199fa9c7ff681b998b3542742c1`, binario invariato; Android distinto e non modificato. Nessuna nuova operazione su Desktop1 in questo lavoro.

Implementati lettore e collegamento di `setup.local.env` a Setup e Cloudflare auto/manual, modello privo di credenziali e documentazione. Test **effettivamente eseguiti** nel cloud Linux: 20/20 test Node del provisioning Cloudflare invariato, hash di tutti i payload, ZIP integro ed esclusione file locali. **Non eseguiti:** test nuovi `Test-SetupSettings.ps1` per lettura/ACL/no-prompt, test PowerShell/Windows del setup completo, API Cloudflare reali o plugin ChatGPT/Voice. Il runtime PowerShell non e disponibile in questo ambiente; non sono stati sostituiti test reali con un parser simulato. Pubblicazione sorgenti, installazione e collaudo sul secondo PC sono esiti distinti; leggere il commit remoto per la pubblicazione.

## Procedura semplice

1. Estrarre tutto il nuovo ZIP in una nuova cartella.
2. Copiare `setup.example.env` come **`setup.local.env`**, accanto a `INSTALLA.cmd`; evitare l'estensione nascosta `.env.txt`.
3. Aprire il file con Blocco note, salvare UTF-8 e compilare token API Cloudflare ed email Access. Verificare install root, dispositivo e zona; mantenere quelli del tentativo gia iniziato.
4. Avviare `INSTALLA.cmd`. Il file viene individuato automaticamente, anche avviando da un'altra cartella di lavoro. Ripresa con lo stesso comando e la stessa destinazione; `RIPRENDI.cmd` e `COLLEGA-CODEX.cmd` sono alias dello stesso setup.

Esempio senza credenziali reali:

```dotenv
INSTALL_DIR=D:\Jarvis
DEVICE_NAME=Desktop2
MCP_PORT=8765
SETUP_NO_PROMPTS=true
SETUP_ALLOW_BROWSER_LOGIN=true
SETUP_REMOTE_MODE=auto
CLOUDFLARE_ZONE_NAME=example.com
CLOUDFLARE_API_TOKEN=<inserire soltanto nella copia privata sul PC>
CLOUDFLARE_ACCESS_EMAIL=<email esatta autorizzata>
```

Il modello distribuito usa il dominio gia scelto dall'utente ma lascia vuoti token/email. La guida `CONFIGURAZIONE.txt` elenca tutti i campi. I placeholder non sono valori validi: sostituirli nella copia privata, mai inviare il file compilato in chat o nel repository.

## Contratto di lettura

`Setup-Settings.ps1` legge al massimo 64 KiB di UTF-8 rigoroso, con o senza BOM. Righe vuote o che iniziano con `#` ignorate; ogni altra riga deve essere `KEY=value`. Nomi maiuscoli da whitelist; duplicati e chiavi sconosciute rifiutati. Split al primo `=`; spazi esterni rimossi e coppia di virgolette esterne facoltativa. Backslash, `$`, `%`, `#`, virgolette interne e `=` nel valore restano dati letterali: nessun dot-source del file, `Invoke-Expression`, espansione environment o shell. Non usare commenti a fine riga. Booleani soltanto `true`/`false`; porta, nomi, percorsi, email e selettori zona validati senza stampare i valori. Errori con codice/chiave ammessa/numero riga.

Il lettore usa percorsi locali senza link/junction/UNC secondo `Get-FullPath`, limita la ACL del singolo file a SID corrente e SYSTEM e fallisce se la protezione non e applicabile. Legge da handle che non condivide scrittura/eliminazione. Il file contiene segreti **in chiaro per scelta dell'utente**, non e un secret manager cifrato. Non viene copiato nel runtime, in un backup automatico, nei log/ricevute o nel registro; a fine setup la mappa in memoria viene svuotata. Il token API continua a entrare nel configuratore Node tramite stdin, mai argv o environment. Il token tunnel runtime separato rimane nel Config protetto come nella 1.2.0.

Il pacchetto contiene solo `setup.example.env`. `Assert-SetupPackage` e builder rifiutano manifest che elencano file `.env` diversi dal modello o copie di `setup.local.env`. Il builder copia esclusivamente i nomi del manifest, mai tutti i file della cartella. `.gitignore` nella cartella installer esclude `setup.local.env`, copie con suffisso e `*.secret.env`; per nomi personalizzati usare una cartella esterna al repository. Il generatore del pacchetto distribuito usa una lista nota dei file sorgente, non un glob che possa incorporare il file compilato.

## Opzioni e precedenza

| Campi | Comportamento |
|---|---|
| `INSTALL_DIR`, `DEVICE_NAME`, `PROJECTS_ROOT`, `MCP_PORT` | Percorso/dispositivo/progetti/porta; progetti vuoto deriva da root/Projects |
| `CODEX_EXECUTABLE` | Facoltativo, deve indicare una CLI ufficiale compatibile; vuoto conserva ricerca/installazione |
| `SETUP_NO_PROMPTS` | Default true quando un file locale e presente; false consente domande per dati mancanti |
| `SETUP_ALLOW_BROWSER_LOGIN` | Default true con file; consente login ChatGPT nel browser se manca, senza API key OpenAI |
| `SETUP_REMOTE_MODE` | `auto` default, `manual` oppure `skip` |
| `SETUP_NO_TASK`, `SETUP_NO_SHORTCUTS` | true/false per installazioni nuove, default false; nessuna rimozione implicita su quelle complete |
| `CLOUDFLARE_API_TOKEN` | Segreto per provisioning auto |
| `CLOUDFLARE_ZONE_NAME`, `CLOUDFLARE_ZONE_ID` | Se entrambi presenti devono identificare la stessa zona; nessuna scelta arbitraria tra piu zone |
| `CLOUDFLARE_HOST_LABEL` | Vuoto deriva dal dispositivo, ad esempio jarvis-desktop2 |
| `CLOUDFLARE_ACCESS_EMAIL` | Singola email esatta della policy Access |
| `CLOUDFLARE_HOSTNAME`, `CLOUDFLARE_TEAM_DOMAIN`, `CLOUDFLARE_APP_AUD`, `CLOUDFLARE_TUNNEL_ID`, `CLOUDFLARE_TUNNEL_TOKEN` | Soltanto percorso manuale; dati gia verificati nella ripresa riusabili |

Parametri CLI espliciti prevalgono sui corrispondenti valori del file; poi valgono dati di ripresa/default. Una configurazione file/CLI che contraddice dispositivo/progetti/porta di un'installazione riconosciuta viene rifiutata prima delle modifiche. Nel provisioning auto, zona/email/etichetta configurate devono coincidere con il journal esistente; il cambio token e ammesso e sottoposto alle normali verifiche account/zona. I parametri salvati non vengono sovrascritti silenziosamente.

`-SettingsFile <file locale assoluto>` consente un file esterno; se esplicitamente indicato ma mancante produce errore. Senza file default presente il percorso interattivo continua a funzionare. Non esistono variabili per disattivare controlli economici, attestazioni, UAC o autenticazione. `SETUP_NO_PROMPTS=true` impedisce tutti i Read-Host gestiti dal setup; dati mancanti producono `SETUP_CONFIG_REQUIRED_<NOME>` e conservano la ripresa. Browser e UAC possono ancora richiedere azione umana. `skip` lascia la configurazione remota dichiarata incompleta. I segreti mancanti vengono rilevati prima dei download/installazioni quando possibile; la scelta della zona richiede la lettura reale dell'account.

## Backup, rollback e continuita

Aggiornare da 1.2.0 estraendo una nuova cartella e copiando privatamente il proprio `setup.local.env`; non cancellare destinazione o registro. File modificati localmente non riconosciuti restano protetti. `script-history.json` comprende gli hash pubblicati 1.2.0; nuovo helper settings incluso nella transazione con backup e marker pending degli script. Repair riconosce anche tentativi incompleti 1.2.1. Binario e credenziali/identita runtime rimangono invariati. Non eseguire l'installer su Desktop1 per collaudare Desktop2.

Rollback script: arrestare l'istanza corretta; usare ricevuta/hash dei backup `Backups/installer-scripts-*`, ripristinare gli script precedenti, rimuovere helper nuovi solo se ancora coincidenti con l'hash after della ricevuta. Ripristinare agent.env solo dal backup pertinente e senza sostituire identita o credenziali runtime. Il file locale non e modificato nei contenuti e non viene incluso nei backup: mantenerlo privato. Prove economiche mai copiate o prorogate. Per risorse remote parziali, valgono i vincoli [1.2.0](INSTALLER-1.2.0.md): nessun replay incerto o cancellazione automatica di Access; rimozione eventuale prima DNS, poi tunnel, infine app dedicata verificando gli ID.

## Test e ricostruzione

```text
node --test windows-mcp-agent/installer/Test-Cloudflare.cjs
powershell -NoProfile -File windows-mcp-agent/installer/Test-SetupSettings.ps1
powershell -NoProfile -File windows-mcp-agent/scripts/build-desktop2-installer.ps1 -AgentPath <binario-0.6.1-verificato> -OutputDirectory <nuova-cartella>
```

Il nuovo test PowerShell e isolato: usa solo cartella temporanea, segreto fittizio e zone simulate; Read-Host viene sostituito con errore per scoprire prompt inattesi. Verifica parsing letterale, errori senza segreti, opzioni, selezione zona, conflitti di ripresa, UTF-8 e ACL reali Windows. **Predisposto ma non eseguito** in questa sessione. Il builder con `-RunTests` esegue anche questo test; non usare il Desktop1 operativo per il collaudo completo del setup. I 20 test Node eseguiti verificano il provisioning invariato, non certificano il lettore PowerShell.

ZIP distribuito SHA-256: `fba24401070c7d852835929b60c29ea1d35201d6113c341918f834daa6255ad8`.
