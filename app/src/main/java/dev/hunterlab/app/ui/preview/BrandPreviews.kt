package dev.hunterlab.app.ui.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hunterlab.app.R

private val BrandGraphite = Color(0xFF101416)
private val BrandInk = Color(0xFFE8ECEE)
private val BrandLime = Color(0xFFB7CF62)

/** Prévia editorial: máscaras aproximadas, sem afirmar execução em Samsung/Pixel. */
@Composable
private fun BrandIcon(size: Dp, shape: Shape, monochrome: Boolean = false, light: Boolean = false) {
    Box(Modifier.size(size).clip(shape).background(if (light) Color(0xFFE5EACF) else BrandGraphite)) {
        if (monochrome) Icon(
            painterResource(R.drawable.ic_launcher_monochrome), "Hunter Lab",
            Modifier.requiredSize(size * 1.5f).align(Alignment.Center), tint = if (light) BrandGraphite else BrandLime,
        ) else Image(painterResource(R.drawable.ic_launcher_foreground), "Hunter Lab", Modifier.requiredSize(size * 1.5f).align(Alignment.Center))
    }
}

@Preview(name = "Marca · máscaras e escala", widthDp = 360, heightDp = 640)
@Composable
fun BrandMasksPreview() {
    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color(0xFF171C1F)).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("Hunter Lab", color = BrandInk, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            Text("H em movimento", color = Color(0xFFA7B0B5), fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                BrandIcon(88.dp, CircleShape)
                BrandIcon(88.dp, RoundedCornerShape(27.dp))
            }
            Text("Circular / arredondado · máscaras aproximadas", color = Color(0xFFA7B0B5), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                for (size in listOf(48, 32, 24)) BrandIcon(size.dp, RoundedCornerShape((size * .3f).dp))
            }
            Text("48 / 32 / 24 dp", color = Color(0xFFA7B0B5), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                BrandIcon(88.dp, CircleShape, monochrome = true)
                BrandIcon(88.dp, CircleShape, monochrome = true, light = true)
            }
            Text("Themed icons · escuro / claro", color = Color(0xFFA7B0B5), fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().background(Color(0xFFE8ECEE)).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BrandIcon(48.dp, CircleShape)
                BrandIcon(48.dp, CircleShape, monochrome = true, light = true)
            }
        }
    }
}

@Preview(name = "Launcher · simulação", widthDp = 360, heightDp = 800)
@Composable
fun BrandLauncherPreview() {
    MaterialTheme {
        Column(Modifier.fillMaxSize().background(BrandGraphite).padding(horizontal = 28.dp, vertical = 40.dp)) {
            Text("Simulação de launcher", color = Color(0xFFA7B0B5), fontSize = 13.sp)
            Spacer(Modifier.height(68.dp))
            Text("Hunter\nLab", color = BrandInk, fontSize = 52.sp, lineHeight = 54.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.size(38.dp, 3.dp).background(BrandLime))
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BrandIcon(60.dp, RoundedCornerShape(18.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Hunter Lab", color = BrandInk, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF263035))) {
                        Text("12", Modifier.align(Alignment.Center), color = BrandInk, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Agenda", color = BrandInk, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(60.dp))
            Text("Recorte arredondado inspirado em One UI.\nPrévia ilustrativa; sem validação no aparelho.", color = Color(0xFFA7B0B5), fontSize = 11.sp)
        }
    }
}

@Preview(name = "Abertura · composição", widthDp = 360, heightDp = 800)
@Composable
fun BrandSplashPreview() {
    Box(Modifier.fillMaxSize().background(BrandGraphite), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_foreground), "Hunter Lab", Modifier.size(240.dp))
    }
}

@Preview(name = "Marca · fonte 150% / 320 dp", widthDp = 320, heightDp = 800, fontScale = 1.5f)
@Composable
fun BrandLargeFontPreview() = BrandMasksPreview()
