> Procedura storica 0.8.1/0.8.2: dalla 0.8.3 usare [inserimento obbligatorio](PAIRING-0.8.3.md).

# Abbinamento con conferma locale — 0.8.1 / installer 0.8.1

Installare con INSTALLA.cmd e assegnare un nome. Chiedere in chat «Collega
SoftPlcTest1 tramite Desktop1». Il gateway avvia la richiesta; la chat mostra
un codice, il nuovo PC una finestra con nome dichiarato, indirizzo e identita
del richiedente e lo stesso codice. Confrontarlo e scegliere Consenti solo per
la richiesta voluta. Nessun file o segreto da trasferire. Il nome non autentica.
Non automatizzare la conferma locale mediante UI remota. Non esiste un comando
MCP per approvare. Rifiuto, chiusura, blocco schermo, timeout o errore non abilitano
il controllo. Un pending dopo riavvio non viene ripresentato automaticamente.

## Protocollo client

1. machine_info e device_list sul gateway; device_discover con query /32 se IP noto.
2. device_pair_begin: machine_id gateway, request_id nuovo, label leggibile del
   gateway, device={device_id,name,machine_id,endpoint,certificate_sha256} dalla
   scoperta. Nessun token_env, credenziale o via.
3. Mostrare comparison_code e attendere confronto e conferma sul nuovo PC.
4. device_pair_status con lo stesso request_id fino a approved/denied/expired/
   interrupted/unavailable. Nessun nuovo begin per fare polling.
5. Se approved: device_list per SHA corrente, device_pair_complete con pairing_id
   originale, expected_sha256 e NUOVO request_id stabile di completamento.
6. Verificare device_list, device_call machine_info e una lettura innocua.

Catalogo precedente: task_start operation=runtime e request completa.
Inoltrabile a un gateway intermedio gia associato; il nuovo target deve essere
raggiungibile direttamente dal gateway scelto. Propagare poi i record via come
nella0.8.0. Nessun tunnel Cloudflare aggiuntivo sui PC locali.
Errori CAS: nuova lettura e nuovo request_id di completamento; niente nuova
conferma. Esiti incerti: conservare IDs e verificare ricevute, niente replay.

## Protezioni

/gateway/pair ammette solo richieste/stato da IP privati via TLS/POST JSON;
nessun Origin browser, body limitato, niente comandi prima dell'abbinamento.
Codice12 cifre esadecimali vincolato a richiesta, identita richiedente/target,
nome, certificato target e chiave pubblica RSA2048. Polling firmato RSA-PSS e
credenziale sigillata RSA-OAEP. Client con pin TLS e indirizzo IP privato esplicito,
senza proxy/redirect/DNS. Un dialogo attivo, cooldown1min, finestra110s,
protocollo3min. Nomi con controlli/formattazione Unicode rifiutati nel dialogo.
Stato privato persistente; nessuna chiave o credenziale/ciphertext nelle risposte
pubbliche. Importazione tramite lo stesso device_pair atomico/CAS con verifica
identita/TLS; chiave effimera rimossa al completamento e handoff sempre rimosso.
Il vecchio percorso da file resta compatibile.

## Aggiornare un agente0.8.0 non associato

Estrarre ZIP0.8.1 in una cartella distinta, avviare AGGIORNA.cmd come amministratore
dello stesso account e indicare la cartella Jarvis esistente. Non INSTALLA sopra
la cartella. Non servono Go o Cloudflare. Il bootstrap legge SOLO LOCALMENTE le
credenziali gia presenti e usa il listener loopback TLS con pin per passare
update_prepare/execute all'agente. Unico updater canonico in batch separato,
backup e rollback rehearsal; verifica versione/identita/hash. Nome, configurazione,
identita e task di avvio conservati. Nessun task update o secondo updater.
SoftPlcTest1 richiede questo bootstrap perche0.8.0 non ha il nuovo protocollo.
Per gateway gia collegati usare i plugin e lo stesso updater automaticamente.
Stato reale prove: VALIDATION-0.8.1-20261114.md.
