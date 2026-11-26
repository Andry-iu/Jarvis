# Calibrazione prospettica IRCESP32 da chat

Stato al 26 novembre 2026: sviluppo a partire da Jarvis Agent 0.9.8. La procedura usa le catture già disponibili e il mouse HID del firmware 0.4 via USB-TTL o Wi-Fi. Per acquisire il riferimento Windows serve un agente sul PC che pilota il monitor inquadrato, con desktop interattivo sbloccato. Se è un PC diverso dal ponte, deve essere raggiungibile come dispositivo Jarvis associato.

## Calibrare

1. In chat chiedere «Calibra la prospettiva dell'ESP32». Jarvis chiama `ircesp32_calibrate` con `bitmap_ready: false` e chiede di aprire la bitmap a tutto schermo. Questa fase restituisce le istruzioni senza catturare immagini.
2. Sul monitor inquadrato aprire a tutto schermo **Calibrazione-IRCESP32.bmp** oppure una bitmap equivalente: fondo bianco, un solo grande rettangolo nero **pieno**, con margine bianco su tutti i lati. Nel modello 960×540 il rettangolo misura 720×400 pixel. La camera deve essere ferma, orientata dritta e senza immagine specchiata, con l'intero monitor visibile. Spostare il cursore al margine e lasciare il disegno in vista fino alla conferma.
3. Quando l'utente risponde che la bitmap è visibile, chiamare di nuovo `ircesp32_calibrate` con `bitmap_ready: true`, `device_id` dell'ESP32, `target_device_id` del PC Windows da `device_list`, il numero `monitor` da `screen_capture` (da 1 in poi) e un `request_id` univoco. Usare `local` solo se il monitor fotografato appartiene al PC ponte. Il ponte riusa la cattura Windows selezionata e acquisisce subito dopo la foto dalla scheda.
4. Alla risposta `state: calibrated` il profilo è persistito sul ponte in `State/irc-calibration-<device_id>.json`. Contiene l'identità del PC, il monitor e le sue dimensioni, le dimensioni JPEG e la matrice prospettica. Le ricevute conservano i metadati senza conservare le immagini. I casi di rettangolo assente, ambiguo o non abbastanza grande, e di monitor tagliato, restituiscono un errore e preservano la calibrazione precedente.

Il rettangolo fornisce quattro punti omologhi. Il ponte determina una trasformazione proiettiva dal piano del JPEG al piano del monitor. La cattura Windows dà le coordinate normalizzate e registra anche l'origine e le dimensioni fisiche. Nel file viene salvata la trasformazione per la singola scheda, anche dopo il riavvio dell'agente. Il riferimento si calcola sul ponte e può essere riutilizzato quando il PC bersaglio non è raggiungibile via rete, a camera, monitor, risoluzione e inquadratura invariati.

## Muovere il mouse tramite la camera

- Chiamare `ircesp32_capture` e individuare la punta del cursore e il punto di destinazione sulla stessa foto. Passare a `ircesp32_mouse_camera` il `capture_id`, `device_id`, `request_id`, `x/y` del cursore e `end_x/end_y` della destinazione, in pixel del JPEG originale.
- Il ponte rilegge la calibrazione, proietta entrambi i punti sul monitor e invia un solo movimento relativo HID. Usa solo l'ultima foto di quella scheda, valida per 45 secondi e monouso. Un nuovo input HID manuale rende inutilizzabile la foto precedente.
- Acquisire una nuova foto per verificare l'effetto. Se lo scarto è visibile, ripetere con la nuova posizione del cursore. Eseguire un eventuale clic separatamente dopo avere osservato il cursore sul bersaglio. L'accelerazione HID e la qualità ottica incidono sulla precisione.
- Rifare la calibrazione se cambia la posizione della camera, il monitor, la risoluzione o l'inquadratura. Il ponte rifiuta dimensioni JPEG cambiate. Da una foto di utilizzo normale non può rilevare con certezza uno spostamento fisico della camera o un cambio di modalità video del bersaglio.

Per un connettore con catalogo precedente si può usare `task_start(operation="runtime")` con il nome dell'operazione e gli argomenti nella richiesta interna, dopo avere installato una build dell'agente con queste operazioni.

## Verifiche

I test Go usano due immagini JPEG sintetiche con un monitor in prospettiva, verificano la geometria risultante, rifiutano un monitor tagliato e immagini prive di un riferimento univoco, e provano il riuso della calibrazione persistita e il movimento senza replay su un trasporto simulato. Il banco con una camera reale e l'esecuzione su Windows devono ancora essere collaudati.
