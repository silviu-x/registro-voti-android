# Registro Voti Android 2.1.6

Interfaccia nativa in Kotlin e Jetpack Compose. Apri la cartella contenente settings.gradle.kts in Android Studio. Per aggiornare il progetto Windows già funzionante, chiudi il progetto e unisci il contenuto di questo archivio alla sua cartella android. Conserva il tuo file app/google-services.json e il keystore che hai usato per installare l’app. L’archivio include il wrapper di Gradle 8.9. I sorgenti web della versione precedente sono esclusi dalla build.

Per una nuova installazione, registra il pacchetto `it.registrovoti.mobile` nel tuo progetto Firebase e copia il file di configurazione scaricato in `app/google-services.json` prima di compilare.

Per aggiornare una tua installazione, utilizza lo stesso progetto Firebase e la stessa firma di prima. La build usa Gradle 8.9, JDK 17 e SDK 35. Esegui :app:assembleDebug con Run Anything, oppure dalla radice del progetto in PowerShell esegui .\gradlew.bat :app:assembleDebug. L’APK di prova si trova in app/build/outputs/apk/debug/app-debug.apk.

## Dati già presenti
Prima di passare alla versione nativa sincronizza l'account o esporta un backup JSON dall'app precedente, soprattutto se usavi il registro ospite. La cache della WebView e l'archivio ospite nativo sono separati. Accedi allo stesso account per recuperare i dati cloud, oppure importa il JSON dalle impostazioni. L'account usa transazioni Firestore e richiede una connessione per confermare le modifiche. Il registro ospite funziona in locale.

## Verifica prima della distribuzione privata
Con un account di prova controlla login, inserimento, modifica e cancellazione sul telefono e verifica il risultato sul sito. Esporta un backup e importalo nel registro ospite. Prova i filtri e il simulatore su dati conosciuti. Per la release privata usa la tua firma release, registra la relativa SHA-1 in Firebase e conserva il keystore per gli aggiornamenti.
