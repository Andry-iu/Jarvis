# IRCESP32 via USB-TTL — candidato Jarvis 0.9.8

Con firmware 0.4 collegare la porta TTL della scheda al ponte Windows e chiedere «Rileva la ESP32 via USB». Il comando è `ircesp32_discover` con `transport: "usb"`. Se rispondono più schede, specificare `serial_port` con la COM esatta. La porta OTG va al computer banco. Nella modalità USB il ponte mantiene la connessione Internet abituale.

Il rilevamento interroga solo lo stato sulle COM USB presenti. Stato, foto e input riutilizzano i comandi esistenti sul trasporto selezionato, identificato tramite COM, istanza Windows, ID della scheda e ID di avvio. Si può rilevare di nuovo su un altro ponte senza trasferire file o credenziali. Il JPEG viaggia in binario con verifica di lunghezza, identità, hash e freschezza. Le ricevute esistenti impediscono di ripetere la stessa richiesta di input o foto.

`transport: "wifi"`, oppure il parametro omesso, seleziona l'hotspot già associato. La modalità precedente con file e token continua a essere disponibile. L'installer di rete e l'agente usano la versione 0.9.8. Un client MCP con catalogo precedente deve ricaricare il catalogo per vedere i nuovi parametri.

I test simulati coprono le richieste su UART, il cambio di avvio, la scelta della COM, l'ambiguità e la gestione dei JPEG non validi. La prova fisica su Windows resta necessaria per chip USB-UART, driver, trasferimento dell'immagine reale e input sul bersaglio.
