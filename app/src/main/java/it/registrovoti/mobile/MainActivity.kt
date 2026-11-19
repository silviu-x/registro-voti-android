package it.registrovoti.mobile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.sp
import kotlin.math.*
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.credentials.*
import com.google.android.libraries.identity.googleid.*
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RegistroTheme {
                Surface(Modifier.fillMaxSize()) { Session() }
            }
        }
    }

    @Composable
    private fun Session() {
        val auth = remember { FirebaseAuth.getInstance() }
        var user by remember { mutableStateOf(auth.currentUser) }
        var message by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        var guest by rememberSaveable { mutableStateOf(false) }
        DisposableEffect(auth) {
            val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
            auth.addAuthStateListener(listener)
            onDispose { auth.removeAuthStateListener(listener) }
        }
        val account = user
        val screen = Modifier.fillMaxSize()
            .livingBackground()
            .safeDrawingPadding()
            .imePadding()
        if (account == null && !guest) {
            EnterScreen { LoginScreen(screen, auth, busy, { busy = it }, message, { message = it },
                guest = { guest = true }, google = {
                    busy = true
                    message = ""
                    scope.launch {
                        try {
                            val option = GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id)).build()
                            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                            val result = CredentialManager.create(this@MainActivity).getCredential(this@MainActivity, request)
                            val token = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
                            auth.signInWithCredential(GoogleAuthProvider.getCredential(token,null)).addOnCompleteListener {
                                busy = false
                                if (!it.isSuccessful) message = it.exception?.localizedMessage ?: "Accesso fallito"
                            }
                        } catch (e: Exception) {
                            busy = false
                            message = e.localizedMessage ?: "Accesso interrotto"
                        }
                    }
                }) }
        } else Box(screen) {
            key(account?.uid) {
                EnterScreen { Register(account?.uid) { guest=false; auth.signOut() } }
            }
        }
    }
}

internal fun average(rows: List<Map<*,*>>) = GradeMath.average(rows,false)
internal fun display(n: Double) = if (n.isFinite()) String.format(Locale.ITALY,"%.2f",n) else "—"

internal fun saveGuestBook(editor: SharedPreferences.Editor, data: Data) {
    editor.putString("guest", org.json.JSONObject(data).toString())
    check(editor.commit()) { "Impossibile salvare il registro sul dispositivo." }
}

