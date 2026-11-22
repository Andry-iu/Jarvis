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

## Rettifica operativa del 22 novembre 2026: Desktop1

**Desktop1 e stato gia modificato durante questa conversazione:** il deploy 0.8.6 e stato eseguito e verificato tramite `agent_update` (`jarvis-mouse-086-desktop1-20261122-152330`), con versione live 0.8.6, backup, hash installato e pulizia verificati. Solo **in seguito** il connettore Desktop1 ha iniziato a restituire `The tool failed internally`. Non confondere questo errore successivo con il mancato intervento iniziale.

Il successivo ordine di aggiornare Desktop1 alla **0.8.7** e stato ricevuto ma **non eseguito**: il comando `agent_update` 0.8.7 non e stato inviato, perche la connessione diretta risultava indisponibile. Non esistono quindi ricevute che dimostrino una 0.8.7 installata.

L'utente ha chiesto espressamente di **non toccare Desktop2**. Nel tentativo precedente sono state compiute interrogazioni di sola lettura su Desktop2 per cercare una via di comunicazione verso Desktop1; non sono state inviate modifiche, accoppiamenti o aggiornamenti a Desktop2. Dopo la correzione dell'utente, **non contattare ulteriormente Desktop2, neppure come gateway, per questa attivita**.

Il ripristino del collegamento diretto Desktop1 resta necessario per procedere all'aggiornamento alla 0.8.7 e per controllare i comandi mouse. Nessun aggiornamento di un computer deve essere inferito da test Git o compilazioni.

## Stato operativo verificato: Desktop1 e Desktop2 aggiornati alla 0.8.7 (22/11/2026)

**Desktop1**: installazione 0.8.7 eseguita e verificata con `agent_update` request_id `jarvis-d1-update-087-20261122-155814`, sorgente `d8936a750c9c8513342b5a699941486105838faf`. Esito `completed` alle 16:00:44 UTC, `cleanup_verified=true`, versione live `0.8.7`, PID `147264`, SHA256 installato `4364e65959c2955a0094fec5a2c737bfc05ac4fe4ae8bf0da865bba15cb12fd4`. Backup `D:\Jarvis\Backups\self-update-5dd8dbf312f348ea9e88b70c69728efd`. Un tentativo successivo di interrogare il connettore ha restituito errore interno; non annulla la ricevuta precedente.

**Desktop2**: partenza confermata da `0.8.4` con agente amministratore inattivo. Il vecchio aggiornamento alla `0.8.5` (`jarvis-source-update-d2-085-20261119-01`) risultava da oltre 48 ore in `building`; runner lock libero e nessun processo corrispondente. Solo il riferimento `active.json` del vecchio tentativo e stato **archiviato**, senza cancellare la ricevuta del precedente aggiornamento ne reinstallare la 0.8.5. Nuova richiesta separata `jarvis-d2-update-087-20261122-170630` dal commit `d8936a750c9c8513342b5a699941486105838faf`: `completed` alle 16:08:11 UTC, `cleanup_verified=true`, `machine_info.version=0.8.7`, PID `5476`, SHA256 `072277d7ef9f04481f344b469d14accb83f8030ff62db90d3f2666316fa7c789`. Backup `D:\Jarvis\Backups\self-update-83cea8fcba0f4cc48b490cb66ed80d76`. Dopo il riavvio `agent_status.state=idle`; rimossi i quattro file diagnostici temporanei dalla root progetti di Desktop2.

**Release complessiva:** il codice coordinato 0.8.7 e in `main`; le due installazioni Windows sono verificate. Rimangono **non completati** il pacchetto ZIP installer 0.8.7, gli APK Android firmati/da distribuire, il collaudo hardware ESP32 e l'esposizione del tool `ui_mouse` nel connettore ChatGPT con prova reale di spostamento e clic. Non presentare questi controlli come gia superati. Le annotazioni inferiori, con stato precedente dei desktop, sono storico e non il quadro corrente.
