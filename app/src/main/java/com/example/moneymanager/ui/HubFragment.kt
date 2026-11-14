package com.example.moneymanager.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.moneymanager.R
import com.example.moneymanager.flavor.FlavorFeatures
import com.google.android.material.card.MaterialCardView

class HubFragment : Fragment(R.layout.fragment_hub) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialCardView>(R.id.card_money).setOnClickListener {
            findNavController().navigate(R.id.action_hub_to_money)
        }
        view.findViewById<MaterialCardView>(R.id.card_time).setOnClickListener {
            findNavController().navigate(R.id.action_hub_to_time)
        }
        view.findViewById<MaterialCardView>(R.id.card_usage).setOnClickListener {
            findNavController().navigate(R.id.action_hub_to_usage)
        }
        view.findViewById<MaterialCardView>(R.id.card_listener).setOnClickListener {
            findNavController().navigate(R.id.action_hub_to_listener)
        }
        val tunnel = FlavorFeatures.tunnel
        view.findViewById<MaterialCardView>(R.id.card_tunnel).apply {
            visibility = if (tunnel != null) View.VISIBLE else View.GONE
            setOnClickListener { tunnel?.open(requireContext()) }
        }
    }
}
