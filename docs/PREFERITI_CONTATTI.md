# Preferiti e cache contatti — .89 / 111

Gruppo esplicitamente autorizzato: distinguere Preferiti dalla cache dei frequenti; aggiungere da qualsiasi numero rubrica o impostando nome e numero direttamente; rimuovere voci senza confondere i due archivi. Nessuna modifica ai flussi cover/PiP/stop/audio/wake/polling/WhatsApp della .88.

## Uso

Configurazione → Collegamento e azioni → Maps, chiamate e WhatsApp → **Preferiti e cache contatti**:

- **Preferiti → Aggiungi dalla rubrica:** apre ACTION_PICK su ContactsContract.CommonDataKinds.Phone.CONTENT_URI. Scegli una persona e uno dei suoi numeri, anche se non era preferito Android. URI content/authority ufficiale, riga e numero vengono revalidati prima del salvataggio. Il permesso Contatti è richiesto se mancante; annullare la scelta non salva. Nessuna scrittura nella rubrica.
- **Preferiti → Nome e numero manuali:** due campi e Salva preferito. Il nome manuale può avere1–80 caratteri, il numero5–15 cifre con eventuale + iniziale; spazi/trattini/parentesi di formattazione normalizzati. Codici *, #, pause/interni, lettere e + interno sono rifiutati. Non viene creato un contatto Android. Errori di validazione non svuotano i campi. Identico nome/numero manuale riutilizza lo stesso ID invece di duplicarlo.
- Tocca un preferito per vedere il numero e **Rimuovi preferito**, con conferma su quella revisione. **Importa preferiti rubrica** resta facoltativo (massimo20 per importazione), non è il solo ingresso. Fino a100 preferiti, senza rotazione automatica; raggiunto il limite occorre rimuoverne uno prima di aggiungere.
- **Cache frequenti:** separata, fino a20 voci automatiche. Ogni riquadro permette Rimuovi dalla cache con conferma; Svuota cache confermato lascia intatti Preferiti/rubrica/appunti. Una nuova telefonata può rigenerare la voce. Il conteggio riguarda dispatch, non conversazioni risposte. Rimuovere un preferito non cancella la sua cronologia in cache, ma il numero non viene richiamato da un vecchio riferimento.

Restano riquadri due per riga, help solo ? del pulsante e Back largo ultimo nello scorrimento. Back moderno con OnBackInvokedCallback su33+, override legacy soltanto31/32. Nessuna modifica alle gesture cover. Picker Samsung e flusso reale chiamata restano da provare sul Galaxy.

## Archivi e aggiornamento .88

Preferiti: `contact_favorites`/`items`, cifrato AES-GCM con Android Keystore; namespace distinto da `bridge_private` della cache. La chiave di cifratura preesistente è riutilizzata, non eliminata cancellando il pairing. Preferiti rimangono quindi dopo svuotamento/rimozione cache o cancellazione del solo abbinamento; cancellazione dati/disinstallazione li elimina. Esclusi da backup/diagnostica/Git, nessun numero reale nel repository. Scrittura commit e rilettura prima di conferma; errori non autorizzano ricostruzione archivio.

Alla prima lettura/salvataggio si copiano gli elementi .88 con favorite=true in Preferiti come voci collegate alla rubrica, ID stabile della riga. Prima si salva e rilegge il nuovo archivio; solo dopo si toglie il vecchio flag (o il vecchio record importato senza utilizzi). Se il salvataggio fallisce, il vecchio preferito rimane; ripresa idempotente senza duplicati. Le voci con utilizzi conservano la cronologia automatica in cache. La cache non può espellere i Preferiti.

## Risoluzione dal plugin

Stessi tool, UUID/lease/claim/journal e consensi. Ricerca solo del nome esplicitamente richiesto: preferiti pertinenti e rubrica accessibile. Nome/numero manuale è configurazione locale dell’utente, non un numero remoto arbitrario. Risultato pubblico invariato: nome/etichetta/ultime4cifre e contact_ref temporaneo, mai numero completo o elenco dei Preferiti.

Il riferimento interno include favorite_id e revisione. Manuale: prima di usare, confronta il record attuale/nome/numero/revisione; rimozione/cambio invalida il riferimento anche se resta una copia storica in cache. Voce rubrica: controlla anche riga/nome/numero attuali; sincronizzazione che sostituisce ID o cambia dati richiede nuova selezione/importazione, nessuna ricerca automatica di una persona sostitutiva. Stessa coppia nome/numero nei risultati deduplicata, nomi uguali con numeri diversi restano una scelta obbligatoria. Senza accesso rubrica si possono risolvere manuali locali pertinenti; non si dichiara letta la rubrica non autorizzata e una voce collegata richiede Contatti.

Chiamata per nome o riferimento resta una sola, con sblocco, consensi e permessi telefonici, singolo stop Voice, audio libero e controlli immediatamente prima del dialer. Nessun telefono reale chiamato dagli strumenti del cloud; test usano dati sintetici. Il motore WhatsApp e la selezione progetto non sono modificati; il resolver comune può fornire un riferimento a un preferito anche agli altri comandi già autorizzati, con le rispettive verifiche.

Fonti primarie: [Phone.CONTENT_URI e colonne](https://developer.android.com/reference/android/provider/ContactsContract.CommonDataKinds.Phone), [Contacts Provider e ID che possono cambiare](https://developer.android.com/identity/providers/contacts-provider). Gli ID non sono promessi eterni: incertezza/cambio interrompe l’uso, non aggiorna silenziosamente il destinatario.
