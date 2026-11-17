# IRCESP32 — candidato agente/installer 0.8.5

Decisione utente del 17 novembre 2026: fotocamera e USB HID sulla diymore ESP32-S3-CAM N16R8 OV3660, ESP32 **access point Wi-Fi**, PC ponte client di quell'AP. Il ponte mantiene Internet via Ethernet o secondo adattatore Wi-Fi. Si usa il connettore MCP Jarvis esistente, senza un secondo agente né MCP sul microcontrollore.

Firmware e protocollo: repository privato `Andry-iu/Autech-IRCESP32`, documento `docs/RETE-CAMERA.md`, protocollo 0.2. Branch agente dedicato `feature/ircesp32-ap-camera-20261117`, base operativa 0.8.4 be3e21784e7da64fd1770abef0f1cb450ec83456. Nessuna modifica Android o merge implicito su main. Questo è un candidato software, **non una distribuzione completata**.

## Collegamento

- USB OTG dell'ESP32 → PC bersaglio, nessun software sul bersaglio.
- Wi-Fi del ponte → AP WPA2 dell'ESP32, un client.
- Ethernet o secondo adattatore del ponte → Internet/MCP. L'ESP32 non inoltra Internet.
- USB-TTL → provisioning locale temporaneo, come documentato nel firmware.

L'agente non cambia le reti Windows, non crea un bridge/ICS e non apre porte firewall. Rete ESP predefinita 192.168.4.0/24: verificare conflitti con le altre reti del ponte. La rete si può cambiare nel firmware con `IRC_AP_IP` e nel JSON locale. Credenziali AP e token API generati privatamente, mai in chat/Git. Il tratto dispositivo usa HTTP su AP WPA2 privato, senza TLS applicativo: non usare su una LAN condivisa o esporre a Internet.

## Configurazione facoltativa

Copiare `windows-mcp-agent/installer-network/ircesp32.example.json` in `<Jarvis>/Config/ircesp32.json`. Sostituire l'identità con quella ottenuta da `NETINFO` sulla UART della scheda, non con il nome SSID o l'identità del PC.

```json
{
  "devices": [{
    "device_id": "ircesp32-000000000000",
    "label": "Banco — sostituire identita",
    "endpoint": "http://192.168.4.1",
    "token_env": "JARVIS_IRCESP32_TOKEN_BENCH"
  }]
}
```

Il token da 64 cifre esadecimali va solo in `<Jarvis>/Config/secrets.env`, con il nome indicato da token_env. Riavvio agente controllato per applicare nuove variabili d'ambiente; non rimuovere configurazioni esistenti. Il JSON è riletto alle richieste. Nessun device configurato significa elenco vuoto, senza scansione o errori negli altri tool.

Sono ammessi al massimo 16 device espliciti, endpoint con IPv4 privata letterale e porta HTTP 80. DNS, indirizzi pubblici/loopback/link-local, credenziali in URL, percorsi/query e redirect vengono rifiutati. Nessun proxy ereditato per questo trasporto; timeout finiti e nessun retry di POST. I risultati non includono token o il suo riferimento d'ambiente.

## Tool MCP

Tutti richiedono il machine_id dell'agente ponte verificato con machine_info. Per raggiungerlo attraverso altri agenti usare il device_call esistente, con l'identità del ponte distinto dal device_id IRCESP32.

| Tool | Parametri aggiuntivi | Risultato |
|---|---|---|
| ircesp32_list | nessuno | Device configurati, senza scansione né segreti |
| ircesp32_status | device_id | Identità/boot, contatore, input e stato camera autenticati |
| ircesp32_capture | device_id, request_id univoco | JPEG come immagine MCP più metadati, oppure errore esplicito |
| ircesp32_input | device_id, text, request_id univoco | Una richiesta KEY/MOUSE/STOP con ricevuta; dispatch non prova l'effetto OS |

`text`: `KEY <usage 4..115 oppure 0> <modifiers 0..255>`, `MOUSE <dx -127..127> <dy -127..127> <wheel -127..127> <buttons 0..7>`, oppure `STOP`. L'agente arma immediatamente prima di un singolo KEY/MOUSE, con due sequenze distinte nella stessa ricevuta. Il firmware rilascia al termine della richiesta. Unicode, layout/testo e trascinamento fra richieste non sono implementati.

Le operazioni vengono serializzate sul ponte, con verifica dell'identità, boot osservato e prossima sequenza. Il contatore del dispositivo è condiviso fra foto, API input e UART. In caso di collisione con un altro client il comando viene rifiutato, mai ritentato automaticamente. Non gestire la stessa scheda contemporaneamente da due ponte/client.

