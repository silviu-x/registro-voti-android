package it.registrovoti.mobile

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)

@Composable
internal fun SubjectLine(name: String, average: String, caption: String, open: () -> Unit) {
    Row(registerRowModifier(open).heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            if (caption.isNotEmpty()) Text(caption, style = MaterialTheme.typography.bodySmall)
        }
        Crossfade(average, animationSpec = androidx.compose.animation.core.tween(280, easing = SmoothMotion), label = "Media materia") { value ->
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun GradeLine(row: Map<*, *>, edit: () -> Unit, remove: (() -> Unit)? = null) {
    val raw = row["date"].toString()
    val day = remember(raw) { runCatching { LocalDate.parse(raw).format(dateFormat) }.getOrDefault(raw) }
    Row(registerRowModifier(edit).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) {
            Text((row["label"] ?: row["value"]).toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(row["subject"].toString(), fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull(day, row["type"]?.toString()).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            if (remove != null) row["note"]?.toString()?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        if (remove != null) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(androidx.compose.material.icons.Icons.Default.MoreVert, contentDescription = "Azioni voto")
                }
                DropdownMenu(expanded, { expanded = false }) {
                    DropdownMenuItem(text = { Text("Modifica") }, onClick = { expanded = false; edit() })
                    DropdownMenuItem(text = { Text("Elimina", color = MaterialTheme.colorScheme.error) }, onClick = { expanded = false; remove() })
                }
            }
        }
    }
}

@Composable
internal fun ValueLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun registerRowModifier(onClick: () -> Unit): Modifier {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    return Modifier.fillMaxWidth().pressMotion().clip(shape)
        .background(colors.surfaceVariant.copy(alpha = 0.55f))
        .border(1.dp, colors.primary.copy(alpha = 0.52f), shape)
        .clickable(role = Role.Button, onClick = onClick)
}

@Composable
internal fun RegisterAction(
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val accent = if (destructive) colors.error else colors.primary
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressMotion(enabled),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = if (enabled) 0.65f else 0.28f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (destructive) accent.copy(alpha = 0.09f) else colors.surfaceVariant,
            contentColor = accent
        ),
        content = content
    )
}

@Composable
internal fun ReportRow(period:String, average:String, estimate:String, heading:Boolean=false) {
    val style = if(heading) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(period,Modifier.weight(1.3f),style=style)
        Text(average,Modifier.weight(1f),style=style,textAlign=androidx.compose.ui.text.style.TextAlign.End)
        Text(estimate,Modifier.weight(1.2f),style=style,textAlign=androidx.compose.ui.text.style.TextAlign.End,
            fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
    }
}
