# Validazione0.8.2 — 14 novembre2026

Sorgente20a862a276baca7d7f95e3ab35aa33430563601a pubblicato e verificato su
feature/agent-gateway-20261114, Andry-iu/Jarvis. Versione unica0.8.2 per agente
e pacchetto. Difetto0.8.1 riprodotto con percorso Go reale: finestra creata con
IsWindowVisible=false, non sessione diversa o problema provato di Supremo.
Correzione minima: CREATE_NO_WINDOW senza HideWindow sul solo helper GUI.

## Prove

- Linux: suite e cross-build Windows PASS.
- Entrambi Windows Go1.27.0: suite completa con JARVIS_TEST_PAIRING_UI=1, vet e
  build PASS; Desktop1 18.774s, Desktop2 16.637s. Incluso test nativo opt-in che
  lancia ESATTAMENTE il comando Go di produzione, verifica finestra visibile e
  chiude il solo dummy con WM_CLOSE; nessuna autorizzazione sintetizzata.
- Entrambi: due installazioni isolate reali, task e identita/chiavi distinti,
  nessun Cloudflare richiesto nel locale, pairing compatibile, controllo e
  rifiuto sovrascrittura PASS; cleanup PASS.
- Entrambi: AGGIORNA da fixture reale0.8.1 ->0.8.2, nome/identita/credenziali/task
  conservati; unico updater con backup e rollback rehearsal PASS, cleanup PASS.
- Entrambi gateway operativi: aggiornamento sequenziale al candidato0.8.2,
  BACKUP_VERIFIED/ROLLBACK_REHEARSAL_PASS/PASS common_batch_update_identity_config_maintenance;
  riconnessione MCP/versione/identita/PID nuovi verificati. Hash e backup in CHECKSUMS.
- Prova ulteriore sugli agenti INSTALLATI di entrambi: richiesta firmata da
  istanza locale isolata, finestra helper osservata visibile tramite API Windows,
  dialogo dummy chiuso, stato denied, nessuna registrazione di trust. Config e
  strumenti di controllo non utilizzati prima del consenso.
- ZIP consegnato27 file: manifest/hash/dimensioni/integrita verificati. Candidato
  cross-build dallo stesso sorgente testato nativamente, hash distinti documentati.

Root e profili di avvio preesistenti conservati; nessun main/Android/force-push,
turno Codex, costo o cambio delle guardie economiche. Test su sessioni sbloccate,
nessun reboot/login fisico certificato. Il confronto codice e il consenso umano
su SoftPlcTest1 rimangono da eseguire dopo il suo aggiornamento locale0.8.2.

## Ripresa

SoftPlcTest1:192.168.1.101, identita e803423d08ab396b98262b3cf55df658,0.8.1 non
associato. SessioneJarvis6888 e PowerShell9580 entrambe1 nel primo riscontro;
nessuna finestra vista da utente via Supremo o monitor. Due richieste precedenti
scadute, nessun completamento o trust creato. Non riutilizzare codici scaduti.
Utente: estrarre Jarvis-Agent-Setup-0.8.2.zip e AGGIORNA.cmd stesso utente/admin
indicando la cartella esistente. Non serve reboot: viene riavviato l'agente.
Poi nuovo begin, codice mostrato, confronto e Consenti locale, status e complete
con SHA corrente e nuovo request_id, infine verifica innocua. Non automatizzare
l'approvazione sul target. Protocollo PAIRING-0.8.1.md valido dalla0.8.2.

## Diagnosi da conservare

Il semplice test PowerShell ProcessStartInfo mostrava la finestra anche con
WindowStyleHidden, quindi non riproduceva il difetto. La prova firmata contro
l'agente0.8.1 installato ha mostrato la finestra NON visibile; la medesima prova
contro0.8.2 la mostra visibile. Per modifiche GUI testare il launcher Go effettivo,
non limitarsi al parse PowerShell o a un launcher sostitutivo.
Log privati pair081-native-prompt-ready.log (prima), pair082-production-visibility.log
(dopo), pair082-{build-native,validation,deploy}.log e self-update.log sui PC.
I primi due harness diagnostici non inizializzavano Config/secrets.env o header
MCP richiesti: falliti senza richieste di trust e ripuliti; non sono prove della GUI.
