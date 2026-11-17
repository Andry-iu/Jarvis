# Verifica candidato IRCESP32 0.8.5 — 17 novembre 2026 UTC

Base Jarvis operativa: be3e21784e7da64fd1770abef0f1cb450ec83456, branch di lavoro feature/ircesp32-ap-camera-20261117. Ambiente effettivo: workspace Linux x86_64 della sessione, distinto da Desktop2. Nessuna modifica Android/main o agente operativo.

## Implementato

Adattatore HTTP per protocollo dispositivo 0.2 e AP privato, quattro tool MCP, validazione input, provenienza/integrità JPEG, ricevute durevoli senza foto e senza replay. Endpoint configurati esplicitamente, niente proxy o redirect, token da secrets.env. Sorgenti installer/versione unica 0.8.5, esempio opzionale senza credenziali e upgrade assistito da 0.8.4 tramite updater canonico invariato.

## Eseguito

SDK Go **1.27.0** ufficiale Linux/amd64, archivio verificato contro SHA-256 del manifest JSON go.dev. SDK e cache isolati, nessun cambiamento al Go di sistema. Per eseguire i comandi di ripresa impostare PATH sul binario del SDK verificato, GOTOOLCHAIN=local e GOCACHE privato; non usare /usr/bin/go di questo ambiente come SDK.

Dal sottoprogetto windows-mcp-agent:

- `go test -count=1 ./...`: PASS.
- `go test -race -count=1 ./...`: PASS finale (4,649 s), compresi gli ultimi test del blocco pinout e dei redirect.
- `go vet ./...`: PASS.
- `GOOS=windows GOARCH=amd64 go build`: PASS, candidato fuori Git.
- `GOOS=windows GOARCH=amd64 go test -c`: PASS, sola compilazione dei test, nessuna esecuzione Windows implicita.
- Pacchetto candidato cross-build `Jarvis-Agent-Setup-0.8.5.zip`: CRC, dimensioni/hash di ogni file del manifest, inclusione dell’esempio IRCESP32 ed esclusione configurazioni private PASS. SHA sorgente incorporato nel PE: `991a5872ba4a243929df4a6c552cb55e302d3453`, `vcs.modified=false`. Checksum pubblicabili in [CHECKSUMS-IRCESP32-0.8.5.json](CHECKSUMS-IRCESP32-0.8.5.json); ZIP/binario fuori Git, non certificati per installazione.
- Parser PowerShell 7.6.9 su installer-network/scripts: PASS. Non equivale a eseguire installazione/upgrade su Windows.

I nuovi test usano esclusivamente server fittizi loopback e immagini sintetiche: identità del dispositivo, boot/sequenza, JPEG/hash/dimensioni, frame precedente alla richiesta, token/redirect, validazione comandi, camera non verificata mai chiamata, ricevute persistenti senza foto e nessun replay di input/cattura incerti o già completati. Non creano fiducia o interagiscono con un ESP32 reale.

La prima esecuzione senza accesso loopback è stata bloccata dalla sandbox prima dei test HTTP. Un'esecuzione race completa successiva ha fallito l'asserzione del test esistente TestSlowQueuedCodexDoesNotDelayIndependentProject, senza segnalare data race; il test originale isolato su baseline per 20 esecuzioni è passato. La suite finale con SDK corretto anche nel PATH dei sottoprocessi è passata. Nessuna modifica allo scheduler né causa puntuale dichiarata.

Firmware, nell'altro repository: Arduino CLI1.6.0/core3.3.3, build generica AP con camera disabilitata PASS e test host politica configurazioni PASS. Collegamento driver separato con mappa sintetica solo per compilazione e PSRAM OPI PASS, senza dichiarare verificato il pinout diymore. Numeri e opzioni nel relativo docs/VALIDAZIONE.md.

## Non eseguito

Nessuna programmazione ESP32, verifica fisica pinout/USB/VBUS/PSRAM, prova radio/AP, acquisizione di monitor reale o input sul bersaglio. Nessun dato reale di velocità, leggibilità o memoria libera a runtime.

