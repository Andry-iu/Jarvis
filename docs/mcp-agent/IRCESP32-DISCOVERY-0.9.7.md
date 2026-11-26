# Rilevamento IRCESP32 dalla chat — candidato 0.9.7

Il destinatario installa Jarvis nel proprio ambiente, collega Windows all'hotspot della scheda e chiede alla chat «Rileva la scheda ESP32». La porta USB OTG della scheda va al computer banco. Il ponte mantiene il collegamento alla propria chat via Ethernet o con un secondo adattatore Wi-Fi.

`ircesp32_discover` verifica l'ESP32 già connessa e restituisce ID, stato camera e disponibilità HID. Se più schede rispondono, usare `interface_name` con il nome esatto dell'interfaccia Windows. Il rilevamento non invia input e non scatta foto. `ircesp32_capture` e `ircesp32_input` continuano a richiedere una richiesta esplicita e un ID richiesta univoco.

L'agente usa il server DHCP dell'interfaccia Wi-Fi connessa senza gateway IPv4, verifica la route e vincola il socket all'interfaccia. Mantiene il rilevamento in memoria e rifiuta di continuare se cambiano l'avvio del dispositivo o l'associazione. Si ripete il rilevamento dopo una riconnessione.

Questa procedura richiede il firmware 0.3, configurato via TTL con SSID e password. Il percorso 0.2 con `Config/ircesp32.json` e token resta disponibile per le schede già configurate. [Preparazione e protocollo](https://github.com/Andry-iu/Autech-IRCESP32/blob/main/docs/PASSAGGIO-PONTE.md).

## Verifiche

Suite Go Linux, test IRCESP32 con race detector, vet e compilazione Windows x64 superati. Le fixture verificano il flusso senza file di configurazione e senza token, foto e input espliciti, scelta interfaccia e rifiuto delle azioni dopo il cambio di associazione.

L'installer di rete e il binario condividono la versione candidata 0.9.7. Il test d'installazione include una verifica di catalogo e dell'assenza di registrazioni ESP32 in una nuova installazione. Restano il collaudo nativo dell'installer e la prova su hotspot e banco effettivi.
