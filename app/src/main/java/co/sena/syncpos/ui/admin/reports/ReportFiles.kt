package co.sena.syncpos.ui.admin.reports

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import co.sena.syncpos.data.remote.ProfitabilityDto
import co.sena.syncpos.domain.DateRange
import co.sena.syncpos.domain.buildProfitabilityCsv
import co.sena.syncpos.util.formatCop
import co.sena.syncpos.util.formatPercent
import java.io.File
import java.time.format.DateTimeFormatter

// Crea los archivos del reporte (CSV y PDF) y los comparte con otras apps.
// Estas funciones hacen entrada/salida de disco: deben llamarse FUERA del hilo principal
// (la pantalla las ejecuta con withContext(Dispatchers.IO)).
object ReportFiles {

    const val MIME_CSV = "text/csv"
    const val MIME_PDF = "application/pdf"

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    // Carpeta donde se guardan los reportes: cacheDir/reports/.
    // cacheDir es privada de la app y Android puede limpiarla cuando falte espacio, ideal
    // para archivos temporales que solo se crean para compartirlos.
    private fun reportsDir(context: Context): File =
        File(context.cacheDir, "reports").apply { mkdirs() }

    private fun fileName(range: DateRange, extension: String) =
        "rentabilidad_${range.from}_${range.to}.$extension" // LocalDate.toString() = 2026-10-20

    // ---------- CSV ----------
    fun writeCsv(context: Context, report: ProfitabilityDto, range: DateRange): File {
        val file = File(reportsDir(context), fileName(range, "csv"))
        // "﻿" es la marca BOM: le indica a Excel que el archivo es UTF-8 y asi las
        // tildes ("Contribución") no salen como simbolos raros.
        file.writeText("﻿" + buildProfitabilityCsv(report.products), Charsets.UTF_8)
        return file
    }

    // ---------- PDF ----------
    // PdfDocument (incluido en Android, sin librerias) funciona como un cuaderno: se abre
    // una pagina con startPage, se dibuja en su Canvas con Paint (pincel) y se cierra con
    // finishPage. Las medidas estan en "puntos": una hoja A4 mide 595 x 842.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val ROWS_PER_PAGE = 35
    private const val ROW_HEIGHT = 16f

    fun writePdf(context: Context, report: ProfitabilityDto, range: DateRange): File {
        val file = File(reportsDir(context), fileName(range, "pdf"))
        val document = PdfDocument()

        val normal = Paint().apply { textSize = 9f; color = 0xFF0F172A.toInt() }
        val bold = Paint(normal).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val right = Paint(normal).apply { textAlign = Paint.Align.RIGHT }
        val rightBold = Paint(bold).apply { textAlign = Paint.Align.RIGHT }

        // Se calculan cuantas paginas hacen falta: 35 filas por pagina.
        val products = report.products
        val pageCount = maxOf(1, (products.size + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE)

        for (pageIndex in 0 until pageCount) {
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
            val page = document.startPage(info)
            val canvas = page.canvas
            var y = MARGIN + 12f

            // La primera pagina lleva titulo, periodo y totales; las demas solo la tabla.
            if (pageIndex == 0) {
                canvas.drawText("Reporte de Rentabilidad", MARGIN, y, Paint(bold).apply { textSize = 18f })
                y += 22f
                canvas.drawText(
                    "Periodo: ${range.from.format(dateFormat)} al ${range.to.format(dateFormat)}",
                    MARGIN, y, normal
                )
                y += 22f
                val totals = report.totals
                canvas.drawText("Ingresos por ventas: ${formatCop(totals.revenue)}", MARGIN, y, normal)
                y += 14f
                canvas.drawText("Costo de mercancia: ${formatCop(totals.cost)}", MARGIN, y, normal)
                y += 14f
                canvas.drawText(
                    "Ganancia neta: ${formatCop(totals.profit)} (margen ${formatPercent(totals.margin)})",
                    MARGIN, y, bold
                )
                y += 28f
            }

            // Encabezado de la tabla. Las columnas numericas se alinean a la DERECHA
            // (el punto x es el borde derecho del texto).
            drawRow(canvas, y, bold, rightBold, "Producto", "Und.", "Ingresos", "Costo", "Ganancia", "Margen")
            y += 4f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, normal)
            y += ROW_HEIGHT - 4f

            // Filas de ESTA pagina: subList toma un trozo de la lista.
            val from = pageIndex * ROWS_PER_PAGE
            val to = minOf(products.size, from + ROWS_PER_PAGE)
            products.subList(from, to).forEach { p ->
                drawRow(
                    canvas, y, normal, right,
                    shorten(p.name, normal, 190f),
                    p.units.toString(),
                    formatCop(p.revenue),
                    formatCop(p.cost),
                    formatCop(p.profit),
                    formatPercent(p.margin)
                )
                y += ROW_HEIGHT
            }

            // Numero de pagina al pie.
            canvas.drawText("Página ${pageIndex + 1} de $pageCount", MARGIN, PAGE_HEIGHT - 24f, normal)
            document.finishPage(page)
        }

        // Escribir el documento al archivo y liberar recursos (use = cierra el stream solo).
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
    }

    // Dibuja una fila de la tabla: la primera celda a la izquierda y las otras cinco
    // alineadas a la derecha en posiciones fijas.
    private fun drawRow(
        canvas: android.graphics.Canvas,
        y: Float,
        leftPaint: Paint,
        rightPaint: Paint,
        name: String,
        units: String,
        revenue: String,
        cost: String,
        profit: String,
        margin: String
    ) {
        canvas.drawText(name, MARGIN, y, leftPaint)
        canvas.drawText(units, 270f, y, rightPaint)
        canvas.drawText(revenue, 340f, y, rightPaint)
        canvas.drawText(cost, 415f, y, rightPaint)
        canvas.drawText(profit, 490f, y, rightPaint)
        canvas.drawText(margin, PAGE_WIDTH - MARGIN, y, rightPaint)
    }

    // Recorta un nombre largo para que quepa en "maxWidth" puntos (agrega "...").
    // breakText devuelve cuantos caracteres caben en ese ancho.
    private fun shorten(text: String, paint: Paint, maxWidth: Float): String {
        val count = paint.breakText(text, true, maxWidth, null)
        return if (count >= text.length) text else text.take((count - 3).coerceAtLeast(1)) + "..."
    }

    // ---------- Compartir ----------
    // Un archivo de cacheDir es PRIVADO: otra app no puede abrirlo con su ruta. Para
    // compartirlo se usa un FileProvider, que entrega una URI temporal (content://...)
    // y le da permiso de lectura SOLO a la app que reciba el Intent.
    // Configuracion: <provider> en AndroidManifest.xml + res/xml/file_paths.xml.
    fun share(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        // ACTION_SEND = "enviar este contenido a otra app". El chooser muestra la hoja
        // para elegir cual (Drive, WhatsApp, correo...).
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Reporte de rentabilidad")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartir reporte"))
    }
}
