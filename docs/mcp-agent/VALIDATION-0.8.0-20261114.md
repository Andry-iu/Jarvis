# Validazione 0.8.0 — 14 novembre 2026

Implementazione gateway universali, collegamenti LAN autenticati, percorsi via,
aggiornamenti remoti con ricevute persistenti, nomi/configurazione/dismissione,
discovery limitata e installer 2.0.0. Specifica GATEWAY-0.8.0.md.

Checkpoint di sviluppo: suite Go e race/vet Linux passate durante lo sviluppo.
La verifica finale dopo tutti i cambiamenti, test/build Windows, collaudo
installer, pubblicazione e aggiornamento dei due desktop sono ancora da
concludere. Non usare questo checkpoint come prova di distribuzione.

Desktop1 e Desktop2 inizialmente 0.7.4, identita e code verificate. I due errori
interni iniziali Desktop2 sono successivamente scomparsi; causa non accertata.
Autorizzazione esplicita dell'utente a implementare, documentare, committare,
pubblicare e aggiornare automaticamente entrambi i desktop, piu installer
riutilizzabile. Nessuna nuova conferma necessaria per questi passaggi.

Desktop1: suite Windows Go completa 16.300s, vet e build PASS. Candidato nativo
13296128 byte, SHA256 5c64332616b08c0e74bd44503baaeafea609f23db8379487fcc6c0c651f3d46c.
Il primo collaudo installer si e fermato nel solo script di prova: variabile
PowerShell home riservata, prima della prima installazione. Rinominata la
variabile di test; nessun cambiamento Go o nuovo test Go necessario per questo
delta. Il collaudo installer va ripreso con il candidato gia verificato.
