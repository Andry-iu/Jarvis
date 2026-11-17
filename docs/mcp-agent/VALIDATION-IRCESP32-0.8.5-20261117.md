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
- Parser PowerShell 7.6.9 su installer-network/scripts: PASS. Non equivale a eseguire installazione/upgrade su Windows.

I nuovi test usano esclusivamente server fittizi loopback e immagini sintetiche: identità del dispositivo, boot/sequenza, JPEG/hash/dimensioni, frame precedente alla richiesta, token/redirect, validazione comandi, camera non verificata mai chiamata, ricevute persistenti senza foto e nessun replay di input/cattura incerti o già completati. Non creano fiducia o interagiscono con un ESP32 reale.

La prima esecuzione senza accesso loopback è stata bloccata dalla sandbox prima dei test HTTP. Un'esecuzione race completa successiva ha fallito l'asserzione del test esistente TestSlowQueuedCodexDoesNotDelayIndependentProject, senza segnalare data race; il test originale isolato su baseline per 20 esecuzioni è passato. La suite finale con SDK corretto anche nel PATH dei sottoprocessi è passata. Nessuna modifica allo scheduler né causa puntuale dichiarata.

Firmware, nell'altro repository: Arduino CLI1.6.0/core3.3.3, build generica AP con camera disabilitata PASS e test host politica configurazioni PASS. Collegamento driver separato con mappa sintetica solo per compilazione e PSRAM OPI PASS, senza dichiarare verificato il pinout diymore. Numeri e opzioni nel relativo docs/VALIDAZIONE.md.

## Non eseguito

Nessuna programmazione ESP32, verifica fisica pinout/USB/VBUS/PSRAM, prova radio/AP, acquisizione di monitor reale o input sul bersaglio. Nessun dato reale di velocità, leggibilità o memoria libera a runtime.

Nessun test Windows nativo o nuova installazione/upgrade/rollback 0.8.5; nessun aggiornamento degli agenti, appunti privati o cataloghi client. machine_info Desktop2 restituisce errore MCP interno in due letture separate; non è una prova di credenziali mancanti. Senza identità corrente non sono state effettuate mutazioni remote.

Per una release operativa restano i controlli Windows/banco e la procedura canonica di distribuzione, con backup/rollback e verifica per ogni PC. La pubblicazione Git del candidato non costituisce un rilascio installato. Non consegnare un pacchetto come collaudato dalla sola cross-compilazione.
