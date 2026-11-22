# Agente Windows Jarvis — riferimento in main

A partire dall'integrazione del 22 novembre 2026, il codice completo dell'agente Windows e dell'installer deve essere mantenuto in `main/windows-mcp-agent/`. I rami precedenti `feature/unified-agent-source-update-20261114` e `fix/ui-mouse-move-20261122` restano riferimenti storici e non devono piu essere usati come base di nuovi sviluppi.

**Versioni:** ultimo agente Windows verificato dal vivo su Desktop1: 0.8.6 (commit esatto `f8bec6ee37c0c4bc15315815f3768e8a7d3ba3b2`). Integrazione sorgente unificata 0.8.7: candidato, da collaudare con i test e poi creare installer/APK ufficiali. Il connettore ChatGPT di Desktop1 non espone ancora `ui_mouse`; questa e una verifica distinta dal binario.

Documento canonico: [CURRENT-RELEASE.md](CURRENT-RELEASE.md), [UNIFIED-RELEASE-0.8.7-20261122.md](UNIFIED-RELEASE-0.8.7-20261122.md). Per gli stati correnti leggere commit e ricevute, non solo questa nota.