@Composable
private fun Register(uid: String?, logout: () -> Unit) {
    val ref = remember(uid) { uid?.let { FirebaseFirestore.getInstance().collection("users").document(it) } }
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember {context.getSharedPreferences("registro-native",0)}
    fun localBook():Data = prefs.getString("guest",null)?.let(::readBackup) ?: context.resources.openRawResource(R.raw.registro_demo).bufferedReader().use { readBackup(it.readText()) }
    var book by remember { mutableStateOf<Map<String,Any>?>(null) }
    var status by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var cached by remember { mutableStateOf(true) }
    DisposableEffect(ref) {
        if(ref==null) book=localBook()
        val listener = ref?.addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot,error ->
            if (error != null) { status = error.localizedMessage.orEmpty(); book = null }
            else if (snapshot != null) { cached=snapshot.metadata.isFromCache; book=snapshot.data ?: emptyBook() }
        }
        onDispose { listener?.remove() }
    }
    val data = book
    if (data == null) {
        var slow by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(1.seconds)
            slow = true
        }
        if (status.isNotEmpty()) Text(status)
        else if (slow) Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            repeat(3) { Spacer(Modifier.fillMaxWidth().height(58.dp).background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(16.dp))) }
            Text("Caricamento del registro…")
        }
        return
    }
    val commit:Commit = { change,done ->
        if(ref==null) {
            runCatching {
                val next=change(localBook())+("savedAt" to System.currentTimeMillis())
                saveGuestBook(prefs.edit(), next)
                book=next
            }.fold({done(null)},{done(it.localizedMessage ?: "Errore locale")})
        } else FirebaseFirestore.getInstance().runTransaction { tx ->
            val current=tx.get(ref).data ?: emptyBook()
            require((current["v"] as? Number)?.toInt()==1)
            tx.set(ref,change(current)+("savedAt" to System.currentTimeMillis()))
            true
        }.addOnSuccessListener{done(null)}.addOnFailureListener{done(it.localizedMessage ?: "Salvataggio fallito")}
    }
    val savedNotice = remember { SnackbarHostState() }
    val noticeScope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Map<*,*>?>(null) }
    var removing by remember { mutableStateOf<Map<*,*>?>(null) }
    var subjectFilter by rememberSaveable { mutableStateOf("Tutte") }
    var typeFilter by rememberSaveable { mutableStateOf("Tutte") }
    var detail by rememberSaveable { mutableStateOf("") }
    fun edit(row:Map<*,*>?) { selected=row; editing=true; status="" }
    removing?.let { row -> Confirm("Eliminare questo voto?",{removing=null}) {
        removing=null
        commit({ current ->
            require(row in grades(current)){"Il voto è cambiato. Riaprilo."}
            current+("grades" to grades(current).filterNot{it==row})
        }){status=it ?: "Voto eliminato"}
    } }
    val today=LocalDate.now()
    val year=today.year - if(today.monthValue<9) 1 else 0
    val cutoff=(data["settings"] as? Map<*,*>)?.get("cutoff")?.toString() ?: "${year+1}-01-31"
    var period by rememberSaveable { mutableStateOf("Anno") }
    val rows=grades(data).filter { when(period) {
        "1° quadr." -> it["date"].toString()<=cutoff
        "2° quadr." -> it["date"].toString()>cutoff
        else -> true
    } }
    val groups = rows.groupBy { it["subject"] }
    val mode = (data["settings"] as? Map<*,*>)?.get("mode")
    val general = GradeMath.average(rows,mode!="all")
    var tab by rememberSaveable { mutableStateOf("Panoramica") }
    androidx.activity.compose.BackHandler(enabled=tab!="Panoramica" && !editing){tab="Panoramica"}
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        AnimatedContent(targetState = tab, modifier = Modifier.fillMaxSize(), label = "Sezione",
            transitionSpec = { (fadeIn(tween(270, easing = SmoothMotion)) + slideInHorizontally(tween(320, easing = SmoothMotion)) { it / 19 }) togetherWith fadeOut(tween(190, easing = SmoothMotion)) }) { visibleTab ->
            LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) {
                if (status.isNotBlank()) item { Text(status) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (visibleTab == "Panoramica") "Registro Voti" else visibleTab,
                                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                if (visibleTab == "Panoramica") Text("Anno scolastico $year/${(year+1)%100}",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            if (visibleTab != "Impostazioni") BlueButton("+ Nuovo voto", { edit(null) }, active = true)
                        }
                        if (visibleTab == "Panoramica") Row(verticalAlignment = Alignment.CenterVertically) {
                            val online = uid != null && !cached
                            Spacer(Modifier.size(7.dp).background(if (online) Color(0xFF59CBAF) else Color.Gray, RoundedCornerShape(50)))
                            Spacer(Modifier.width(7.dp))
                            Text(if (uid == null) "Ospite · su questo dispositivo" else if (cached) "Dati in cache" else "Sincronizzato con il tuo account",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (visibleTab !in listOf("Impostazioni", "Pagella")) {
                            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(3.dp)) {
                                listOf("Anno","1° quadr.","2° quadr.").forEach { label ->
                                    val active = period == label
                                    Box(Modifier.weight(1f).background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(11.dp))
                                        .pressMotion().selectable(selected = active, onClick = { period = label }).heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                                        Text(label, style = MaterialTheme.typography.labelLarge,
                                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                if(visibleTab=="Panoramica" && data["demo"]==true) item { BluePanel {Text("Dati di esempio")
                    BlueButton("Inizia con i miei voti",{commit({it+mapOf("grades" to grades(it).filterNot{g->g["demo"]==true},"demo" to false)}){status=it.orEmpty()}})
                } }
                when (visibleTab) {
                    "Panoramica" -> {
                        item {
                            val lastDay=rows.maxOfOrNull{it["date"].toString()}?.let {runCatching{LocalDate.parse(it)}.getOrNull()} ?: today
                            val old=rows.filter { it["date"].toString()<=lastDay.minusDays(14).toString() }
                            val previous=if(mode=="all") average(old) else old.groupBy{it["subject"]}.values.map(::average).average()
                            BluePanel(centered = true) {
                                Text("MEDIA GENERALE · "+if(period=="Anno") "TUTTO L’ANNO" else period.uppercase(Locale.ITALY),fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
                                Gauge(general)
                                Text("${rows.size} voti · ${groups.size} materie")
                                if(previous.isFinite()) Text(String.format(Locale.ITALY,"%+.2f",general-previous)+" rispetto a due settimane fa",fontSize=14.sp)
                                BlueButton("＋ Aggiungi voto",{edit(null)},Modifier.fillMaxWidth(),true)
                            }
                        }
                        item { BluePanel {
                            Text("Andamento della media",Modifier.fillMaxWidth(),fontWeight=FontWeight.Bold)
                            val timeline=rows.groupBy{it["date"].toString()}.toSortedMap()
                            val history=mutableListOf<Map<*,*>>()
                            val points=timeline.values.map { day -> history.addAll(day)
                                if(mode=="all") average(history) else history.groupBy{it["subject"]}.values.map(::average).average() }
                            var selectedDay by remember(timeline.keys.toList()) {mutableIntStateOf(-1)}
                            if(points.isEmpty()) Text("Aggiungi il primo voto.")
                            else GradeChart(points,timeline.keys.toList(),selectedDay) { selectedDay=it }
                            if(selectedDay in points.indices) {
                                val date=timeline.keys.elementAt(selectedDay)
                                Text("$date · Media ${display(points[selectedDay])}")
                                Text(timeline.getValue(date).joinToString(" · "){"${it["subject"]} ${it["label"] ?: it["value"]}"})
                            }
                        } }
                        item { BluePanel {
                            Text("Ultimi voti inseriti",fontWeight=FontWeight.Bold)
                            rows.sortedByDescending{(it["ts"] as? Number)?.toLong() ?: 0}.take(4).forEach{row->
                                GradeLine(row, { edit(row) })
                            }
                        } }
                        item { BluePanel {
                            Text("Medie per materia",fontWeight=FontWeight.Bold)
                            subjects(data).sortedByDescending{average(groups[it].orEmpty()).takeIf(Double::isFinite) ?: -1.0}.forEach{name->
                                val subjectRows=grades(data).filter{it["subject"]==name}
                                val terms = if (period == "Anno") "1° quadr.  ${display(average(subjectRows.filter{it["date"].toString()<=cutoff}))}    ·    2° quadr.  ${display(average(subjectRows.filter{it["date"].toString()>cutoff}))}" else ""
                                SubjectLine(name, display(average(groups[name].orEmpty())), terms) { detail=name; tab="Materie" }
                            }
                        } }
                    }
                    "Voti" -> {

                        if (rows.isEmpty()) item { Text("Il primo voto apparirà qui.") }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { Choice("Materia · $subjectFilter", listOf("Tutte")+subjects(data)){subjectFilter=it} }
                            Box(Modifier.weight(1f)) { Choice("Prova · $typeFilter", listOf("Tutte")+types){typeFilter=it} }
                            }
                        }
                        items(rows.filter{(subjectFilter=="Tutte" || it["subject"]==subjectFilter) && (typeFilter=="Tutte" || it["type"]==typeFilter)}.sortedWith(compareByDescending<Map<*,*>> { it["date"].toString() }.thenByDescending { (it["ts"] as? Number)?.toLong() ?: 0L })) { row ->
                            GradeLine(row, { edit(row) }, { removing = row })
                        }
                    }
                    "Materie" -> {

                        items(subjects(data)) { name ->
                            val entries=groups[name].orEmpty()
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                SubjectLine(name, display(average(entries)), if(entries.size==1) "1 voto" else "${entries.size} voti") { detail=if(detail==name) "" else name }
                                AnimatedVisibility(detail==name){ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    SubjectDetails(entries)
                                    entries.sortedByDescending{it["date"].toString()}.forEach { row ->
                                        GradeLine(row, { edit(row) })
                                    }
                                } }
                            }
                        }
                    }
                    "Pagella" -> {
                        val all=grades(data)
                        val periods=listOf("1° quadr." to all.filter{it["date"].toString()<=cutoff},"2° quadr." to all.filter{it["date"].toString()>cutoff},"Anno" to all)
                        item { BluePanel {Text("Riepilogo generale",style=MaterialTheme.typography.titleLarge); HorizontalDivider(); periods.forEach{(label,entries)->
                            val n=if(mode=="all") average(entries) else entries.groupBy{it["subject"]}.values.map(::average).average()
                            ValueLine(label, display(n))
                        }} }
                        item { Text("Pagella per materia",style=MaterialTheme.typography.titleLarge) }
                        items(subjects(data)) { name -> BluePanel {
                            Text(name,fontWeight=FontWeight.Bold)
                            ReportRow("Periodo","Media","Voto stimato",true)
                            periods.forEach{(label,entries)->
                                val n=average(entries.filter{it["subject"]==name})
                                HorizontalDivider()
                                ReportRow(label,display(n),if(n.isFinite()) n.roundToInt().toString() else "—")
                            }
                        } }
                    }
                    "Impostazioni" -> {
                        item { BluePanel {
                            Text("Registro scolastico", style=MaterialTheme.typography.titleMedium)
                            SchoolSettings(data,cutoff,commit){status=it}
                        } }
                        item { BluePanel {
                            Text("Aspetto", style=MaterialTheme.typography.titleMedium)
                            AppearanceControls()
                        } }
                        item { BluePanel {
                            Text("Account", style=MaterialTheme.typography.titleMedium)
                            FirebaseAuth.getInstance().currentUser?.email?.let { Text(it) }
                            AccountControls(uid,logout){status=it}
                            BlueButton(if(uid==null) "Accedi" else "Esci dall’account",logout,Modifier.fillMaxWidth())
                        } }
                        item {
                            val version = remember(context) {
                                val pm = context.packageManager
                                val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                                } else {
                                    @Suppress("DEPRECATION")
                                    pm.getPackageInfo(context.packageName, 0)
                                }
                                info.versionName.orEmpty()
                            }
                            Text(
                                text = "-Developed by Silviu ;) - Version : $version -",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    "Simulatore" -> item {
                        var subject by rememberSaveable { mutableStateOf(subjects(data).firstOrNull().orEmpty()) }
                        var raw by rememberSaveable { mutableStateOf("") }
                        BluePanel { Target(rows,subjects(data),mode) }
                        Spacer(Modifier.height(20.dp))
                        BluePanel {
                        Text("E se prendo…",style=MaterialTheme.typography.titleLarge)
                        Choice(subject.ifEmpty{"Scegli materia"},subjects(data)){subject=it}
                        OutlinedTextField(raw,{raw=it},shape=RoundedCornerShape(16.dp),singleLine=true,label={Text("Voto ipotetico")},modifier=Modifier.fillMaxWidth())
                        val score=parseGrade(raw)
                        AnimatedVisibility(score!=null && subject.isNotEmpty()) {
                            val hypothetical=rows+listOf(mapOf("subject" to subject,"value" to (score?.first ?: 0.0)))
                            val simulated=hypothetical.groupBy{it["subject"]}
                            Column(Modifier.padding(vertical=16.dp)) {
                                Text("Media di $subject")
                                Text(display(average(simulated[subject].orEmpty())),style=MaterialTheme.typography.headlineLarge)
                                val future=GradeMath.average(hypothetical,mode!="all")
                                val current=average(groups[subject].orEmpty())
                                Text("Prima: ${display(current)} · Differenza: ${display(average(simulated[subject].orEmpty())-current)}")
                                Text("Media generale: ${display(general)} → ${display(future)} (Δ ${display(future-general)})")
                            }
                        }
                        if(raw.isNotEmpty() && score==null) Text("Inserisci un voto da 2 a 10.")
                        }
                    }
                }
            }
        }
        SnackbarHost(savedNotice, Modifier.align(Alignment.BottomCenter))
        }
        val destinations = listOf("Panoramica" to Icons.Outlined.Home, "Materie" to Icons.AutoMirrored.Outlined.List,
            "Voti" to Icons.Outlined.CheckCircle, "Pagella" to Icons.Outlined.DateRange,
            "Simulatore" to Icons.Outlined.AddCircle, "Impostazioni" to Icons.Outlined.Settings)
        val minimumTabWidth = 62.dp * LocalDensity.current.fontScale
        val barSurface = MaterialTheme.colorScheme.surface
        LazyRow(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Brush.verticalGradient(listOf(barSurface.copy(alpha = 0.12f), barSurface.copy(alpha = 0.65f))))
                .padding(vertical = 4.dp)
        ) {
            items(destinations) { (name, icon) ->
                val selected = name == tab
                Column(Modifier.widthIn(min = minimumTabWidth).fillParentMaxWidth(1f / destinations.size).pressMotion().selectable(selected = selected, onClick = { tab = name }).heightIn(min = 54.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(42.dp, 26.dp).background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(name, fontSize = 10.sp, letterSpacing = 0.sp, maxLines = 1)
                }
            }
        }
    }
    if(editing) {
        var saving by remember { mutableStateOf(false) }
        var another by remember { mutableStateOf(false) }
        var formKey by remember {mutableIntStateOf(0)}
        androidx.compose.ui.window.Dialog(onDismissRequest={if(!saving) editing=false}) {
            Surface(shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(if(selected==null) "Nuovo voto" else "Modifica voto",style=MaterialTheme.typography.titleLarge)
                    AnimatedVisibility(status.isNotEmpty()) { Text(status) }
                    if(selected==null) Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=another,onCheckedChange={another=it});Text("Inserisci un altro voto dopo il salvataggio")}
                    key(formKey){ EnterScreen { GradeForm(subjects(data),selected,onCancel={editing=false},onSave={name,score,date,type,note,done ->
                        saving=true; status=""
                        commit(changeGrade(selected,name,score,date,type,note)){ error ->
                            saving=false;done()
                            if (error == null) {
                                editing = another
                                formKey++
                                if (another) status = "Voto salvato"
                                else noticeScope.launch {
                                    savedNotice.currentSnackbarData?.dismiss()
                                    savedNotice.showSnackbar("Voto salvato")
                                }
                            } else status = error
                        }
                    }) } }
                }
            }
        }
    }
}

