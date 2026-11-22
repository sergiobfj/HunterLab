package dev.hunterlab.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object HunterSpace {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Page = 24.dp
    val Section = 32.dp
    val Major = 40.dp
}

object HunterShapes {
    val Small = RoundedCornerShape(10.dp)
    val Medium = RoundedCornerShape(14.dp)
    val Large = RoundedCornerShape(18.dp)
    val Material = Shapes(extraSmall = Small, small = Small, medium = Medium, large = Large, extraLarge = Large)
}

object HunterElevation {
    val Flat = 0.dp
    val Overlay = 3.dp
}
