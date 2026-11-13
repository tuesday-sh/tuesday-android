package sh.tuesday.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import sh.tuesday.app.theme.BrandPalette

val SectionHeadingStyle = TextStyle(
    fontSize = 14.sp,
    fontWeight = FontWeight.Bold
)

@Composable
fun SectionHeading(
    text: String,
    palette: BrandPalette,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = palette.textPrimary,
        style = SectionHeadingStyle,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}
