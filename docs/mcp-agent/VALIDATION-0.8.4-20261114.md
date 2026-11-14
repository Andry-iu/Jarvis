# Validazione 0.8.4 — stato in corso, 14 novembre 2026

Ambito: comando target-owned uniforme di aggiornamento da Git, coordinato dalla
chat a qualsiasi profondita; alternativa pacchetto conservata quando Git non e
raggiungibile. Versione agente/sorgenti/installer unificata 0.8.4.

Linux Go1.27.0: suite completa, race e vet PASS; cross-build Windows PASS.
Nuovi test: stesso comando diretto e su quattro livelli TLS, stato del target
leggibile attraverso ogni percorso, nessuna esecuzione sui gateway, deduplicazione
dopo risposta persa/ricostruzione, ID rebound rifiutato, concorrenza/busy/non-admin,
argomenti non fissati e completamento senza hash live rifiutati; quoting della
shell verificato. La suite preesistente mantiene l'aggiornamento a pacchetto.

Windows, parse PowerShell, preparazione tool su target senza Git/Go, installer,
rollback e aggiornamenti operativi: da completare prima di dichiarare il rilascio
installato. Nessun reboot fisico o refresh del catalogo client ancora certificato.
