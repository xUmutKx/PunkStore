package com.punkstore

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

class Pal(val bg: Color, val panel: Color, val panel2: Color, val card: Color, val blue: Color, val text: Color, val dim: Color,
          val greenA: Color, val greenB: Color, val top: Color, val topEnd: Color, val row: Color, val btn: Color, val link: Color, val hero: List<Color>, val corner: Int,
          val pGrad: List<Color> = emptyList(), val edgeHi: Color = Color.Transparent, val edgeLo: Color = Color.Transparent,
          val glossy: Boolean = false, val carbon: Boolean = false, val topAccent: Color? = null,
          val detailBg: Color? = null, val box: Color? = null, val buy: List<Color>? = null, val wish: Color? = null)

/** Modern Steam mobil (kullanıcının ekran görüntülerinden örneklenen gerçek renkler): üst/alt çubuk #1F2127, sayfa/panel #2A2C34, arama #333842, mavi #1A9FFF, mağaza sayfası #121A24→#172330, kutu #1B2838, satın alma #3B4650 */
val PAL_MODERN = Pal(Color(0xFF1F2127), Color(0xFF2A2C34), Color(0xFF333842), Color(0xFF2A2C34), Color(0xFF66C0F4), Color(0xFFC7D5E0), Color(0xFF8F98A0),
    Color(0xFF75B022), Color(0xFF588A1B), Color(0xFF1F2127), Color(0xFF1F2127), Color(0xFF2A2C34), Color(0xFF1A9FFF), Color(0xFF67C1F5), listOf(Color(0xFF3B1E5E), Color(0xFFB8453A), Color(0xFFF2A33A)), 2,
    pGrad = listOf(Color(0xFF2A2C34), Color(0xFF2A2C34)), edgeHi = Color(0xFF343741), edgeLo = Color(0xFF1F2127), glossy = false, topAccent = null,
    detailBg = Color(0xFF131C27), box = Color(0xFF1B2838), buy = listOf(Color(0xFF3B4650), Color(0xFF3B4650)), wish = Color(0xFF233C4E))
/** 2013–2015: karbon siyahı/koyu gri istemci, yeşil vurgular (Steam'in o dönem yeşil "Yükle" düğmeleri) */
val PAL_2013 = Pal(Color(0xFF1B1B1B), Color(0xFF232323), Color(0xFF333333), Color(0xFF1F1F1F), Color(0xFFA4D007), Color(0xFFB8B6B4), Color(0xFF7C7C7C),
    Color(0xFF7DAE1E), Color(0xFF4A6B0C), Color(0xFF3A3A3A), Color(0xFF151515), Color(0xFF2A2A2A), Color(0xFF5C7E10), Color(0xFFA4D007), listOf(Color(0xFF0B0B0B), Color(0xFF2B3D0A), Color(0xFF7DAE1E)), 3,
    pGrad = listOf(Color(0xFF383838), Color(0xFF1E1E1E)), edgeHi = Color(0xFF505050), edgeLo = Color(0xFF080808), glossy = true, carbon = true, topAccent = Color(0xFFA4D007))
/** 2004–2006 (gerçek steam.styles değerleri): GreenBG 76,88,68 · LightGreenBG 90,106,80 · DarkGreenBG 62,70,55 · Maize 196,181,80 · Text 160,170,149 */
val PAL_2006 = Pal(Color(0xFF4C5844), Color(0xFF3E4637), Color(0xFF5A6A50), Color(0xFF3E4637), Color(0xFFC4B550), Color(0xFFD8DED3), Color(0xFFA0AA95),
    Color(0xFF8C9E6B), Color(0xFF5F6F47), Color(0xFF5A6A50), Color(0xFF3E4637), Color(0xFF455139), Color(0xFF5A6A50), Color(0xFFE5E2DF), listOf(Color(0xFF3E4637), Color(0xFF5A6A50), Color(0xFF889180)), 0,
    pGrad = listOf(Color(0xFF4A5640), Color(0xFF3E4637)), edgeHi = Color(0xFF889180), edgeLo = Color(0xFF282E22), topAccent = Color(0xFFC4B550))

/** Anlık temaya göre değişen Steam renkleri (Compose durumu: tema değişince arayüz kendiliğinden yenilenir). */
object Steam {
    var pal by mutableStateOf(PAL_MODERN)
    var material by mutableStateOf(false)
    val bg get() = pal.bg; val panel get() = pal.panel; val panel2 get() = pal.panel2; val card get() = pal.card
    val blue get() = pal.blue; val text get() = pal.text; val dim get() = pal.dim
    val top get() = pal.top; val topEnd get() = pal.topEnd; val row get() = pal.row; val btn get() = pal.btn; val link get() = pal.link
    val greenA get() = pal.greenA; val greenB get() = pal.greenB; val hero get() = pal.hero; val corner get() = pal.corner
    val priceBg = Color(0xFF4C6B22)
    /** 2006 teması: sivri köşeli, kabartma (bevel) kenarlı */
    val bevel get() = pal === PAL_2006
    val panelGrad get() = pal.pGrad; val edgeHi get() = pal.edgeHi; val edgeLo get() = pal.edgeLo; val glossy get() = pal.glossy; val carbon get() = pal.carbon; val topAccent get() = pal.topAccent
    val detailBg get() = pal.detailBg ?: pal.bg; val box get() = pal.box ?: pal.panel; val buy get() = pal.buy ?: listOf(pal.panel2, pal.panel); val wish get() = pal.wish ?: pal.panel2
    val topBrush get() = Brush.verticalGradient(listOf(top, topEnd))
}

private fun steamScheme() = darkColorScheme(
    primary = Steam.blue, onPrimary = Color(0xFF0B1620),
    secondary = Steam.greenA, onSecondary = Color.White,
    background = Steam.bg, onBackground = Steam.text,
    surface = Steam.panel, onSurface = Steam.text,
    surfaceVariant = Steam.card, onSurfaceVariant = Steam.dim,
    surfaceContainer = Steam.panel, surfaceContainerHigh = Steam.panel2,
    outline = Steam.panel2,
)

@Composable
fun PunkTheme(design: Design, dark: Boolean, amoled: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when (design) {
        Design.STEAM, Design.STEAM2013, Design.STEAM2006 -> steamScheme()
        Design.MATERIAL -> {
            val useDark = dark || amoled
            val base = if (Build.VERSION.SDK_INT >= 31) {
                if (useDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            } else if (useDark) darkColorScheme() else lightColorScheme()
            if (amoled) base.copy(
                background = Color.Black, surface = Color.Black,
                surfaceContainer = Color(0xFF0B0B0B), surfaceContainerHigh = Color(0xFF141414),
                surfaceContainerHighest = Color(0xFF1C1C1C), surfaceContainerLow = Color(0xFF050505),
                surfaceVariant = Color(0xFF121212),
            ) else base
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun palFor(d: Design) = when (d) { Design.STEAM2013 -> PAL_2013; Design.STEAM2006 -> PAL_2006; else -> PAL_MODERN }
