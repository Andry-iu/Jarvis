# IRCESP32 via USB-TTL — candidato Jarvis 0.9.8

Con firmware 0.4 collegare la porta TTL della scheda al ponte Windows e chiedere «Rileva la ESP32 via USB». Il comando è `ircesp32_discover` con `transport: "usb"`. Se rispondono più schede, specificare `serial_port` con la COM esatta. La porta OTG va al computer banco. Nella modalità USB il ponte mantiene la connessione Internet abituale.

Il rilevamento interroga solo lo stato sulle COM USB presenti. Stato, foto e input riutilizzano i comandi esistenti sul trasporto selezionato, identificato tramite COM, istanza Windows, ID della scheda e ID di avvio. Si può rilevare di nuovo su un altro ponte senza trasferire file o credenziali. Il JPEG viaggia in binario con verifica di lunghezza, identità, hash e freschezza. Le ricevute esistenti impediscono di ripetere la stessa richiesta di input o foto.

`transport: "wifi"`, oppure il parametro omesso, seleziona l'hotspot già associato. La modalità precedente con file e token continua a essere disponibile. L'installer di rete e l'agente usano la versione 0.9.8. Un client MCP con catalogo precedente deve ricaricare il catalogo per vedere i nuovi parametri.

I test simulati coprono le richieste su UART, il cambio di avvio, la scelta della COM, l'ambiguità e la gestione dei JPEG non validi. La prova fisica su Windows resta necessaria per chip USB-UART, driver, trasferimento dell'immagine reale e input sul bersaglio.

## Desktop1 — aggiornamento verificato il 26 novembre 2026

**Stato `COLLAUDATO_CON_LIMITI`.** L'aggiornamento dell'agente e l'instradamento della richiesta MCP sono stati verificati su Desktop1. Il collaudo con la ESP32 fisica è in attesa.

- **Sorgenti e build:** [Jarvis `865bf9fd1f5bdcf6c0c83d5ece96fb20d6c50375`](https://github.com/Andry-iu/Jarvis/commit/865bf9fd1f5bdcf6c0c83d5ece96fb20d6c50375) e [firmware `aa45529cad8f69bc77e288003833f81a70bedc06`](https://github.com/Andry-iu/Autech-IRCESP32/commit/aa45529cad8f69bc77e288003833f81a70bedc06). Test Go, inventario USB COM su Windows e controlli PowerShell riusciti. [GitHub Actions](https://github.com/Andry-iu/Jarvis/actions/runs/45846761147).
- **Preflight e aggiornamento:** `machine_info` identificava Desktop1 su 0.9.7 con privilegi elevati e `queue_list` mostrava la coda libera. Usato `agent_update` con commit esatto e `target_version="0.9.8"` secondo il [runbook](UPDATE-FROM-GIT-0.8.4.md).
- **Ricevuta:** `jarvis-desktop1-098-usb-ttl-20261126-01`, `agent_update_status.phase=completed` il 26 novembre 2026 alle 16:25:44 CET e `cleanup_verified=true`. SHA-256 installato `9d6bb8d7bb1d504a32039d233c183986bb837265d62255f6b8be9bb315eb3bef`. L'updater canonico ha predisposto il backup e gestisce il rollback. In caso di esito incerto rileggere la stessa ricevuta prima di inviare altri comandi.
- **Postflight:** `machine_info` ha confermato identità invariata, versione 0.9.8, privilegi elevati e `ircesp32_discover`. Il catalogo della chat non esponeva il comando dedicato. `task_start(operation="runtime", request={operation:"ircesp32_discover", transport:"usb", serial_port:"COM0"})` ha raggiunto il validatore dell'agente e ha ricevuto il rifiuto atteso per la COM volutamente non valida. La prova termina prima dell'apertura della porta.
- **Installer:** il kit contiene l'installer Windows x64 0.9.8 con manifest e hash verificati. Restano il collaudo dell'installer su un Windows indipendente e la distribuzione agli altri PC.
- **Ripresa:** flash firmware 0.4, rilevamento USB/Wi-Fi, JPEG e riscontro dell'input sul PC banco. [Handoff della scheda](https://github.com/Andry-iu/Autech-IRCESP32/blob/feature/usb-ttl-20261126/docs/HANDOFF.md).
