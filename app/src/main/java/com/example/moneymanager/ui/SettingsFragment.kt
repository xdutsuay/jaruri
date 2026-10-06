package com.example.moneymanager.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.moneymanager.R
import com.example.moneymanager.data.SettingsRepository
import com.example.moneymanager.utils.SmsHistoryBackfill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private lateinit var settingsRepo: SettingsRepository
    private var switchAutoImport: Switch? = null
    private var switchUsageWellbeing: Switch? = null
    private var suppressAutoImportCallback = false
    private var suppressUsageCallback = false

    private val requestSmsPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val receiveOk = results[Manifest.permission.RECEIVE_SMS] == true
        val readOk = results[Manifest.permission.READ_SMS] == true
        val notifyOk = if (Build.VERSION.SDK_INT >= 33) {
            results[Manifest.permission.POST_NOTIFICATIONS] != false
        } else {
            true
        }
        if (receiveOk && readOk) {
            persistAutoImport(true)
            if (!notifyOk) {
                Toast.makeText(
                    requireContext(),
                    R.string.settings_auto_import_notify_optional,
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            setAutoImportChecked(false)
            Toast.makeText(
                requireContext(),
                R.string.settings_auto_import_permission_denied,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val requestReadForBackfill = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) runHistoryBackfill()
        else {
            Toast.makeText(
                requireContext(),
                R.string.import_sms_permission_denied,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        settingsRepo = SettingsRepository(requireContext())

        val spCurrency = view.findViewById<Spinner>(R.id.sp_currency)
        val etDateFormat = view.findViewById<EditText>(R.id.et_date_format)
        val spFiscal = view.findViewById<Spinner>(R.id.sp_fiscal_year)
        switchAutoImport = view.findViewById(R.id.switch_auto_import_sms)
        switchUsageWellbeing = view.findViewById(R.id.switch_usage_wellbeing)

        val currencyLabels = SettingsRepository.CURRENCY_OPTIONS.map { it.first }
        spCurrency.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            currencyLabels
        )

        val fyLabels = listOf(
            getString(R.string.settings_fiscal_calendar),
            getString(R.string.settings_fiscal_apr_mar)
        )
        spFiscal.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            fyLabels
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val symbol = settingsRepo.currencySymbol.first()
            val idx = SettingsRepository.CURRENCY_OPTIONS.indexOfFirst { it.second == symbol }
                .takeIf { it >= 0 } ?: 0
            spCurrency.setSelection(idx)
            etDateFormat.setText(settingsRepo.dateFormat.first())
            setAutoImportChecked(settingsRepo.autoImportSmsEnabled.first())
            setUsageWellbeingChecked(settingsRepo.usageWellbeingEnabled.first())
            val fy = settingsRepo.fiscalYearMode.first()
            spFiscal.setSelection(if (fy == SettingsRepository.FY_APR_MAR) 1 else 0)
        }

        switchAutoImport?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressAutoImportCallback) return@setOnCheckedChangeListener
            if (isChecked) {
                enableAutoImportWithPermissions()
            } else {
                persistAutoImport(false)
            }
        }

        switchUsageWellbeing?.setOnCheckedChangeListener { _, isChecked ->
            if (suppressUsageCallback) return@setOnCheckedChangeListener
            viewLifecycleOwner.lifecycleScope.launch {
                settingsRepo.setUsageWellbeingEnabled(isChecked)
                setUsageWellbeingChecked(isChecked)
            }
        }

        view.findViewById<Button>(R.id.btn_sms_history_backfill).setOnClickListener {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) runHistoryBackfill()
            else requestReadForBackfill.launch(Manifest.permission.READ_SMS)
        }

        view.findViewById<Button>(R.id.btn_save_settings).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val selected = SettingsRepository.CURRENCY_OPTIONS
                    .getOrNull(spCurrency.selectedItemPosition)
                    ?.second ?: "₹"
                settingsRepo.setCurrencySymbol(selected)
                settingsRepo.setDateFormat(etDateFormat.text.toString())
                settingsRepo.setFiscalYearMode(
                    if (spFiscal.selectedItemPosition == 1) SettingsRepository.FY_APR_MAR
                    else SettingsRepository.FY_CALENDAR
                )
                Toast.makeText(requireContext(), "Settings saved!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun runHistoryBackfill() {
        val tv = view?.findViewById<TextView>(R.id.tv_sms_backfill_status) ?: return
        val btn = view?.findViewById<Button>(R.id.btn_sms_history_backfill)
        btn?.isEnabled = false
        tv.setText(R.string.settings_sms_history_running)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                SmsHistoryBackfill.run(requireContext()) { progress ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                        tv.text = progress.message
                    }
                }
            }
            tv.text = result.message
            btn?.isEnabled = true
            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun enableAutoImportWithPermissions() {
        val needReceive = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECEIVE_SMS
        ) != PackageManager.PERMISSION_GRANTED
        val needRead = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_SMS
        ) != PackageManager.PERMISSION_GRANTED
        val needNotify = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

        if (!needReceive && !needRead && !needNotify) {
            persistAutoImport(true)
            return
        }

        val perms = mutableListOf<String>()
        if (needReceive) perms += Manifest.permission.RECEIVE_SMS
        if (needRead) perms += Manifest.permission.READ_SMS
        if (needNotify) perms += Manifest.permission.POST_NOTIFICATIONS
        requestSmsPermissions.launch(perms.toTypedArray())
    }

    private fun persistAutoImport(enabled: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsRepo.setAutoImportSmsEnabled(enabled)
            setAutoImportChecked(enabled)
            if (enabled) {
                Toast.makeText(
                    requireContext(),
                    R.string.settings_auto_import_enabled,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setAutoImportChecked(checked: Boolean) {
        val sw = switchAutoImport ?: return
        suppressAutoImportCallback = true
        sw.isChecked = checked
        suppressAutoImportCallback = false
    }

    private fun setUsageWellbeingChecked(checked: Boolean) {
        val sw = switchUsageWellbeing ?: return
        suppressUsageCallback = true
        sw.isChecked = checked
        suppressUsageCallback = false
    }
}
