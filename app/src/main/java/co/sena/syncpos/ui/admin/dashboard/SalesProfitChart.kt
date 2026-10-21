package co.sena.syncpos.ui.admin.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.sena.syncpos.data.remote.DayPointDto
import co.sena.syncpos.domain.shortWeekday
import co.sena.syncpos.ui.pos.AppCard
import co.sena.syncpos.ui.theme.BorderGray
import co.sena.syncpos.ui.theme.DarkSlate
import co.sena.syncpos.ui.theme.SenaGreen
import co.sena.syncpos.ui.theme.TextSecondary

// Tarjeta "Ventas vs. Ganancias · 7 dias".
// El grafico se dibuja a mano con Canvas (sin librerias): barras verdes = ventas y
// una linea oscura con puntos = ganancia.
@Composable
fun SalesProfitChartCard(points: List<DayPointDto>, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Ventas vs. Ganancias · 7 días",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkSlate
            )

            // Leyenda
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(SenaGreen, "Ventas")
                LegendDot(DarkSlate, "Ganancia")
            }

            SalesProfitChart(points = points)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun SalesProfitChart(points: List<DayPointDto>) {
    if (points.isEmpty()) {
        Text("Sin datos para graficar.", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        return
    }

    // El valor mas alto de TODAS las barras y puntos define la altura maxima del grafico.
    // Si todo es 0 se usa 1 para no dividir entre cero.
    val maxValue = points.maxOf { maxOf(it.sales, it.profit) }.coerceAtLeast(1.0)

    Column {
        // Canvas = un lienzo en blanco donde dibujamos con coordenadas en pixeles.
        // El origen (0,0) esta ARRIBA a la izquierda: la "y" crece hacia ABAJO.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val chartWidth = size.width
            val chartHeight = size.height

            // 1) Lineas guia horizontales (0 %, 50 % y 100 % de la altura).
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = chartHeight - chartHeight * fraction
                drawLine(
                    color = BorderGray,
                    start = Offset(0f, y),
                    end = Offset(chartWidth, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 2) El ancho se divide en una "ranura" por dia. Cada barra y cada punto se
            // centran en su ranura.
            val slotWidth = chartWidth / points.size
            val barWidth = slotWidth * 0.5f

            // 3) Barras de ventas. Altura = (valor / maximo) * altura total.
            //    Como la "y" crece hacia abajo, la parte de arriba de la barra esta en
            //    y = alturaTotal - alturaBarra.
            points.forEachIndexed { index, point ->
                val barHeight = (point.sales / maxValue).toFloat().coerceAtLeast(0f) * chartHeight
                val centerX = slotWidth * index + slotWidth / 2
                drawRoundRect(
                    color = SenaGreen,
                    topLeft = Offset(centerX - barWidth / 2, chartHeight - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }

            // 4) Linea de ganancia: un Path que une los puntos (misma escala que las barras).
            val path = Path()
            val linePoints = points.mapIndexed { index, point ->
                val centerX = slotWidth * index + slotWidth / 2
                val y = chartHeight - (point.profit / maxValue).toFloat().coerceAtLeast(0f) * chartHeight
                Offset(centerX, y)
            }
            linePoints.forEachIndexed { index, offset ->
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }
            drawPath(
                path = path,
                color = DarkSlate,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 5) Un punto (circulo) sobre cada dato de la linea.
            linePoints.forEach { offset ->
                drawCircle(color = DarkSlate, radius = 4.dp.toPx(), center = offset)
            }
        }

        // Etiquetas de los dias. Cada una usa weight(1f) = mismo ancho, igual que las
        // ranuras del Canvas, por eso quedan justo debajo de su barra.
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            points.forEach { point ->
                Text(
                    text = shortWeekday(point.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

