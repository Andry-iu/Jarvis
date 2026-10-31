# Installer 1.1.0 — prerequisiti automatici e ripresa persistente

31 ottobre 2026. Richiesta utente: un unico installer deve riconoscere cio che manca, installarlo e riprendere i tentativi precedenti, senza delegare all'utente i comandi PowerShell gia noti. Agente Windows distinto da Android. Branch reconcile-windows-mcp-agent-20261031; binario 0.6.1 invariato.

## Procedura utente

Estrarre tutto il nuovo ZIP in una nuova cartella ed eseguire **INSTALLA.cmd**. Lo stesso comando serve alla prima installazione e a ogni ripresa. La cartella richiesta e quella dell'installazione Jarvis, non quella del setup: propone D:\Jarvis se il disco D esiste, altrimenti C:\Jarvis-Desktop2. Nome Desktop2 configurabile nelle nuove installazioni, plugin suggerito Jarvis-Desktop2; le copie esistenti conservano il nome registrato. RIPRENDI e COLLEGA-CODEX sono alias dello stesso ingresso.

UAC e richiesto con lo stesso utente Windows; il SID originario viene passato e verificato dopo l'elevazione. Utente diverso, architettura diversa da Windows AMD64 o PowerShell non compatibile producono un rifiuto. Nessuna modifica alle policy di esecuzione persistenti.

## Componenti e comportamento

- Git: copia gia funzionante oppure runtime PortableGit completo dalla release ufficiale Git for Windows, con SHA-256 ufficiale. Estrazione senza dialoghi in una directory privata del setup, senza aggiornare un Git esistente.
- Go: copia Windows AMD64 >=1.23.2 oppure archivio Windows ufficiale stabile con SHA-256 da go.dev. Estrazione in una nuova directory privata, senza sovrapporre alberi Go o cambiare il toolchain di altri progetti.
- Codex: cerca una CLI eseguibile esattamente 0.165.0-alpha.1.2, nativa prima di Windows App. Se manca, scarica ed esegue l'installer ufficiale fissato a quella versione, con CODEX_NON_INTERACTIVE=1. SHA-256 dello script 2D2AACC5270987F1D33EB22969BF1936528A5B09DA152FEC0EB9D4C97DCC0D8F; origine GitHub OpenAI della release esatta. Runtime mantenuto integro. Nessuna ACL WindowsApps alterata, niente copia del solo codex.exe, nessun npm/winget necessario. Non seleziona automaticamente una nuova versione Codex.
- Accesso: controlla login status senza stampare dati account; se necessario, avvia direttamente codex login e l'utente completa l'OAuth ChatGPT nel browser. Non chiede API key. Ambiente della verifica coerente con il profilo predefinito del ponte, senza variabili CODEX_/OPENAI_ ereditate; ambiente del processo chiamante ripristinato dopo il controllo.
- Cloudflared: download ufficiale verificato quando si prepara il collegamento remoto. Configurazione locale riprendibile con valori pendenti conservati e token separato privato; riuso del binario gia verificato, senza sostituzioni implicite.
- Jarvis: pacchetto binario 0.6.1 gia collaudato, credenziali e identita nuove solo per nuove copie. Config/toolchain.json passa Git/Go al PATH dei processi Jarvis, anche dal task di login, senza dipendere dal refresh del PATH globale.

SDK di altri progetti (.NET, Visual Studio/C++, Android) non sono prerequisiti universali di Jarvis e non vengono installati indiscriminatamente.

## Registro, processi ed esiti incerti

Stato privato per PC/utente/percorso in LOCALAPPDATA\Jarvis-MCP\Setup\<hash-percorso>. progress.json, summary.json, download e ricevute sono fuori da Git e dalle cartelle progetto. JSON sostituito atomicamente, con precedente conservato. La stessa radice ha un lock esclusivo; i worker di installazione hanno inoltre un lock comune per utente che resta attivo anche se si chiude il genitore.

Ogni avvio riconcilia i file e le versioni reali: non si fida soltanto del checkpoint. I metadati di download vengono selezionati e fissati nel registro prima di scaricare; la ripresa non cambia release a meta installazione. Cache riusata solo dopo hash; file alterati o incompleti conservati separatamente e mai eseguiti. URL vincolati alle origini attese. Download interrotto ripetibile in sicurezza, senza eseguire il file parziale.

Un worker produce ricevuta running/completed/failed con PID, istante di avvio e risultato. Worker ancora vivo o risultato assente/incerto non vengono duplicati. Un errore confermato puo essere ritentato al successivo avvio dell'installer; un componente gia presente viene verificato e riusato anche se il genitore si e chiuso prima di aggiornare il checkpoint. Se rimane un esito incerto non riconciliabile, l'installer si ferma e indica il registro: non esiste un retry indiscriminato. Nessuna elaborazione software o turno Codex viene avviato o ripetuto.

## Ripresa Jarvis e aggiornamento delle copie precedenti

