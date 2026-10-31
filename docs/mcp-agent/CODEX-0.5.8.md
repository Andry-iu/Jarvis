# Codex 0.5.8 — collaudo in corso

Il 31 ottobre 2026 l'utente ha confermato l'aggiornamento degli strumenti del plugin e fornito la schermata autenticata Utilizzo e limiti: 88% quota settimanale residua, 0 crediti, ricarica automatica Disattivato. Questo e il nuovo riscontro che mancava al tentativo respinto nella 0.5.7. L'immagine e i dati dell'account non vanno pubblicati in Git.

La 0.5.8 conserva la guardia rigorosa originale sui dati API. Solo per balance=null puo utilizzare un riscontro locale distinto, in Config/codex-cost-proof.json, legato all'identita del computer e all'hash dell'account ChatGPT/piano. Deve dichiarare zero crediti e ricarica automatica disattivata, provenire dalla schermata confermata dall'utente e avere validita massima di sette giorni. Il file non e scrivibile tramite l'API file dei progetti e non viene accettato nei parametri degli strumenti Codex.

Restano obbligatori, a ogni turno: account ChatGPT Business verificato, hasCredits=false, unlimited=false, quota valida sotto il 90%, nessun blocco di spesa/limite. Un saldo positivo o flag credito ignoti non possono essere superati dal riscontro. Alla scadenza/cambio account la guardia torna chiusa. Il saldo API rimane visibile come nullo nel risultato, insieme alla provenienza/scadenza del riscontro: non viene falsificata la risposta del servizio.

Nessuna chiave API, acquisto, ricarica o modifica alle impostazioni di pagamento. Provider/tier e sandbox restano controllati. Test negativi per riscontro mancante/scaduto, account/PC errato, ricarica non disattivata, crediti positivi e quota sconosciuta. Collaudo isolato previsto per risposta reale, storico, steer, interrupt e ripresa dopo riavvio prima dell'aggiornamento dell'agente funzionante 0.5.7.
