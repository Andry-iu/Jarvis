# Stato corrente — pulizia 1.1.1, 14 novembre 2026

La pulizia permanente include Releases per default e dopo gli aggiornamenti
verificati il coordinatore esegue Scope=Backups,Releases. Releases non conserva
un archivio storico o obbligatoriamente l'ultima copia: agente attivo in Agent,
ripristino nell'ultimo backup verificato. Radice Releases rimossa se vuota.
Nessun hook autonomo aggiunto al runtime 0.8.4.

Script SHA-256: becc173694134ed8dc1a17c79bcefa6f8d9af7840fdd94e53cafeecb7e35afe0.
Parser e fixture Windows PASS: 18 controlli, inclusi aggiornamento sorgente
preparato, lavoro runtime attivo, anteprima, release recente/bloccata, worktree
Git, rimozione e radice vuota, agente/backup e regressioni batch/anti-replay.
Fixture e staging del trasferimento rimossi nel finally.

| Postazione | Cartelle release rimosse | Byte rimossi |
|---|---:|---:|
| Desktop1 | 10 | 193767848 |
| Desktop2 | 3 | 61532837 |
| SoftPlcTest1 | 5 | 53758559 |
| Totale | 18 | 309059244 |

Prima dell'esecuzione: inventario e riferimenti processi/task/servizi verificati,
nessun riferimento trovato nei 18 bersagli. Esecuzioni completed senza errori;
Releases assente e stesso script verificato sulle tre postazioni. Agente e backup
mantenuto con hash invariati. Inventari/ricevute grezzi restano privati.

Tre vecchi pacchetti sul target avevano LastWriteTimeUtc futuro nonostante la
creazione precedente e nessun uso corrente. Il default5min li ha correttamente
preservati. Dopo verifica esatta dei tre nomi e inattivita, una seconda passata
Scope=Releases, KeepRecentMinutes=0 ha disattivato soltanto la recenza: controlli
su aggiornamenti, lavori, riferimenti, lock, link e sorgenti conservati. Nessun
timestamp modificato. Il test copre l'override0 anche per un timestamp futuro.

## Storico validazione 1.0.1

# Pulizia permanente — 14 novembre 2026

Procedura PowerShell autonoma 1.0.1, runtime agente invariato 0.8.4.
Sorgente: windows-mcp-agent/procedures/Invoke-JarvisCleanup.ps1.
SHA-256: d33589abef31a90d315e2361a16517fab2013655ac36f1c7308073d6b1d8bf8a.

Il precedente collaudo isolato 1.0.0 ha restituito passed=true per anteprima,
retention backup, log/temp/script, file bloccati/recenti, batch attivo,
protezione progetti/configurazione/sorgente, anti-replay, parametri cambiati,
aggiornamento attivo e junction. Installazione e anteprime senza errori sui tre
PC; poi prima esecuzione completata su tutti: 351, 203 e 91 elementi rimossi,
rispettivamente 2790312, 3047309 e 10409947 byte. Backup finale unico per PC.

I batch delle versioni precedenti non sempre hanno una ricevuta durevole.
La 1.0.0 li conservava indistintamente. La 1.0.1 riconosce gli orfani soltanto
con intestazione esatta e assenza di lavori runtime, applicando ancora controlli
su riferimenti attivi, file bloccati, link e recenza. Una ricevuta presente ma
invalida/preparata/non terminale non viene scavalcata.

Nuova fixture Windows pubblicata con la procedura: parser e test passati;
verificati marker legacy, anteprima, recenza, batch preparato, marker non valido,
ricevuta invalida, protezione dati permanenti e anti-replay. Un solo orfano noto
rimosso nella fixture; residui fixture rimossi nel finally. Nessun test ha avviato
un programma di progetto, nuovo pairing o servizio hardware.

Le ricevute operative, gli inventari e i log grezzi restano privati sui PC.
Leggere il corrente State/cleanup-receipts e gli appunti per l'esito di ciascuna
esecuzione. Non ripetere request_id completati o incerti. La procedura non cambia
l'agente, l'avvio, configurazioni o stato dei progetti e non aggiunge un hook
automatico all'updater 0.8.4; il passo dopo un aggiornamento e del coordinatore.

## Chiusura operativa della 1.0.1

Installazione dello stesso script sui tre destinatari ed esecuzione successiva
completate senza errori. Seconda passata: 211, 16 e 5 elementi rimossi,
rispettivamente 612749, 111534 e 12000 byte. Totale delle due passate: 877
operazioni di rimozione e 16983851 byte, oltre ai 32 backup gia rimossi.
Ogni esecuzione ha verificato l'hash invariato dell'agente e del backup mantenuto.
Un solo backup per PC verificato nella prima chiusura; nessuna modifica a
Projects, configurazioni, avvio o runtime. Gli elementi conservati includono
file in uso, scritture recenti e batch con ricevuta non terminale: non dichiarare
che tutte le cartelle script o log siano vuote.