Riconosciuta la copia 1.0.1 mai avviata e fallita prima di agent.env. Per i tentativi della 1.1.0, Jarvis-Install-Intent.json lega radice, PC e utente prima di copiare i componenti; install-complete.json distingue la conclusione del nucleo locale. Ripresa anche prima di metadati/credenziali e dopo agent.env, purche non vi siano stato operativo, progetti, log o dati aggiunti. La copia parziale viene spostata integralmente in un backup; errore nella nuova copia ripristina l'originale. I task vengono riconosciuti per identita utente effettiva (nome o SID), azione e percorso; un task attivo o diverso non viene toccato. Task proprio fermo esportato/rimosso e ripristinato su errore, senza duplicazione.

Una copia completa 1.0.x conserva binario, token, identita, stato e configurazioni. Gli script vengono aggiornati solo se coincidono con quelli distribuiti nei pacchetti noti (script-history.json), con backup hash verificato e sostituzioni atomiche. Modifiche locali sconosciute rifiutate prima della scrittura. Il runner.lock coordina con l'avvio; durante una transazione interrotta Start rifiuta l'avvio fino alla ripresa. Le copie gia passate alla nuova versione sono riconosciute dai loro hash, senza riscrivere quelle corrette. Backup e ricevute restano sul PC.

Non si ferma automaticamente un agente attivo: se occorre cambiarne script o configurazione, terminare i lavori e Arresta.cmd, poi INSTALLA.cmd. Una ripresa senza modifiche puo interrogare l'agente gia attivo e preserva identita/credenziali. Avvio automatico disabilitato non riabilitato implicitamente.

## Cloudflare, plugin e criterio di completamento

Il software locale e automatizzato. Le risorse dell'account Cloudflare restano da predisporre: tunnel dedicato, hostname/DNS, Access/Managed OAuth. L'installer chiede e conserva localmente i valori necessari; il token e nascosto, mai negli argomenti di processo o nei log del setup. Non crea queste risorse tramite API e non crea il plugin ChatGPT. Nessun acquisto di dominio o piano e nessuna replica del tunnel Desktop1. Se l'utente rinvia, lo stato finale dichiara waiting_cloudflare; lo stesso INSTALLA riprende in seguito.

codex_status verifica account ChatGPT e quota senza inferenza. Il risultato separa local_ready, codex_connected, remote_configured e remote_verified (quest'ultimo false finche manca il collaudo dal client); inference_allowed/blocked_reason restano la decisione economica separata. Quote basse restano osservabili e saldo null resta sconosciuto. Nessuna prova costi copiata, prorogata o alterata; niente acquisti, ricariche, API a consumo o upgrade di abbonamento.

## Validazione

Vedere COLLAUDO.txt per il risultato finale. Test Windows isolati: vecchia suite installer, test del nuovo bootstrap con cache/hash/rete interrotta, worker reali su payload eseguibili di fixture, ricevute/esiti incerti, recupero delle copie parziali, task/rollback, configurazione Cloudflare sintetica riprendibile, aggiornamento copie complete e doppio avvio con account/usage reali senza inferenza. Download dello script Codex ufficiale e metadati/digest ufficiali letti senza eseguire l'installer Codex sul PC operativo.

I test dei componenti mancanti usano fixture eseguibili e archivi isolati: non equivalgono a un'installazione reale dei runtime ufficiali su Windows vuoto. Restano da collaudare il secondo PC fisico, l'installazione effettiva Codex da assente, accesso browser/UAC a doppio clic, le risorse Cloudflare e il nuovo plugin. Nessun servizio operativo Desktop1 sostituito o fermato. Il binario 0.6.1 conserva i test/vet/build del verbale VALIDATION-0.6.1-20261031.md; questa release cambia il setup PowerShell.

## Riproduzione e ripristino

Sorgenti/test completi in windows-mcp-agent/installer; builder scripts/build-desktop2-installer.ps1. Fornire binario 0.6.1 con SHA-256 9FF4A136951F464925CC55B96E1A968174204199FA9C7FF681B998B3542742C1 e directory output nuova. Normalizzazione LF prima dei checksum. Eseguire Test-Installer e Test-Bootstrap sul payload; LegacyZipPath abilita regressioni con il pacchetto originale 1.0.1, CheckNetwork verifica soltanto download/metadati ufficiali.

Ripristino degli script: agente fermo, usare il backup relativo alla stessa installazione e le ricevute prima/dopo; non ripristinare vecchi snapshot dello stato o credenziali di altri PC. I tool privati e i download non sostituiscono quelli globali. In caso di esito worker incerto verificare processo/ricevuta prima di tentare riparazioni; non cancellare progress.json per forzare un nuovo avvio.

Fonti primarie: https://learn.chatgpt.com/docs/enterprise/windows-deployment ; https://learn.chatgpt.com/docs/config-file/environment-variables ; https://learn.chatgpt.com/docs/cli/reference ; https://gitforwindows.org/zip-archives-extracting-the-released-archives.html ; https://go.dev/doc/install ; https://developers.cloudflare.com/tunnel/downloads/ .

## Pacchetto e risultato finale

Jarvis-MCP-Setup-1.1.0.zip, 6765954 byte, 25 file, SHA-256 029E588790450AA87C0498E12AA8C197912741526977A2E84C48DE51A7822129. Test-Installer e Test-Bootstrap: COMPLETE. Correzioni durante i test: chiavi mancanti con StrictMode, liste generiche PowerShell, identita nome/SID dei task e percorso di backup File.Replace; regressioni finali superate.
