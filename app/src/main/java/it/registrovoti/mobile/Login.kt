package it.registrovoti.mobile

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

internal fun Modifier.livingBackground(): Modifier = composed {
    val palette = LocalGradient.current
    val accent = MaterialTheme.colorScheme.primary
    val motion = rememberInfiniteTransition(label = "Sfondo")
    val phase = motion.animateFloat(0f, 1f, infiniteRepeatable(
        tween(16000, easing = SmoothMotion), RepeatMode.Reverse), label = "Luce")
    background(Brush.horizontalGradient(palette)).drawBehind {
        val t = phase.value
        val radius = size.maxDimension * 0.74f
        val center = Offset(size.width * (0.10f + 0.8f * t), size.height * (0.32f + 0.42f * t))
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.20f), Color.Transparent),
            center = center, radius = radius), radius, center = center)
    }
}

@Composable
internal fun LoginScreen(modifier: Modifier, auth: FirebaseAuth, busy: Boolean, setBusy: (Boolean) -> Unit,
                         message: String, report: (String) -> Unit, google: () -> Unit, guest: () -> Unit) {
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val paper = Brush.horizontalGradient(listOf(colors.primary, lerp(colors.primary, colors.surface, 0.12f)))
    val ink = colors.onPrimary
    Column(modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().drawBehind {
            val w = size.width
            val h = size.height
            val wave = Path().apply {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                lineTo(w, h * 0.66f)
                cubicTo(w * 0.82f, h * 1.03f, w * 0.69f, h * 0.79f, w * 0.52f, h * 0.91f)
                cubicTo(w * 0.25f, h * 1.10f, w * 0.19f, h * 0.86f, 0f, h * 0.86f)
                close()
            }
            drawPath(wave, paper)
        }.padding(horizontal = 24.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.registro_mark), null, Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Text("Registro Voti", Modifier.weight(1f), color = ink, fontWeight = FontWeight.Bold)
                TextButton(modifier = Modifier.pressMotion(!busy), enabled = !busy, onClick = { register = !register; report("") }) {
                    Text(if (register) "Accedi" else "Registrati", color = ink)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(if (register) "Crea account" else "Accedi", Modifier.align(Alignment.CenterHorizontally),
                color = ink, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(44.dp))
        }
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                unfocusedBorderColor = Color.Transparent)
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), enabled = !busy,
                singleLine = true, shape = RoundedCornerShape(24.dp), colors = fieldColors, label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), enabled = !busy,
                singleLine = true, shape = RoundedCornerShape(24.dp), colors = fieldColors, label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            fun finish(error: Exception?, success: String = "") {
                setBusy(false)
                report(error?.localizedMessage ?: success)
            }
            if (!register) TextButton(modifier = Modifier.align(Alignment.End).pressMotion(!busy && email.isNotBlank()), enabled = !busy && email.isNotBlank(),
                onClick = {
                    setBusy(true)
                    auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener {
                        finish(it.exception, "Controlla la tua casella email")
                    }
                }) { Text("Recupera password") }
            OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).pressMotion(!busy && email.isNotBlank() && password.isNotEmpty()),
                border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(colors.primary, colors.outline))),
                enabled = !busy && email.isNotBlank() && password.isNotEmpty(), onClick = {
                    setBusy(true)
                    report("")
                    val task = if (register) auth.createUserWithEmailAndPassword(email.trim(), password)
                               else auth.signInWithEmailAndPassword(email.trim(), password)
                    task.addOnCompleteListener { finish(it.exception) }
                }) { Text(if (register) "Crea account" else "Accedi") }
            if (message.isNotBlank()) Text(message, color = colors.onBackground)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(Modifier.weight(1f))
                Text("oppure", style = MaterialTheme.typography.labelMedium, color = colors.onBackground)
                HorizontalDivider(Modifier.weight(1f))
            }
            FilledTonalButton(onClick = google, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressMotion(!busy)) {
                Text("Continua con Google")
            }
            TextButton(modifier = Modifier.pressMotion(!busy), onClick = guest, enabled = !busy) { Text("Continua come ospite") }
        }
    }
}
