# Registro Voti per Android

**Che voto mi serve nella prossima verifica per arrivare alla media del 7?** Registro Voti è un’app per studenti che raccoglie i voti per materia, mostra come cambia la media durante il quadrimestre e ti aiuta a fissare un obiettivo per le prossime prove.

## Cosa puoi fare

- **Segui una materia nel tempo.** Inserisci, modifica o elimina i voti. Nel grafico puoi selezionare una prova per leggere la data, il voto e la media che avevi in quel momento.
- **Calcola che voto ti serve.** Scegli una materia e la media che vuoi raggiungere. Il simulatore calcola il voto necessario e mostra come cambia il risultato se hai ancora più prove a disposizione.
- **Organizza il registro.** Aggiungi le tue materie e imposta la data di fine del primo quadrimestre per consultare i voti del periodo che ti interessa.
- **Scegli dove salvare i dati.** Il registro ospite funziona in locale. Con lo stesso account Firebase puoi ritrovare i voti anche sul sito. Per confermare le modifiche dell’account serve una connessione. Dalle impostazioni puoi esportare e importare un backup JSON.

## Installazione

Serve **Android 8.0 o successivo**. Apri la sezione **Releases** del repository, scegli la versione desiderata e scarica il file APK dagli **Assets**. Per la versione 2.1.6 il file si chiama `registro-2.1.6.apk`. Aprilo sul telefono e segui le istruzioni di Android.

**Se aggiorni dalla versione con WebView**, sincronizza i voti oppure esporta un backup JSON prima di installare la 2.1.6. Il nuovo registro ospite usa un archivio locale separato. Dopo l’aggiornamento accedi allo stesso account per recuperare i voti sincronizzati oppure importa il backup dalle impostazioni.

## Compilare dai sorgenti

L’interfaccia Android è scritta in **Kotlin** con **Jetpack Compose**. Il progetto usa Firebase Authentication per l’accesso e Cloud Firestore per i dati sincronizzati.

1. Apri in Android Studio la cartella che contiene `settings.gradle.kts`. Usa JDK 17 e installa Android SDK 35.
2. Configura in Firebase l’app Android con identificativo `it.registrovoti.mobile` e inserisci il relativo `google-services.json` nella cartella `app/`. Per utilizzare l’accesso Google, registra anche l’impronta SHA-1 della firma usata.
3. Dalla radice del progetto esegui il comando di build. Su Windows, in PowerShell, usa

   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```

   Su macOS o Linux usa `bash gradlew :app:assembleDebug`.

L’APK di prova viene creato in `app/build/outputs/apk/debug/app-debug.apk`.
