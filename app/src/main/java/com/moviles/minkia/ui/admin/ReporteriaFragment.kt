package com.moviles.minkia.ui.admin

import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import com.google.android.material.datepicker.MaterialDatePicker
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.mostrarSkeleton
import com.moviles.minkia.data.model.FilaReporte
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.databinding.FragmentAdminReportesBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Reporte municipal (A06). El admin elige un rango de fechas y un nivel; genera la
 * lista REAL desde Firestore y la exporta como PDF (para archivar/firmar) o CSV
 * (para Excel/sistemas municipales). La misma lista filtrada alimenta pantalla,
 * PDF y CSV: una fuente, tres salidas.
 */
class ReporteriaFragment : BaseFragment<FragmentAdminReportesBinding>() {

    private val viewModel: ReporteriaViewModel by viewModels { ReporteriaViewModel.Factory() }
    private val adapter = FilaReporteAdapter()

    private var desdeMs = inicioDeDia(hace(30))
    private var hastaMs = finDeDia(System.currentTimeMillis())
    private var nivel: Severidad? = null
    private var filasActuales: List<FilaReporte> = emptyList()

    // Se retiene mientras el WebView arma el PDF; si no, lo recoge el GC a mitad.
    private var webViewPdf: WebView? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAdminReportesBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerReportesContenido.aplicarInsetSuperior()
        binding.rvReportes.adapter = adapter

        pintarFiltros()

        binding.btnDesde.setOnClickListener { elegirFecha(esDesde = true) }
        binding.btnHasta.setOnClickListener { elegirFecha(esDesde = false) }
        binding.btnNivel.setOnClickListener { elegirNivel() }
        binding.btnGenerar.setOnClickListener { generar() }
        binding.btnPdf.setOnClickListener { exportarPdf() }
        binding.btnCsv.setOnClickListener { exportarCsv() }

