# Finestra di abbinamento invisibile — 0.8.2

Utente: SoftPlcTest1 aggiornato0.8.1, richieste ricevute ma nessuna finestra sul
monitor fisico o Supremo. Jarvis e PowerShell entrambi SessionId1.
Riproduzione tramite richiesta firmata reale tra istanza isolata e agente0.8.1
operativo su Desktop1: il processo PowerShell crea la finestra Jarvis ma
IsWindowVisible=false. Nessuna autorizzazione o registrazione di trust effettuata.
Le prove precedenti via ProcessStartInfo non riproducevano il comportamento Go.

Causa: helper GUI avviato con configureDesktopCommand, che imposta HideWindow
(SW_HIDE/STARTF_USESHOWWINDOW). La prima ShowDialog WinForms rimane invisibile.
Correzione minima: mantenere CREATE_NO_WINDOW per la console, senza HideWindow
sul solo helper di abbinamento. Gli altri worker restano invariati.

Regressione: TestPairingPromptHasNoConsoleButDoesNotHideGUI e opt-in Windows
JARVIS_TEST_PAIRING_UI=1/TestPairingPromptNativeVisible. La seconda avvia lo stesso
comando Go di produzione, osserva il PID/handle/titolo e IsWindowVisible, poi
chiude esclusivamente il dialogo dummy con WM_CLOSE. Nessuna conferma/trust
sintetizzata. Verifiche/distribuzione0.8.2 ancora in corso.

Unica release sorgenti/agente/installer0.8.2. AGGIORNA accetta0.8.0 e0.8.1.
Per SoftPlcTest1 non ancora associato serve aggiornamento locale dal nuovo ZIP.
Nessun riavvio del PC richiesto dalla correzione; updater riavvia l'agente.
