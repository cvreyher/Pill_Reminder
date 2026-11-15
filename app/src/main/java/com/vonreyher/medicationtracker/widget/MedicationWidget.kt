package com.vonreyher.medicationtracker.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.vonreyher.medicationtracker.MainActivity
import com.vonreyher.medicationtracker.data.AppMode
import com.vonreyher.medicationtracker.data.MedicationStore
import com.vonreyher.medicationtracker.data.Person
import com.vonreyher.medicationtracker.formatted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Round home screen widget in the app's purple: the ring shows how many of today's scheduled
 * doses are done, the center what has to be taken next.
 */
class MedicationWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = MedicationStore.get(context)
        provideContent {
            // Computed inside the composition: it reads the store's snapshot state, so the widget
            // recomposes as soon as an intake is confirmed, while its session is still running.
            val state = WidgetState.from(store, LocalTime.now())
            WidgetContent(context, state, WidgetColors.resolve(context))
        }
    }
}

class MedicationWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MedicationWidget()
}

/** Refreshes all placed widgets, e.g. after an intake was confirmed. */
object MedicationWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun update(context: Context) {
        val appContext = context.applicationContext
        scope.launch { runCatching { MedicationWidget().updateAll(appContext) } }
    }
}

private data class WidgetState(
    val headline: String,
    val title: String,
    val subtitle: String,
    val progress: Float
) {
    companion object {
        fun from(store: MedicationStore, now: LocalTime): WidgetState {
            val careMode = store.settings.mode == AppMode.Care
            val scheduled = store.activeMedications
                .filter { !it.asNeeded && (careMode || it.personId == Person.SELF_ID) }
                .sortedBy { it.time }
            val done = scheduled.count { it.taken }
            val total = scheduled.size
            val progress = if (total == 0) 0f else done.toFloat() / total
            val next = scheduled.firstOrNull { !it.taken }

            return when {
                total == 0 -> WidgetState("–", "Keine Einnahmen", "für heute geplant", 0f)
                next == null -> WidgetState("✓", "Alles erledigt", "$done von $total", 1f)
                else -> {
                    val time = next.time!!
                    val person = store.person(next.personId)?.takeIf { careMode }
                    WidgetState(
                        headline = if (time.isAfter(now)) time.formatted() else "Jetzt",
                        title = next.name,
                        subtitle = listOfNotNull(person?.firstName?.ifBlank { null }, "$done von $total erledigt")
                            .joinToString(" · "),
                        progress = progress
                    )
                }
            }
        }
    }
}

private data class WidgetColors(
    val container: Int,
    val track: Int,
    val progress: Int,
    val text: Int,
    val textVariant: Int
) {
    companion object {
        /** The app's purple palette, light or dark like the system. */
        fun resolve(context: Context): WidgetColors {
            val night = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return if (night) {
                WidgetColors(0xFF533D6C.toInt(), 0xFF6B5585.toInt(), 0xFFD8BDF2.toInt(), 0xFFEDD9FF.toInt(), 0xFFD8BDF2.toInt())
            } else {
                WidgetColors(0xFFEDD9FF.toInt(), 0xFFD8BDF2.toInt(), 0xFF6B5585.toInt(), 0xFF261136.toInt(), 0xFF6B5585.toInt())
            }
        }
    }
}

@Composable
private fun WidgetContent(context: Context, state: WidgetState, colors: WidgetColors) {
    val size = LocalSize.current
    val side: Dp = min(size.width.value, size.height.value).dp
    val sidePx = (side.value * context.resources.displayMetrics.density).roundToInt().coerceIn(96, 900)
    val text = ColorProvider(Color(colors.text))
    val textVariant = ColorProvider(Color(colors.textVariant))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(renderCircle(sidePx, state.progress, colors)),
            contentDescription = null,
            modifier = GlanceModifier.size(side)
        )
        Column(
            modifier = GlanceModifier
                .size(side)
                .padding(side * 0.2f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.headline,
                maxLines = 1,
                style = TextStyle(
                    color = text,
                    fontSize = (side.value * 0.15f).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )
            Text(
                text = state.title,
                maxLines = 2,
                style = TextStyle(
                    color = text,
                    fontSize = (side.value * 0.075f).sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            )
            Text(
                text = state.subtitle,
                maxLines = 1,
                style = TextStyle(
                    color = textVariant,
                    fontSize = (side.value * 0.06f).sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

/**
 * Draws the round background with the progress ring. Drawn as a bitmap so the widget stays a
 * circle whatever size the launcher gives it.
 */
private fun renderCircle(sizePx: Int, progress: Float, colors: WidgetColors): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = colors.container
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

    val center = sizePx / 2f
    val radius = sizePx * 0.38f
    val oval = RectF(center - radius, center - radius, center + radius, center + radius)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = sizePx * 0.045f
    paint.strokeCap = Paint.Cap.ROUND
    paint.color = colors.track
    canvas.drawArc(oval, 0f, 360f, false, paint)
    if (progress > 0f) {
        paint.color = colors.progress
        canvas.drawArc(oval, -90f, 360f * progress.coerceIn(0f, 1f), false, paint)
    }
    return bitmap
}
