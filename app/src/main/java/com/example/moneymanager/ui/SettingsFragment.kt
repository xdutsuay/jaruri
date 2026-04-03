package com.example.moneymanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.moneymanager.R
import com.example.moneymanager.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private lateinit var settingsRepo: SettingsRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        settingsRepo = SettingsRepository(requireContext())
        
        val etCurrency = view.findViewById<EditText>(R.id.et_currency)
        val etDateFormat = view.findViewById<EditText>(R.id.et_date_format)
        val switchSample = view.findViewById<Switch>(R.id.switch_sample_data)
        
        viewLifecycleOwner.lifecycleScope.launch {
            etCurrency.setText(settingsRepo.currencySymbol.first())
            etDateFormat.setText(settingsRepo.dateFormat.first())
            switchSample.isChecked = settingsRepo.sampleDataEnabled.first()
        }
        
        view.findViewById<Button>(R.id.btn_save_settings).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                settingsRepo.setCurrencySymbol(etCurrency.text.toString())
                settingsRepo.setDateFormat(etDateFormat.text.toString())
                settingsRepo.setSampleDataEnabled(switchSample.isChecked)
                Toast.makeText(requireContext(), "Settings saved!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
