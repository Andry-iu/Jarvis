# Integrazione componenti Jarvis — release unica 0.8.7

Data: 22 novembre 2026. **Stato candidato, non ufficialmente rilasciato.**

## Inventario sorgente e stato osservato
- `main` al momento del confronto: `139cf885d4b52af9447b2dc682e538679d0dc98c`, app Android JarvisWake e Cube v0.5.123/145, entrambi verificati nel precedente cloud Jarvis; Android Auto/Audi non ancora collaudato fisicamente. `wear` v0.5.93/115 (distribuzione attuale non verificata).
- Agente Windows/installer v0.8.6: sorgenti `f8bec6ee37c0c4bc15315815f3768e8a7d3ba3b2`; installato e verificato su Desktop1 (backup e cleanup) il 22 novembre 2026. L'accesso mouse del connettore non e ancora esposto e il successivo collegamento Desktop1 ha riportato errore interno.
- Ramo `feature/ircesp32-ap-camera-20261117`: codice Go del ponte ESP32 camera e USB HID, con test e protocollo documentato; hardware ancora da collaudare. Integrato nel sorgente come funzione opzionale, non configurata di default. Firmware reale nel repository dedicato `Andry-iu/Autech-IRCESP32`, non sovrascriverlo implicitamente.
- `phone-bridge`, `cloud-runner`, `car-game`, `wear`, script Android e relativi test mantengono il sorgente piu recente di `main`.
- `experiment/chat-context-probe`: esperimento separato, conservato sotto `experiments/chat-context-probe` senza attivarlo nel build Android di produzione.
- `signing-handoff-2026-10-19`: archivio cifrato di recupero chiavi preservato sul proprio ramo privato, **non copiato in main** per minimizzare la diffusione di dati di firma.

## Numerazione
La 0.8.6 e una versione Windows gia distribuita: per modificare ulteriormente l'agente e coordinare i moduli Android si sceglie **0.8.7** come prossimo identificatore unico. Per i package Android il versionCode va portato a 146, maggiore del precedente 145 per JarvisWake/Cube e 115 per Wear. Il cambio del versionName nei sorgenti **non prova l'esistenza di APK aggiornati**, firma o installazione. L'installer 0.8.7 e un candidato sorgente e va impacchettato e testato separatamente.

## Checklist di convalida prima della release definitiva
1. Test Go, go vet e compilazione per agente e IRCESP32; analisi sintattica PowerShell e test installer isolati Windows. Nessuna azione hardware senza banco pronto.
2. Test, lint, assemblaggio e firma Android attraverso il cloud Jarvis autorizzato, preservando il certificato permanente e la progressione degli APK; verificare separatamente Cube e Wear, non dedurre risultati dal solo build script.
3. Controllare il rilascio di `phone-bridge` e cloud runner: fonte e versione backend possono restare indipendenti come servizio, ma il manifest centrale ne deve fissare lo SHA/compatibilita quando distribuiti.
4. Verificare esposizione `ui_mouse` in ChatGPT, movimento e clic innocui su Desktop1; registrare solo esiti osservati.
5. Quando testate, integrare le modifiche nel `main` senza force-push e chiudere il ramo temporaneo. I commit e i test sono evidenze diverse; nessun rilascio binario va dichiarato senza ricevuta e hash verificati.

## Sicurezza e conservazione
Nessun codice Android obsoleto del ramo Windows deve sovrascrivere `app/`, `car-game/`, `wear/` o servizi di `main`. Nessun segreto in Git. I rami storici restano accessibili per verifiche; non cancellarli prima di averne controllato le differenze, incluso lo storico di firma.

## Consolidamento su main e verifiche (22/11/2026)

- PR [#1](https://github.com/Andry-iu/Jarvis/pull/1) integrata con merge commit `f0408ac4a2d1826bc74b71319462cc638fc33a28`.
- GitHub Actions: esecuzione `45040466751` sul commit candidato `af5378c07d4662b197c370574301d00412251d4b`, esito **success** per `go` e `powershell`.
- Il job Go ha completato `go test -count=3 ./...`, `go vet ./...`, compilazione Linux e cross-compilazione Windows; parser PowerShell per 7 script superato.
- Durante i controlli e stato corretto un difetto di sincronizzazione fra chiusura del runtime e salvataggio dei risultati dei comandi, con test di regressione. Le esecuzioni CI precedenti fallite sono mantenute come storia.
- Il `main` contiene i sorgenti Android precedentemente piu avanzati, il Windows MCP Agent 0.8.7 candidato con camera/HID ESP32 opzionale, i servizi non alterati e i documenti storici.
- **Non completato:** build/firma/distribuzione APK 0.8.7, packaging/collaudo installer 0.8.7, collaudo fisico ESP32, refresh `ui_mouse` nel connettore ChatGPT, test puntatore e clic sul desktop. Non distribuire la 0.8.7 in produzione come conseguenza automatica del merge.
- Il vecchio agente Windows 0.8.6 rimane l'ultima installazione effettivamente verificata su Desktop1; il collegamento ChatGPT Desktop1 aveva restituito errori interni dopo il deploy.