        viewModel.uiState.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is UiState.Loading -> {
                    binding.skeletonReportes.root.mostrarSkeleton(true)
                    binding.rvReportes.visibility = View.GONE
                    binding.tvVacio.visibility = View.GONE
                    habilitarExportar(false)
                }
                is UiState.Success -> {
                    binding.skeletonReportes.root.mostrarSkeleton(false)
                    mostrarResultado(estado.data)
                }
                is UiState.Error -> {
                    binding.skeletonReportes.root.mostrarSkeleton(false)
                    binding.rvReportes.visibility = View.GONE
                    binding.tvVacio.visibility = View.VISIBLE
                    habilitarExportar(false)
                    Toast.makeText(requireContext(), R.string.admin_rep_error, Toast.LENGTH_LONG).show()
                }
            }
        }

        generar() // primera carga con el rango por defecto (últimos 30 días)
    }

    private fun mostrarResultado(filas: List<FilaReporte>) {
        filasActuales = filas
        binding.tvTotal.text = getString(R.string.admin_rep_total, filas.size)
        val hay = filas.isNotEmpty()
        binding.rvReportes.visibility = if (hay) View.VISIBLE else View.GONE
        binding.tvVacio.visibility = if (hay) View.GONE else View.VISIBLE
        habilitarExportar(hay)
        adapter.submitList(filas)
    }

    private fun generar() {
        if (desdeMs > hastaMs) {
            Toast.makeText(requireContext(), R.string.admin_rep_rango_invalido, Toast.LENGTH_LONG).show()
            return
        }
        viewModel.generar(desdeMs, hastaMs, nivel)
    }

    /** Selector de fecha (Material). Normaliza a inicio (desde) o fin (hasta) del día. */
    private fun elegirFecha(esDesde: Boolean) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(if (esDesde) desdeMs else hastaMs)
            .build()
        picker.addOnPositiveButtonClickListener { utc ->
            if (esDesde) desdeMs = inicioDeDia(aLocal(utc)) else hastaMs = finDeDia(aLocal(utc))
            pintarFiltros()
        }
        picker.show(childFragmentManager, "fecha")
    }

    private fun elegirNivel() {
        val opciones = arrayOf(getString(R.string.admin_rep_nivel_todos), "ALTA", "MEDIA", "BAJA")
        val niveles = arrayOf<Severidad?>(null, Severidad.ALTA, Severidad.MEDIA, Severidad.BAJA)
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_rep_nivel)
            .setSingleChoiceItems(opciones, niveles.indexOf(nivel)) { dialog, cual ->
                dialog.dismiss()
                nivel = niveles[cual]
                pintarFiltros()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pintarFiltros() {
        binding.btnDesde.text = getString(R.string.admin_rep_desde) + ": " + fmtCorto(desdeMs)
        binding.btnHasta.text = getString(R.string.admin_rep_hasta) + ": " + fmtCorto(hastaMs)
        val nivelTexto = nivel?.name ?: getString(R.string.admin_rep_nivel_todos)
        binding.btnNivel.text = getString(R.string.admin_rep_nivel) + ": " + nivelTexto
    }

    private fun habilitarExportar(habilitado: Boolean) {
        binding.btnPdf.isEnabled = habilitado
        binding.btnCsv.isEnabled = habilitado
    }

    /**
     * Genera el PDF con un WebView invisible que imprime un HTML maquetado (la
     * paginación la resuelve el sistema). Abre el diálogo de impresión de Android,
     * donde el admin elige "Guardar como PDF".
     */
    private fun exportarPdf() {
        if (filasActuales.isEmpty()) return
        val web = WebView(requireContext())
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val servicio = requireContext().getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
                val nombre = getString(R.string.admin_rep_pdf_titulo)
                val adaptador = view.createPrintDocumentAdapter(nombre)
                servicio.print(nombre, adaptador, PrintAttributes.Builder().build())
                webViewPdf = null // ya se creó el trabajo de impresión: liberamos
            }
        }
        web.loadDataWithBaseURL(null, htmlReporte(filasActuales), "text/html", "UTF-8", null)
        webViewPdf = web
    }

    /** Escribe el CSV en cache y lo comparte por un content:// seguro (FileProvider). */
    private fun exportarCsv() {
        if (filasActuales.isEmpty()) return
        try {
            val dir = File(requireContext().cacheDir, "exportes").apply { mkdirs() }
            val archivo = File(dir, "reporte_minkia.csv")
            archivo.writeText(viewModel.csv(filasActuales))
            val uri = FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.fileprovider", archivo
            )
            val enviar = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.admin_rep_pdf_titulo))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(enviar, getString(R.string.admin_rep_csv)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.admin_rep_error, Toast.LENGTH_LONG).show()
        }
    }

    /** HTML del reporte municipal: tabla con la identidad MinkIA para el PDF. */
    private fun htmlReporte(filas: List<FilaReporte>): String {
        val filasHtml = filas.joinToString("") { f ->
            "<tr>" +
                "<td>${esc(f.ticket)}</td>" +
                "<td>${esc(f.direccion)}</td>" +
                "<td>${esc(f.tipo)}</td>" +
                "<td>${esc(f.severidad.name)}</td>" +
                "<td>${esc(f.estado.etiqueta)}</td>" +
                "<td>${esc(f.fechaHoraTexto)}</td>" +
                "<td>${f.latitud}, ${f.longitud}</td>" +
                "</tr>"
        }
        val rango = "${fmtCorto(desdeMs)} a ${fmtCorto(hastaMs)}"
        val nivelTexto = nivel?.name ?: getString(R.string.admin_rep_nivel_todos)
        return """
            <html><head><meta charset="utf-8"><style>
              body { font-family: sans-serif; color: #223; padding: 8px; }
              h1 { color: #1b4332; font-size: 20px; margin: 0 0 2px; }
              p.sub { color: #667; font-size: 12px; margin: 0 0 14px; }
              table { width: 100%; border-collapse: collapse; font-size: 11px; }
              th { background: #1b4332; color: #fff; text-align: left; padding: 6px; }
              td { border-bottom: 1px solid #ddd; padding: 6px; }
              tr:nth-child(even) td { background: #f4f7f4; }
            </style></head><body>
              <h1>MinkIA · Reporte municipal</h1>
              <p class="sub">Chimbote, Áncash · Rango: $rango · Nivel: $nivelTexto · ${filas.size} reportes</p>
              <table>
                <tr><th>Ticket</th><th>Dirección</th><th>Tipo</th><th>Nivel</th><th>Estado</th><th>Fecha</th><th>Coordenadas</th></tr>
                $filasHtml
              </table>
            </body></html>
        """.trimIndent()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // --- Fechas ---
    private fun fmtCorto(ms: Long): String =
        SimpleDateFormat("dd MMM", Locale("es")).format(java.util.Date(ms))

    /** Extrae Y/M/D de la selección (UTC del picker) y la lleva a la zona local. */
    private fun aLocal(utcMs: Long): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMs }
        return Calendar.getInstance().apply {
            clear()
            set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    private fun inicioDeDia(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun finDeDia(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    private fun hace(dias: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -dias)
    }.timeInMillis

    override fun onBindingDestroy() {
        binding.rvReportes.adapter = null
        webViewPdf = null
    }
}
