package com.example.moneymanager.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R
import com.example.moneymanager.listener.ListenerPolicy
import com.example.moneymanager.listener.ListenerService
import com.example.moneymanager.listener.ListenerSessionEntity
import com.example.moneymanager.listener.ListenerState
import com.example.moneymanager.listener.ListenerStopReason
import com.example.moneymanager.viewmodel.ListenerViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Listener screen. Start always runs here (visible app), in this order:
 * consent (once) → RECORD_AUDIO → POST_NOTIFICATIONS (13+, optional) →
 * free-space check → start the foreground service.
 */
class ListenerFragment : Fragment(R.layout.fragment_listener) {

    private val viewModel: ListenerViewModel by viewModels()

    private var statusText: TextView? = null
    private var chrono: Chronometer? = null
    private var toggle: MaterialButton? = null
    private var titleInput: TextInputEditText? = null
    private var freeText: TextView? = null

    private val requestMic = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ensureNotificationsThenStart()
        } else {
            Toast.makeText(requireContext(), R.string.listener_err_mic_permission, Toast.LENGTH_LONG).show()
        }
    }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(requireContext(), R.string.listener_notif_permission_hint, Toast.LENGTH_LONG).show()
        }
        startIfSpace()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statusText = view.findViewById(R.id.tv_listener_status)
        chrono = view.findViewById(R.id.chrono_listener)
        toggle = view.findViewById(R.id.btn_listener_toggle)
        titleInput = view.findViewById(R.id.et_listener_title)
        freeText = view.findViewById(R.id.tv_listener_free)
        val empty = view.findViewById<TextView>(R.id.tv_listener_empty)

        val adapter = Adapter(onClick = { play(it) }, onLongClick = { showActions(it) })
        view.findViewById<RecyclerView>(R.id.rv_listener_sessions).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        toggle?.setOnClickListener {
            if (ListenerState.isBusy) ListenerService.stop(requireContext()) else beginStartFlow()
        }

        viewModel.status.observe(viewLifecycleOwner) { render(it) }
        viewModel.sessions.observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.lastError.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                ListenerState.consumeError()
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshFreeSpace()
        if (!ListenerState.isBusy) viewModel.reconcile()
        // QS tile → MainActivity → here. Consumed once, works for new or existing instance.
        if (pendingAutoStart.getAndSet(false) && !ListenerState.isBusy) beginStartFlow()
    }

    override fun onDestroyView() {
        chrono?.stop()
        statusText = null
        chrono = null
        toggle = null
        titleInput = null
        freeText = null
        super.onDestroyView()
    }

    private fun render(status: ListenerState.Status) {
        val btn = toggle ?: return
        val c = chrono ?: return
        when (status) {
            is ListenerState.Status.Recording -> {
                btn.setText(R.string.listener_stop)
                btn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.accent_red))
                btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                btn.iconTint = ContextCompat.getColorStateList(requireContext(), R.color.white)
                statusText?.text = getString(R.string.listener_status_recording, status.segmentIndex)
                statusText?.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_red))
                c.base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - status.startedAt)
                c.visibility = View.VISIBLE
                c.start()
                titleInput?.isEnabled = false
            }
            is ListenerState.Status.Starting -> {
                btn.setText(R.string.listener_stop)
                titleInput?.isEnabled = false
            }
            ListenerState.Status.Idle -> {
                btn.setText(R.string.listener_start)
                btn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_yellow))
                btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.button_on_yellow))
                btn.iconTint = ContextCompat.getColorStateList(requireContext(), R.color.button_on_yellow)
                statusText?.setText(R.string.listener_status_idle)
                statusText?.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                c.stop()
                c.visibility = View.GONE
                titleInput?.isEnabled = true
                refreshFreeSpace()
            }
        }
    }

    private fun refreshFreeSpace() {
        viewLifecycleOwner.lifecycleScope.launch {
            val free = viewModel.freeBytes()
            val hours = (free / BYTES_PER_HOUR).toInt()
            freeText?.text = getString(R.string.listener_free_space, ListenerPolicy.formatBytes(free), hours)
        }
    }

    // ---- start flow ----

    private fun beginStartFlow() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.isConsentAccepted()) ensureMicThenContinue() else showConsent()
        }
    }

    private fun showConsent() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.listener_consent_title)
            .setMessage(R.string.listener_consent_body)
            .setPositiveButton(R.string.listener_consent_ok) { _, _ ->
                viewModel.acceptConsent()
                ensureMicThenContinue()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun ensureMicThenContinue() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestMic.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        ensureNotificationsThenStart()
    }

    private fun ensureNotificationsThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED &&
            !askedNotificationsThisProcess
        ) {
            askedNotificationsThisProcess = true
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startIfSpace()
    }

    private fun startIfSpace() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!ListenerPolicy.canStart(viewModel.freeBytes())) {
                Toast.makeText(requireContext(), R.string.listener_err_low_storage_start, Toast.LENGTH_LONG).show()
                return@launch
            }
            ListenerService.start(requireContext(), titleInput?.text?.toString())
            titleInput?.setText("")
        }
    }

    // ---- session actions ----

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)

    private fun play(session: ListenerSessionEntity) {
        if (session.state == ListenerSessionEntity.STATE_RECORDING) {
            Toast.makeText(requireContext(), R.string.listener_busy_recording, Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val segs = viewModel.segments(session)
            when {
                segs.isEmpty() ->
                    Toast.makeText(requireContext(), R.string.listener_no_audio, Toast.LENGTH_SHORT).show()
                segs.size == 1 -> openAudio(segs[0])
                else -> {
                    val labels = segs.indices.map { getString(R.string.listener_pick_part, it + 1) }
                    AlertDialog.Builder(requireContext())
                        .setTitle(session.title)
                        .setItems(labels.toTypedArray()) { _, which -> openAudio(segs[which]) }
                        .show()
                }
            }
        }
    }

    private fun openAudio(file: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(file), "audio/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.listener_no_player, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showActions(session: ListenerSessionEntity) {
        val items = arrayOf(
            getString(R.string.listener_action_play),
            getString(R.string.listener_action_rename),
            getString(R.string.listener_action_share),
            getString(R.string.listener_action_delete)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(session.title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> play(session)
                    1 -> rename(session)
                    2 -> share(session)
                    3 -> confirmDelete(session)
                }
            }
            .show()
    }

    private fun rename(session: ListenerSessionEntity) {
        val input = EditText(requireContext()).apply {
            setText(session.title)
            setSelection(text.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.listener_action_rename)
            .setView(input)
            .setPositiveButton(R.string.save_transaction) { _, _ ->
                viewModel.rename(session.id, input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun share(session: ListenerSessionEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val segs = viewModel.segments(session)
            if (segs.isEmpty()) {
                Toast.makeText(requireContext(), R.string.listener_no_audio, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val uris = ArrayList(segs.map { uriFor(it) })
            val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/mp4"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                clipData = ClipData.newUri(requireContext().contentResolver, session.title, uris[0]).also { clip ->
                    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, session.title))
        }
    }

    private fun confirmDelete(session: ListenerSessionEntity) {
        if (ListenerState.ownsSession(session)) {
            Toast.makeText(requireContext(), R.string.listener_busy_recording, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.listener_action_delete)
            .setMessage(getString(R.string.listener_delete_confirm, session.title))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.delete(session.id) { ok ->
                    if (!ok && isAdded) {
                        Toast.makeText(requireContext(), R.string.listener_delete_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private class Adapter(
        private val onClick: (ListenerSessionEntity) -> Unit,
        private val onLongClick: (ListenerSessionEntity) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {
        private var items: List<ListenerSessionEntity> = emptyList()
        private val df = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

        fun submit(list: List<ListenerSessionEntity>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tv_session_title)
            val duration: TextView = v.findViewById(R.id.tv_session_duration)
            val sub: TextView = v.findViewById(R.id.tv_session_sub)
            val state: TextView = v.findViewById(R.id.tv_session_state)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_listener_session, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val ctx = holder.itemView.context
            holder.title.text = item.title
            holder.duration.text = ListenerPolicy.formatElapsed(item.durationMs)
            holder.sub.text = ctx.getString(
                R.string.listener_item_sub,
                df.format(Date(item.startedAt)),
                ListenerPolicy.formatBytes(item.bytes),
                item.segmentCount
            )
            val stateText = when {
                item.state == ListenerSessionEntity.STATE_RECORDING -> ctx.getString(R.string.listener_state_recording)
                item.state == ListenerSessionEntity.STATE_INTERRUPTED -> ctx.getString(R.string.listener_state_interrupted)
                item.notes == ListenerStopReason.MAX_DURATION.name -> ctx.getString(R.string.listener_state_stopped_max)
                item.notes == ListenerStopReason.LOW_STORAGE.name -> ctx.getString(R.string.listener_state_stopped_storage)
                else -> null
            }
            holder.state.text = stateText
            holder.state.visibility = if (stateText == null) View.GONE else View.VISIBLE
            holder.itemView.setOnClickListener { onClick(item) }
            holder.itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        /** Set by MainActivity when the QS tile asks to start; consumed in onResume. */
        val pendingAutoStart = AtomicBoolean(false)

        private var askedNotificationsThisProcess = false

        // 32 kbps AAC ≈ 4 KB/s ≈ 14 MB/h
        private const val BYTES_PER_HOUR = ListenerPolicy.BIT_RATE_BPS / 8L * 3600L
    }
}