@Composable
private fun GradeForm(names: List<String>, initial:Map<*,*>?, onCancel: ()->Unit,
    onSave: (String,Pair<Double,String>,LocalDate,String,String,()->Unit)->Unit) {
    var name by remember { mutableStateOf(initial?.get("subject")?.toString() ?: names.firstOrNull().orEmpty()) }
    var raw by remember { mutableStateOf(initial?.get("label")?.toString() ?: initial?.get("value")?.toString().orEmpty()) }
    var date by remember { mutableStateOf(initial?.get("date")?.toString() ?: LocalDate.now().toString()) }
    var type by remember { mutableStateOf(initial?.get("type")?.toString() ?: types.first()) }
    var note by remember { mutableStateOf(initial?.get("note")?.toString().orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Choice("Scegli materia",names) { name=it }
        OutlinedTextField(name,{name=it},shape=RoundedCornerShape(16.dp),label={Text("Materia esistente o nuova")},singleLine=true)
        OutlinedTextField(raw,{raw=it},shape=RoundedCornerShape(16.dp),label={Text("Voto, es. 7+ o 6/7")},singleLine=true)
        OutlinedTextField(date,{date=it},shape=RoundedCornerShape(16.dp),label={Text("Data AAAA-MM-GG")},singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){listOf("−","+","½").forEach{suffix->
            TextButton(modifier=Modifier.pressMotion(),onClick={val n=raw.takeWhile(Char::isDigit);if(n.isNotEmpty()) raw=if(raw==n+suffix)n else n+suffix}){Text(suffix)}
        }}
        Choice(type,types) { type=it }
        OutlinedTextField(note,{note=it},shape=RoundedCornerShape(16.dp),label={Text("Nota")})
        if (error.isNotBlank()) Text(error)
        Row {
            Button(modifier=Modifier.pressMotion(!busy),enabled=!busy,onClick={
                val score=parseGrade(raw)
                val day=runCatching { LocalDate.parse(date) }.getOrNull()
                if (name.isBlank() || score==null || day==null) error="Controlla materia, voto (2–10) e data."
                else { error=""; busy=true; onSave(name.trim(),score,day,type,note) { busy=false } }
            }) { Text(if (busy) "Salvataggio…" else "Salva voto") }
            TextButton(modifier=Modifier.pressMotion(!busy),enabled=!busy,onClick=onCancel) { Text("Annulla") }
        }
    }
}

@Composable
internal fun Choice(label: String, options: List<String>, select: (String)->Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RegisterAction(onClick={open=true}) {
            Text(label, Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded=open,onDismissRequest={open=false}) {
            options.forEach { value -> DropdownMenuItem(text={Text(value)},onClick={select(value); open=false}) }
        }
    }
}

@Composable
private fun BlueButton(label:String, action:()->Unit, modifier:Modifier=Modifier, active:Boolean=false) {
    Button(onClick=action,modifier=modifier.pressMotion(),shape=RoundedCornerShape(10.dp),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),
        colors=ButtonDefaults.buttonColors(containerColor=if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,contentColor=if(active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant),
        contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) { Text(label) }
}

@Composable
internal fun BluePanel(centered:Boolean=false, content:@Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp),horizontalAlignment=if(centered) Alignment.CenterHorizontally else Alignment.Start,verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}

@Composable
private fun Gauge(score:Double) {
    val target = if (score.isFinite()) score.toFloat() else 0f
    val shown by animateFloatAsState(target, tween(620, easing = SmoothMotion), label = "Media generale")
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().aspectRatio(2f),contentAlignment=Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val radius=size.width*.395f
            val origin=Offset(size.width/2,size.height*.96f)
            repeat(10) { index ->
                val angle=185f+index*17f
                val rad=Math.toRadians(angle.toDouble())
                val center=Offset(origin.x+cos(rad).toFloat()*radius,origin.y+sin(rad).toFloat()*radius)
                val w=size.width*.129f
                val h=size.width*.076f
                rotate(angle,center) {
                    val fill = (shown - index).coerceIn(0f, 1f)
                    drawRoundRect(androidx.compose.ui.graphics.lerp(colors.surfaceVariant, colors.primary, fill),
                        Offset(center.x-w/2,center.y-h/2),Size(w,h),CornerRadius(h*.24f))
                }
            }
        }
        Text(display(if (score.isFinite() && shown != target) shown.toDouble() else score),fontSize=42.sp,fontWeight=FontWeight.Bold,lineHeight=46.sp)
    }
}
