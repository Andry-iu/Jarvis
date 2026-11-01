# JarvisWake Link — server della prova di stato

Sorgente canonico del servizio privato usato dalla 0.5.80; istruzioni complete in [COLLEGAMENTO_CHATGPT](../docs/COLLEGAMENTO_CHATGPT.md).

Worker ESM senza dipendenze: `node --test test/*.test.mjs`, `bash scripts/build.sh`, `node scripts/validate-artifact.mjs`. Il build deriva dal template Worker ESM Sites letto tramite skill. I helper di workflow/setup del plugin non sono montati nel filesystem cloud e le loro risorse non erano leggibili: usati template Worker ESM disponibile, Git con credenziale passata solo in memoria e operazioni native Sites. Nessun cambio di hosting o audience.

R2 binding BUCKET; segreto runtime SITE_DEVICE_ACCESS tramite Sites. Nessuna credenziale nel repository. Pubblicazione privata del1nov2026: Site appgprj_6ae75ae6cdd08191ab126c1d392a603a, sorgenti Site cf06d19d54768dc6fe6f9bf4af0b1d46fa30d9e2, deployment appgdep_6ae75d98fa48819194d0a52e59bb55cb succeeded/has_mcp=true/revisione ambiente1. Plugin provisionato plugin_asdk_app_sites_20b3e95cbcfc8191b4b5c0f04aee3679; installazione e disponibilità strumenti da verificare nella chat.

Nove test simulati verificano discovery limitata, identità e origine dell’abbinamento, giro richiesta/risposta, schema chiuso senza audio/testo chat, altro utente/token/azione rifiutati, scadenza, risposta duplicata, coda limitata e revoca. Non sono collaudi sul telefono né chiamate alla produzione con stati finti.
