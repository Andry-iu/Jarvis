# Menu e configurazione guidata — 0.5.83 / 105

Gruppo riepilogato e autorizzato dall’utente, con successive precisazioni nello stesso lavoro: pulsanti grandi, categorie, separazione uso/configurazione/servizio, wizard e schermo con comandi sopra/istruzioni sotto. Aggiunta durante l’implementazione, su richiesta esplicita, la guida completa dell’app e dei plugin, interrogabile dal plugin per rispondere alle domande dell’utente. Nessuna nuova azione del telefono o funzione calendario inclusa.

## Percorsi

| Funzione già presente | Percorso attuale |
| --- | --- |
| Avvio ciclo vocale, avvio/stop wake | Uso quotidiano |
| Appunti locali: elenco, nuovo, lettura, modifica | Uso quotidiano → Appunti locali |
| Accesso alla guida e ricerca per argomento/domanda | Uso quotidiano → Guida completa; anche Servizio |
| Microfono, notifiche, assistente, Accessibilità, overlay, batteria | Configurazione → Permessi e assistente; stessa pagina da Servizio |
| App nativa/WebView desktop, shortcut e nome progetto, prova senza Voice, accesso, URL, modello/livello | Configurazione → ChatGPT e progetto |
| Frase saluto e ripristino predefinita | Configurazione → Voce e saluto |
| Audio ON/OFF, file, volume/anteprima, tempo minimo | Configurazione → Audio di avvio |
| Opaco/semitrasparente e spiegazione gesti | Configurazione → Cover |
| Codice, collegamento attivo, cancellazione pairing | Configurazione → Collegamento e azioni → Abbinamento e stato; accesso anche da Servizio |
| Consenso azioni e permessi rubrica/chiamate/stato telefonico | Configurazione → Collegamento e azioni → Maps, chiamate e WhatsApp |
| Consenso remoto appunti e accesso alla memoria locale | Configurazione → Collegamento e azioni → Accesso agli appunti dal plugin |
| Export ZIP diagnostico | Servizio → Diagnostica |
| Cache e cancellazione dati WebView con conferma | Servizio → Manutenzione WebView |

Configurazione guidata attraversa le sei categorie di configurazione, poi il riepilogo. Indietro sul primo passo esce al menu chiamante; Termina dal riepilogo torna all’uso quotidiano. La guida può essere riaperta senza ripristinare valori. I selettori salvano la scelta; Avanti applica frase/URL validi della relativa pagina, senza inviare comandi Voice o azioni remote. Tornare indietro non annulla preferenze già applicate. I campi URL/frase non salvati e la posizione di navigazione vengono mantenuti nella ricreazione dell’attività.

## Schermata a due aree

SplitMenuScreen riutilizzato da menu principale, collegamento, appunti e guida. Comandi, slider, selettori e navigazione nella parte alta; istruzioni contestuali indipendentemente scorrevoli nella parte bassa. Proporzione normale65/35, con tastiera75/25; insets sistema/IME gestiti esplicitamente. Pulsanti almeno64dp, testo18sp, etichette su più righe; slider almeno56dp e switch/radio almeno64dp. Nessuna altezza fissa che tronchi il testo del pulsante. I pannelli Android e ChatGPT esterni mantengono la loro UI, controllata dalle rispettive app.

Focus di campo/controllo e variazioni dei selettori aggiornano la guida inferiore. Lasciare una pagina interrompe l’anteprima audio. Ritornare dalle schermate di sistema ricostruisce i controlli dalle preferenze; la scelta non viene applicata solo perché una pagina è stata aperta. La navigazione Indietro usa OnBackInvokedCallback su API33+ e fallback API31–32, con manifest esplicito; tastiera e testo grande richiedono comunque verifica sul Galaxy.

MainActivity conserva il preloader dietro la UI e non ricrea o rimuove il WebView a ogni cambio categoria. Il pannello modello/livello conserva il percorso di trasferimento dello stesso WebView. L’avvio wake previsto all’apertura e i proprietari di BridgeLink restano invariati. Il gruppo non modifica i servizi/algoritmi Voice, gesture, wake/audio, note o azioni .81/.82. La rimozione immediata .70 e la ripresa soltanto dopo rilascio restano requisiti critici.

## Guida unica e interrogazione via plugin

Sorgente canonica `docs/guida-jarvis.json`:19 argomenti, circa4.100 parole. `python3 scripts/sync-guide.py` produce asset offline, GUIDA_COMPLETA.md e blocco statico nel Worker. `--check` verifica uguaglianza degli output. Per modificare la guida aggiornare il JSON e rigenerare prima del rilascio, mantenendo versione dichiarata coerente.

`jarviswake_guide({topic,query})` è un tool di sola lettura sullo stesso plugin privato JarvisWake Link. topic indice/query vuota elenca le sezioni; topic cerca/query domanda restituisce fino a due sezioni pertinenti; un ID dall’indice legge la sezione intera. Non richiede device_id, pairing o telefono online e non accede all’archivio appunti o allo stato del telefono. Ricerca lessicale normalizzata per accenti e maiuscole: assenza di risultati dichiarata, non risposta generata dal server. Il modello deve rispondere usando i passaggi restituiti, con argomento/versione, e consultare gli strumenti reali se la domanda riguarda lo stato attuale.

Una chat deve avere il plugin disponibile per interrogarlo: il tool non crea disponibilità universale in ogni chat/modalità. In assenza di plugin fornire il file guida. Nell’app Esporta guida completa salva Markdown tramite selettore documento; il file contiene solo istruzioni statiche, nessun dato personale del telefono. La guida copre anche il calendario gestito dal suo plugin dedicato e i limiti delle connessioni Codex remoto, senza implementare nuove integrazioni o affermare che desktop non restituiti da una connessione non esistano.

Fonti primarie UI: https://developer.android.com/guide/topics/ui/accessibility/views/apps-views (touch target), https://developer.android.com/develop/ui/views/layout/sw-keyboard (IME/insets). Indicazioni del codice e test non sostituiscono un collaudo fisico di scorrimento, font e tastiera.
