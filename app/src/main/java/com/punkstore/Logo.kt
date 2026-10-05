package com.punkstore

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Köşeli, blok yazılı logo fontu (Russo One, OFL). */
val LogoFont = FontFamily(Font(R.font.russo_one))

/** "(Steam)Punk" üst satır, altında aralıklı "STORE"; isteğe bağlı karesel köşe çerçevesi. */
@Composable
fun PunkLogo(size: Float = 36f, modifier: Modifier = Modifier, color: Color = Color.White, accent: Color = Color(0xFFB7E3FF), frame: Boolean = true, align: Alignment.Horizontal = Alignment.Start, showSteam: Boolean = false) {
    val pad = (size * .28f).dp
    Column(modifier.then(if (frame) Modifier.drawBehind {
        val l = size.sp.toPx() * .45f; val w = 2.dp.toPx(); val c = accent
        fun corner(x: Float, y: Float, dx: Float, dy: Float) { drawLine(c, Offset(x, y), Offset(x + dx * l, y), w); drawLine(c, Offset(x, y), Offset(x, y + dy * l), w) }
        corner(0f, 0f, 1f, 1f); corner(this.size.width, 0f, -1f, 1f); corner(0f, this.size.height, 1f, -1f); corner(this.size.width, this.size.height, -1f, -1f)
    } else Modifier).padding(pad), horizontalAlignment = align) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (showSteam) Text("(Steam)", fontFamily = LogoFont, fontSize = (size * .42f).sp, color = accent.copy(alpha = .75f), modifier = Modifier.padding(bottom = (size * .08f).dp))
            Box { Text("Punk", Modifier.offset(x = (size * .035f).dp), fontFamily = LogoFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = size.sp, color = color, lineHeight = size.sp)
            Text("Punk", fontFamily = LogoFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = size.sp, color = color, lineHeight = size.sp)
            }
        }
        Box { Text("STORE", Modifier.offset(x = (size * .02f).dp).padding(top = (size * .04f).dp), fontFamily = LogoFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = (size * .5f).sp, color = accent, letterSpacing = (size * .22f).sp, lineHeight = (size * .5f).sp)
        Text("STORE", fontFamily = LogoFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = (size * .5f).sp, color = accent, letterSpacing = (size * .22f).sp, lineHeight = (size * .5f).sp, modifier = Modifier.padding(top = (size * .04f).dp)) }
    }
}
