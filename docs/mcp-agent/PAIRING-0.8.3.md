# Abbinamento con inserimento obbligatorio — 0.8.3

## Procedura utente

Installare l'agente, assegnare un nome e chiedere in chat «Collega <nome> tramite
Desktop1». Partecipano il gateway scelto e il nuovo PC; gli altri gateway non
intervengono. Entrambi calcolano lo stesso codice dai dati della richiesta.
Il gateway lo restituisce al client/chat. Il target non mostra il valore atteso:
mostra richiedente, indirizzo, identita, campo vuoto e conto alla rovescia.
Digitare o incollare il codice ricevuto in chat e premere Consenti.
Il pulsante resta disabilitato senza 12 cifre esadecimali; il contenuto deve
corrispondere per autorizzare. Maiuscole/minuscole, trattini e spazi ASCII sono
accettati. Unicode simile, caratteri nascosti, input parziale o altri caratteri
sono rifiutati. Un codice formalmente valido ma diverso mostra errore e pulisce
il campo; tre errori chiudono la richiesta senza approvare.

Il tempo disponibile e 110 secondi, conto alla rovescia da 1:50, rosso negli
ultimi 20 secondi. La verifica del click e dell'output controlla la scadenza anche
se il timer grafico fosse in ritardo. Chiusura, Rifiuta, blocco schermo, timeout
o errore non autorizzano. Dopo la scadenza occorre una nuova richiesta, con nuovo
request_id/chiave/codice, non il riuso della precedente.

Dopo l'approvazione il gateway completa l'importazione CAS e verifica identita,
certificato e controllo autenticato. Nessun trasferimento manuale di credenziali.
I collegamenti gia salvati restano validi e non richiedono nuovo consenso.

## Implementazione e limiti

La richiesta iniziale usa il listener LAN di abbinamento: comunicare per chiedere
consenso non concede strumenti di controllo. Codice di 12 cifre esadecimali
ricavato dalla stessa impronta del transcript su entrambi i lati, non cablato
nell'installer. L'helper GUI conserva il valore atteso solo in memoria; restituisce
il codice effettivamente digitato, mai un semplice marcatore ALLOW. Il launcher
Go normalizza e verifica nuovamente la corrispondenza e il contesto non scaduto.
Il server conserva il gate di consenso/scadenza prima di sigillare la credenziale.

Questa modifica obbliga a trasferire attivamente il codice e impedisce il semplice
clic distratto. Il codice e derivato da dati pubblici del protocollo: non va
presentato come password segreta, secondo fattore o garanzia contro un utente
malevolo con accesso locale. Restano TLS con pin, firma RSA-PSS, RSA-OAEP, limiti
LAN e rate limit della 0.8.1. Nessun endpoint di approvazione remota introdotto.
Non inserire il codice o premere Consenti per conto dell'utente.

## Compatibilita e client

Il protocollo begin/status/complete e il campo comparison_code restano invariati.
Mostrare il codice ricevuto e attendere approvazione locale; non richiedere che
l'utente confermi a voce una corrispondenza che ora viene verificata dal programma.
Usare lo stesso request_id per status; complete solo su approved e con SHA CAS
corrente. In caso di esito incerto leggere stato/ricevuta prima di ripetere.

Serve aggiornare il destinatario alla 0.8.3: aggiornare solo Desktop1 non cambia
la finestra di un nuovo PC ancora alla 0.8.2. I vecchi target conservano il
confronto a vista fino al loro aggiornamento; nessuna garanzia nuova per quei PC.
AGGIORNA.cmd accetta 0.8.0, 0.8.1 e 0.8.2 tramite lo stesso updater canonico,
preservando identita, configurazioni, credenziali e avvio. Versione unica 0.8.3
anche per il pacchetto Jarvis-Agent-Setup-0.8.3.zip. Stato effettivo e limiti del
collaudo in VALIDATION-0.8.3-20261114.md.
