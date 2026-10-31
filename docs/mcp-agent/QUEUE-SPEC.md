# Coda per progetto — specifica approvata, 31 ottobre 2026

Direzione approvata in Voice: le elaborazioni vengono accodate per progetto; consultazioni di sola lettura restano disponibili durante il lavoro. La previsione dell'assistente non sostituisce controlli lato agente, anche per richieste provenienti da altre chat.

## Requisiti
- Un'elaborazione attiva per progetto, ordine di arrivo predefinito. Risorse/progetti con percorsi sovrapposti non devono elaborare contemporaneamente.
- Elenco consultabile dalla chat con ID, progetto, stato, ordine e attivita attiva. Nessuna coda nascosta.
- Annullare un lavoro in attesa senza avviarlo; un lavoro attivo si interrompe con il comando esatto e verifica dello stato finale.
- Riordinare i lavori in attesa: sposta X prima di Y oppure metti X come prossima. Restituire l'ordine risultante. Non interrompere automaticamente il lavoro attivo.
- Non invertire dipendenze note fra attivita. L'assistente deve indicare le dipendenze prima dell'accodamento; non presumere di poterle dedurre dai testi.
- Controlli quota/costi immediatamente prima dell'avvio effettivo del turno, anche se la richiesta era in attesa.
- Pausa dopo errore/esito incerto. Conservare stato e ricevute; nessun replay automatico di lavori avviati con esito sconosciuto dopo riavvio.
- Stato, quote, release e file indipendenti si leggono durante le elaborazioni. Se il file e in scrittura, ottenere una lettura coerente o segnalare indisponibilita; non serializzare per questo l'intera elaborazione.
- Per le letture non promettere coerenza con processi esterni che modificano risorse fuori dal controllo dell'agente.

Nessun limite di un solo lavoro in attesa e stato approvato: era una proposta non adottata. Nessuna approvazione ad acquisti/API o a sviluppi di altri progetti.
Stato: specifica approvata; implementazione successiva alla 0.5.9 in corso, non ancora operativa.
