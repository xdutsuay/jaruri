package com.example.moneymanager.ui

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.moneymanager.R
import com.example.moneymanager.utils.CsvFormatter
import com.example.moneymanager.viewmodel.MainViewModel
import java.io.File

class ExportFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            exportToCSV()
        } else {
            Toast.makeText(
                requireContext(),
                "Permission required to save file in older Android versions.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val openCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        importFromUri(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_export, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<Button>(R.id.btn_export_csv).setOnClickListener {
            if (viewModel.allTransactions.value.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "No transactions to export.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportToCSV()
            } else {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    exportToCSV()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }

        view.findViewById<Button>(R.id.btn_import_csv).setOnClickListener {
            openCsvLauncher.launch(arrayOf("text/*", "text/csv", "application/csv", "*/*"))
        }
    }

    private fun importFromUri(uri: Uri) {
        try {
            val text = requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text.isNullOrBlank()) {
                Toast.makeText(requireContext(), R.string.import_csv_failed, Toast.LENGTH_SHORT).show()
                return
            }
            val datePattern = viewModel.dateFormat.value ?: "yyyy-MM-dd"
            val rows = CsvFormatter.parse(text, datePattern)
            if (rows.isEmpty()) {
                Toast.makeText(requireContext(), R.string.import_csv_empty, Toast.LENGTH_SHORT).show()
                return
            }
            viewModel.importTransactions(rows)
            Toast.makeText(
                requireContext(),
                getString(R.string.import_csv_done, rows.size),
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.import_csv_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportToCSV() {
        val transactions = viewModel.allTransactions.value ?: return
        val datePattern = viewModel.dateFormat.value ?: "yyyy-MM-dd"

        val csvData = CsvFormatter.format(transactions, datePattern)
        val filename = "Jaruri_Export_${System.currentTimeMillis()}.csv"

        var fileUri: Uri? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = requireContext().contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            fileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            fileUri?.let { uri ->
                resolver.openOutputStream(uri)?.use { it.write(csvData.toByteArray()) }
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, filename)
            file.writeText(csvData)

            fileUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
        }

        if (fileUri != null) {
            Toast.makeText(requireContext(), "Exported to Downloads", Toast.LENGTH_SHORT).show()
            shareFile(fileUri)
        }
    }

    private fun shareFile(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share CSV file"))
    }
}
