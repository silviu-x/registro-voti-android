package it.registrovoti.mobile

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.credentials.*
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.libraries.identity.googleid.*
import kotlinx.coroutines.launch

@Composable
fun AccountControls(uid:String?,logout:()->Unit,report:(String)->Unit) {
    val context=LocalContext.current
    val prefs=context.getSharedPreferences("registro-native",0)
    var privacy by remember {mutableStateOf(false)}
    RegisterAction(onClick={privacy=true}){Text("Privacy e copyright")}
    if(privacy) PrivacyDialog {privacy=false}
    val user=FirebaseAuth.getInstance().currentUser
    if(uid==null || user==null || user.uid!=uid) return
    var confirm by remember {mutableStateOf(false)}
    var password by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val passwordAccount=user.providerData.any{it.providerId=="password"}
    fun erase(credential:AuthCredential) {
        user.reauthenticate(credential).continueWithTask { auth ->
            if(!auth.isSuccessful) throw auth.exception ?: IllegalStateException("Accesso fallito")
            FirebaseFirestore.getInstance().collection("users").document(uid).delete()
        }.addOnCompleteListener { document ->
            if(!document.isSuccessful){busy=false;report(document.exception?.localizedMessage ?: "Eliminazione fallita")}
            else user.delete().addOnCompleteListener {
                busy=false
                if(it.isSuccessful) logout() else report("Registro eliminato. Riprova per eliminare anche l’account.")
            }
        }
    }
    RegisterAction(destructive=true,enabled=!busy,onClick={confirm=true}){Text("Elimina account e dati")}
    if(confirm) AlertDialog(onDismissRequest={confirm=false;password=""},title={Text("Eliminare definitivamente account e voti?")},
        text={if(passwordAccount) OutlinedTextField(password,{password=it},shape=RoundedCornerShape(16.dp),label={Text("Password attuale")},visualTransformation=PasswordVisualTransformation()) else Text("Conferma nuovamente il tuo account Google.")},
        dismissButton={TextButton(onClick={confirm=false;password=""}){Text("Annulla")}},
        confirmButton={TextButton(enabled=!passwordAccount || password.isNotEmpty(),onClick={
            confirm=false;busy=true
            if(passwordAccount){erase(EmailAuthProvider.getCredential(user.email.orEmpty(),password));password=""}
            else scope.launch { try {
                val option=GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
                val req=GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result=CredentialManager.create(context).getCredential(context,req)
                erase(GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(result.credential.data).idToken,null))
            }catch(e:Exception){busy=false;report(e.localizedMessage ?: "Operazione interrotta")} }
        }){Text("Elimina definitivamente")}})
}

@Composable
fun PrivacyDialog(firstRead:Boolean=false,close:()->Unit) {
    val links=androidx.compose.ui.platform.LocalUriHandler.current
    var details by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest={if(!firstRead) close()},title={Text("Privacy e copyright")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("In modalità ospite il registro resta su questo dispositivo. Con un account, Firebase gestisce l’accesso e sincronizza voti e materie nel tuo documento privato.")
            Text("Puoi correggere i dati, scaricarne una copia o eliminare l’account dalle impostazioni. I file esportati e le copie su altri dispositivi vanno eliminati separatamente.")
            Text(androidx.compose.ui.res.stringResource(R.string.privacy_controller))
            Text("Se sei minorenne, puoi tenere il tuo registro come ospite. Per importare uno screenshot ritaglia solo i tuoi voti ed evita nomi o dati di altre persone.")
            TextButton(onClick={details=!details}) { Text(if(details) "▾ Conservazione, diritti e fornitori" else "▸ Conservazione, diritti e fornitori") }
            if(details) {
                Text("Il registro usa la memoria del dispositivo per i dati locali e le preferenze. I servizi di accesso e sincronizzazione ricevono dati tecnici di connessione, come l’indirizzo IP. Il registro non include pubblicità o strumenti di analisi del pubblico.")
                Text("Per accesso e sincronizzazione, la base giuridica indicata è l’esecuzione del servizio richiesto (art. 6, par. 1, lett. b GDPR). Firebase Authentication tratta identificativo ed email. Firestore conserva materie, voti, date, tipo di prova, note e impostazioni. I dati del registro restano nel cloud fino alla cancellazione.")
                Text("Gli screenshot sono letti sul dispositivo con Google ML Kit. Solo i voti confermati entrano nel registro. ML Kit può inviare a Google metriche tecniche di utilizzo e prestazioni.")
                Text("Puoi chiedere accesso, rettifica, cancellazione, limitazione e, quando applicabile, portabilità dei dati. Puoi presentare un reclamo al Garante.")
                Text("I fornitori possono trattare dati anche fuori dallo Spazio economico europeo. Consulta le informazioni di Google su localizzazione e garanzie per i trasferimenti.")
                TextButton(onClick={links.openUri("https://firebase.google.com/support/privacy")}) { Text("Privacy e trattamento dei dati in Firebase") }
                TextButton(onClick={links.openUri("https://developers.google.com/ml-kit/terms")}) { Text("Privacy di ML Kit") }
                TextButton(onClick={links.openUri("https://www.garanteprivacy.it/")}) { Text("Garante Privacy") }
            }
            Text("© Registro Voti. I contenuti inseriti restano dei rispettivi autori.")
        }
    },confirmButton={TextButton(onClick=close){Text("Ho letto")}})
}
