package sh.tuesday.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = TuesdayTheme.HealthyGreen,
    onPrimary = Color.Black,
    secondary = TuesdayTheme.HacsCyan,
    onSecondary = Color.Black,
    tertiary = TuesdayTheme.WarningAmber,
    background = TuesdayTheme.Background,
    surface = TuesdayTheme.CardSurface,
    onBackground = Color.White,
    onSurface = Color.White,
    error = TuesdayTheme.CriticalRed,
    onError = Color.White
)

@Composable
fun TuesdayAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
