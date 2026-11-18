package it.registrovoti.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val palettes = linkedMapOf(
    "Oceano" to Color(0xFF3378AE),
    "Bosco" to Color(0xFF298361),
    "Prugna" to Color(0xFF99549C),
    "Tramonto" to Color(0xFFC6663C)
)

private fun Color.tone(lightness: Float): Color =
    if (lightness >= 0f) lerp(this, Color.White, lightness)
    else lerp(this, Color.Black, -lightness)

private fun paletteColors(seed: Color, light: Boolean): androidx.compose.material3.ColorScheme {
    fun tone(day: Float, night: Float) = seed.tone(if (light) day else night)
    val surface = tone(.84f, -.49f)
    val ink = tone(-.76f, .95f)
    val accent = tone(-.38f, .65f)
    val container = tone(.75f, -.40f)
    val base = if (light) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = accent, onPrimary = if (light) Color.White else seed.tone(-.78f),
        primaryContainer = container, onPrimaryContainer = ink,
        secondary = accent, onSecondary = if (light) Color.White else seed.tone(-.78f),
        secondaryContainer = container, onSecondaryContainer = ink,
        tertiary = accent, onTertiary = if (light) Color.White else seed.tone(-.78f),
        tertiaryContainer = container, onTertiaryContainer = ink,
        background = tone(.85f, -.78f), onBackground = ink,
        surface = surface, onSurface = ink, surfaceTint = accent,
        surfaceVariant = tone(.70f, -.34f), onSurfaceVariant = tone(-.57f, .77f),
        outline = tone(-.15f, .32f), outlineVariant = tone(.55f, -.21f),
        surfaceBright = tone(.97f, -.38f), surfaceDim = tone(.73f, -.84f),
        surfaceContainerLowest = tone(.97f, -.82f), surfaceContainerLow = tone(.93f, -.70f),
        surfaceContainer = tone(.89f, -.62f), surfaceContainerHigh = tone(.85f, -.55f),
        surfaceContainerHighest = tone(.81f, -.48f),
        inverseSurface = ink, inverseOnSurface = surface, inversePrimary = tone(.65f, -.38f)
    )
}

val LocalGradient = compositionLocalOf { listOf(Color.Black, Color.DarkGray) }
private val LocalOptions=compositionLocalOf<Map<String,String>>{emptyMap()}
private val LocalSet=compositionLocalOf<(String,String)->Unit>{{_,_->}}
@Composable
fun RegistroTheme(content: @Composable () -> Unit) {
    val prefs=LocalContext.current.getSharedPreferences("appearance",0)
    var options by remember {mutableStateOf(mapOf("Tema" to (prefs.getString("Tema","Scuro") ?: "Scuro"),
        "Gradiente" to (prefs.getString("Gradiente","Oceano") ?: "Oceano"),
        "Testo" to (prefs.getString("Testo","Standard") ?: "Standard")))}
    var notice by remember {mutableStateOf(!prefs.getBoolean("notice",false))}
    val density=LocalDensity.current
    val scale=when(options["Testo"]){"Grande"->1.15f;"Molto grande"->1.3f;else->1f}
    val light = options["Tema"] == "Chiaro"
    val seed = palettes[options["Gradiente"]] ?: palettes.getValue("Oceano")
    val colors = remember(seed, light) { paletteColors(seed, light) }
    val gradient = remember(seed, light) {
        if (light) listOf(seed.tone(.91f), seed.tone(.64f))
        else listOf(seed.tone(-.72f), seed.tone(-.41f))
    }
    val activity = LocalContext.current as? android.app.Activity
    SideEffect {
        activity?.window?.let { window ->
            if (android.os.Build.VERSION.SDK_INT < 35) {
                @Suppress("DEPRECATION")
                window.statusBarColor = gradient.first().toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = colors.surface.toArgb()
            }
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = light
            controller.isAppearanceLightNavigationBars = light
        }
    }
    CompositionLocalProvider(LocalOptions provides options, LocalSet provides { key,value->
        prefs.edit().putString(key,value).apply();options=options+(key to value)
    },LocalDensity provides Density(density.density,density.fontScale*scale),
        LocalGradient provides gradient) {
        MaterialTheme(colorScheme = colors) {
            content()
            if (notice) PrivacyDialog {
                prefs.edit().putBoolean("notice", true).apply()
                notice = false
            }
        }
    }
}
@Composable
fun AppearanceControls(){
    val options=LocalOptions.current
    val update=LocalSet.current
    mapOf("Tema" to listOf("Scuro","Chiaro"),"Gradiente" to palettes.keys.toList(),"Testo" to listOf("Standard","Grande","Molto grande")).forEach{(key,values)->
        Choice("$key · ${options[key]}",values){update(key,it)}
    }
}