Le ricevute durevoli sono prenotate prima del dispatch. Ripetere lo stesso request_id non reinvia input né scatta una seconda foto. Il recupero dopo timeout passa da request_status. Per le foto le ricevute contengono **solo metadati**, non il JPEG: una ripetizione completata rimanda alla ricevuta, senza ripresentare una vecchia foto come nuova. Se la risposta MCP è persa dopo il completamento, un nuovo scatto intenzionale richiede un nuovo ID.

La foto viene accettata soltanto se JPEG entro 1 MiB, dimensioni effettive corrispondenti, SHA-256, identità, boot, sequenza e timestamp monotono validi. Il firmware scarta frame precedenti alla richiesta. Metadati: capture_id, tempi dall'avvio del dispositivo, richiesta/ricezione UTC del ponte e origine `ircesp32_camera`. L'ESP32 non ha UTC sincronizzata in questo assetto: `captured_at_utc=null`, nessuna ora di ricezione spacciata per ora esatta dello scatto.

## Stato effettivo e ripresa

- Driver/API e adattatore implementati; il successivo diagramma fornito dall’utente documenta i 14 GPIO camera della scheda dual USB-C. Profilo N16R8 selezionato nel firmware, PWDN/RESET non indicati lasciati a -1 senza pilotarli. Il disegno mostra OV2640, il progetto mantiene il controllo PID OV3660 richiesto: sensore ricevuto e funzionamento restano da collaudare.
- Sorgenti dell'installer aggiornati alla stessa 0.8.5; esempio opzionale incluso nel payload. Updater canonico invariato, upgrade assistito ammette anche 0.8.4.
- Suite Go Linux, race, vet, cross-compilazione Windows e compilazione dei test Windows verificate; dettagli nel verbale `VALIDATION-IRCESP32-0.8.5-20261117.md`.
- Non eseguiti: test Windows nativi, nuova installazione/upgrade/rollback 0.8.5, trasferimento Wi-Fi reale, collaudo HID/camera, aggiornamento PC o refresh del catalogo client. Desktop2 machine_info restituisce errore interno in questa ripresa; nessuna deduzione su credenziali o consenso e nessuna modifica remota effettuata.

Prossimi passi: verificare schema/revisione PCB, memoria e VBUS; collaudare la scheda su banco controllato; riprendere i test Windows tramite la procedura già riuscita e installer isolato; solo dopo i controlli obbligatori usare agent_update/updater canonico, inventario corrente, backup/rollback e verifica di ogni PC. Aggiornare gli appunti privati sul destinatario quando il canale è disponibile. Non sostituire gli agenti operativi dalla sola cross-compilazione.

Firmware/protocollo esatto verificato: [`25ffa8a94dd45117dd50d4c65d2b32a077935815`](https://github.com/Andry-iu/Autech-IRCESP32/commit/25ffa8a94dd45117dd50d4c65d2b32a077935815). Include DHCP senza annuncio gateway/DNS; collaudo Windows delle route ancora aperto.

## Aggiornamento successivo: diagramma fornito dall'utente

Profilo firmware aggiornato al commit [`54c00028e15076479813e5fdee6761b83cbc7593`](https://github.com/Andry-iu/Autech-IRCESP32/commit/54c00028e15076479813e5fdee6761b83cbc7593). I 14 GPIO camera sono trascritti dall'immagine del pinout della scheda dual USB-C fornita dall'utente. GPIO38 è SD_CMD; PWDN/RESET della camera non mostrati vengono lasciati a -1 senza pilotare GPIO. Il flag di verifica indica riscontro documentale, non collaudo elettrico. Il disegno raffigura OV2640; il progetto resta configurato per OV3660, con rifiuto di un PID diverso.

Build firmware pulita Arduino CLI1.6.0/core3.3.3 con flash16MB/PSRAM OPI: PASS, 1.070.055byte programma e79.616byte globali. Test configurazioni host PASS (profilo standard, override pin non implicitamente verificato, disabilitazione esplicita). Nessuna scheda programmata o prova fisica. Nuova lettura Desktop2 ancora con errore MCP interno; nessun test Windows nativo o aggiornamento operativo dichiarato.

Questa è una modifica del profilo firmware e della documentazione: sorgente/candidato binario Jarvis 0.8.5 e checksum precedenti restano invariati. Nessuna nuova release agente o distribuzione implicita. Il precedente riferimento firmware resta storico.