Nessun test Windows nativo o nuova installazione/upgrade/rollback 0.8.5; nessun aggiornamento degli agenti, appunti privati o cataloghi client. machine_info Desktop2 restituisce errore MCP interno in due letture separate; non è una prova di credenziali mancanti. Senza identità corrente non sono state effettuate mutazioni remote.

Per una release operativa restano i controlli Windows/banco e la procedura canonica di distribuzione, con backup/rollback e verifica per ogni PC. La pubblicazione Git del candidato non costituisce un rilascio installato. Non consegnare un pacchetto come collaudato dalla sola cross-compilazione.

Firmware/protocollo esatto verificato: [`25ffa8a94dd45117dd50d4c65d2b32a077935815`](https://github.com/Andry-iu/Autech-IRCESP32/commit/25ffa8a94dd45117dd50d4c65d2b32a077935815). Include DHCP senza annuncio gateway/DNS; collaudo Windows delle route ancora aperto.

## Aggiornamento successivo: diagramma fornito dall'utente

Profilo firmware aggiornato al commit [`54c00028e15076479813e5fdee6761b83cbc7593`](https://github.com/Andry-iu/Autech-IRCESP32/commit/54c00028e15076479813e5fdee6761b83cbc7593). I 14 GPIO camera sono trascritti dall'immagine del pinout della scheda dual USB-C fornita dall'utente. GPIO38 è SD_CMD; PWDN/RESET della camera non mostrati vengono lasciati a -1 senza pilotare GPIO. Il flag di verifica indica riscontro documentale, non collaudo elettrico. Il disegno raffigura OV2640; il progetto resta configurato per OV3660, con rifiuto di un PID diverso.

Build firmware pulita Arduino CLI1.6.0/core3.3.3 con flash16MB/PSRAM OPI: PASS, 1.070.055byte programma e79.616byte globali. Test configurazioni host PASS (profilo standard, override pin non implicitamente verificato, disabilitazione esplicita). Nessuna scheda programmata o prova fisica. Nuova lettura Desktop2 ancora con errore MCP interno; nessun test Windows nativo o aggiornamento operativo dichiarato.

Questa è una modifica del profilo firmware e della documentazione: sorgente/candidato binario Jarvis 0.8.5 e checksum precedenti restano invariati. Nessuna nuova release agente o distribuzione implicita. Il precedente riferimento firmware resta storico.

## Ripresa Desktop2 — verifiche native completate

Il 17 novembre 2026 alle21:20UTC il collegamento diretto è tornato utilizzabile: identità Desktop2 verificata, agente0.8.4 elevato e coda libera. Eseguita preparazione **PrepareOnly**, senza aggiornare il processo operativo, dal sorgente esatto784ffee47190ad56fc80f3409cabc3e2a0e5f3ef. Profilo già documentato Go1.27.0/cache isolata/GOMAXPROCS1/GOGC25/-p1: suite completa **PASS29.610s**, vet **PASS**, build Windows nativa **PASS**. Stato finale prepared e cleanup_verified=true alle21:26:35UTC. Dati curati in [NATIVE-IRCESP32-0.8.5.json](NATIVE-IRCESP32-0.8.5.json); ricevute e log grezzi restano privati.

Un primo bootstrap generato nella chat si è fermato prima del runner per un overload Split non compatibile con Windows PowerShell. Dopo esito terminale, corretto il parser con IndexOf/Substring e usato un nuovo ID; nessun replay o modifica del processo agente. Il runner ha ripulito checkout/cache temporanei; candidato e SDK gestiti sono conservati.

L’agente operativo resta0.8.4; nessun firmware programmato. Restano da eseguire test installer/upgrade/rollback0.8.5 isolati, collaudo hardware e distribuzione canonica sui PC conosciuti. Gli appunti privati Desktop2 vengono aggiornati con la ricevuta di questa ripresa. Le precedenti dichiarazioni di indisponibilità e di test Windows mancanti sono storiche.
