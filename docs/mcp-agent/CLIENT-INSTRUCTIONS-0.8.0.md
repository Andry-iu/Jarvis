## Aggiornamenti remoti e catalogo client

`device_call` inoltra anche `update_prepare`, `update_execute`, `update_status`
e `log_read` ai dispositivi gia associati. La descrizione storica del client che
nega i batch di aggiornamento e obsoleta; il catalogo server e gia corretto.
Prima di dichiarare una capacita assente confrontare `tools/list`, sorgente e
ricevute. Non chiedere copia manuale o nuovo pairing quando il canale esiste.
Usare l'unico updater canonico e verificare risultato, non il solo dispatch.
[Catalogo operativo e formato delle chiamate](MCP-CATALOG-0.8.3.md).

> Abbinamento interattivo aggiornato: [PAIRING-0.8.3.md](PAIRING-0.8.3.md). Controllare la versione del destinatario prima di descrivere la finestra.

# Istruzioni client — gateway universali 0.8.0

Ogni agente controlla il proprio PC e inoltra ad altri senza cambio di ruolo.
La scelta installer locale/Cloudflare riguarda solo l'ingresso esterno.
Non chiedere dati Cloudflare agli agenti locali. Avvio automatico all'accesso
Windows; non promettere attivita grafica prima dell'accesso o sul desktop bloccato.

## Identita e nomi

Usare il nome univoco richiesto dall'utente come selettore. Risolverlo nei record
attivi del gateway e mantenere machine_id e pin associati. Nomi non sono
credenziali: non assegnare fiducia dalla sola scoperta. In caso di duplicato o
identita diversa non scegliere a caso e non sovrascrivere il vecchio dispositivo.
Dismissione richiesta a voce: device_retire su tutti i gateway conosciuti coinvolti,
con i loro SHA correnti; conservare storico. Offline non e una dismissione.

## Aggiungere un computer

1. Identificare il gateway gia raggiungibile con machine_info/device_list.
2. Se il PC nuovo non ha un agente, usare l'installer amministrativo locale fornito;
   il solo collegamento di rete non abilita installazione remota. Un canale
   Windows remoto gia autorizzato e un prerequisito distinto per installare da remoto.
3. Cercare con device_discover sul gateway a cui il PC e collegato, anche attraverso
   device_call. Auto: /24 delle interfacce private, massimo1024 indirizzi; per
   un'altra porzione specificare un CIDR /24 o piu stretto. Scoperta non esaustiva
   per grandi reti e non permanente. Rifiutare ambiguita di nome/certificato/identita.
4. Primo abbinamento: trasferimento privato del peer-enrollment.json nel gateway,
   mai lettura/stampa in chat. Abbina.cmd importa il file, oppure mettere un file
   in Config/enrollments e chiamare device_pair con filename, device_id, label,
   expected_sha256 da device_list e request_id nuovo. Verifica TLS e machine_id
   prima della registrazione; nessun riavvio necessario per usare la nuova chiave.
5. Per piu passaggi registrare il destinatario finale anche nei gateway precedenti
   con lo stesso device_id/machine_id e via del prossimo gateway direttamente
   raggiungibile. Propagare i record lungo il percorso; non confondere via con
   un endpoint raggiungibile solo sul gateway successivo. Iniziare dalla foglia.
6. Verificare machine_info del destinatario e una lettura innocua prima di azioni.

## Invocazione

Al primo ingresso usare il relativo plugin Desktop1 o Desktop2 e il machine_id
verificato. device_call richiede device_id e call={operation:<tool pubblico>,...}.
Il machine_id interno, se presente, e quello FINALE, distinto da quello esterno.
Il destinatario originale rimane vincolato su tutti i passaggi. Non annidare a
mano device_call: il percorso e nei record via. Gateway in cicli/retired rifiutati;
limite 8 nodi. Tutti gli strumenti pubblici sono inoltrabili, anche legacy/update;
permessi Windows, UAC, stato sessione e guardie economiche restano vincolanti.

Se il catalogo del client e precedente, usare il task_start esistente:
operation=runtime, request completa con operation e machine_id. Non serve
ricollegare il plugin solo per eseguire le nuove operazioni. Il refresh effettivo
del catalogo diretto e distinto dalla riuscita della compatibilita.

Conservare request_id/update_id/queue_id/job_id sul DESTINATARIO. Su errore o timeout
leggere la ricevuta con lo stesso percorso; non ripetere automaticamente azioni.
update_prepare/update_execute continuano a usare batch CRLF, hash e ID esatti.
update_status conferma gli stati persistenti, non il successo della sostituzione:
leggere ricevuta/hash/versione dell'unico updater canonico. Se un gateway si
riavvia, attendere il ritorno del percorso e recuperare l'esito, senza replay.

## Configurazione e segreti

`device_list` fornisce configuration_sha256; le scritture richiedono SHA e
request_id. Nome e kind non cambiano le capacita. Nuova identita richiede nuovo
device_id dopo dismissione; record vecchio conservato. Mai leggere segreti per
esibirli nel modello o inserirli nei campi del comando: usare riferimenti locali.
Aggiornare documentazione curata e appunti dei PC, mai esportare log/ricevute grezzi.
