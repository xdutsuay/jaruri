package com.example.moneymanager.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import com.example.moneymanager.R
import com.example.moneymanager.listener.ListenerPolicy
import com.example.moneymanager.listener.ListenerSessionEntity
import com.example.moneymanager.listener.transcribe.ChunkPlayer
import com.example.moneymanager.listener.transcribe.NoteLine
import com.example.moneymanager.listener.transcribe.SessionNotes
import com.example.moneymanager.listener.transcribe.TranscriptExporter
import com.example.moneymanager.listener.transcribe.TranscriptSegmentEntity
import com.example.moneymanager.viewmodel.ListenerSessionViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One Listener session: in-app player across chunks, transcript (tap a line to
 * play from it), transcription controls, export, and keyword highlights.
 * Args: sessionId, startMs (seek there once audio is ready; -1 = don't).
 */
class ListenerSessionFragment : Fragment(R.layout.fragment_listener_session) {

    private val viewModel: ListenerSessionViewModel by viewModels()
    private var player: ChunkPlayer? = null
    private var pendingSeekMs: Long = -1
    private var current: ListenerSessionEntity? = null
    private var activeWork: WorkInfo? = null
    private var userSeeking = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var adapter: SegmentAdapter

    private var playBtn: ImageButton? = null
    private var seekBar: SeekBar? = null
    private var positionText: TextView? = null

    private val tick = object : Runnable {
        override fun run() {
            renderPosition()
            handler.postDelayed(this, 500)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        viewModel.bind(args.getLong(ARG_SESSION_ID, -1))
        if (savedInstanceState == null) pendingSeekMs = args.getLong(ARG_START_MS, -1)

        playBtn = view.findViewById(R.id.btn_ls_play)
        seekBar = view.findViewById(R.id.sb_ls_position)
        positionText = view.findViewById(R.id.tv_ls_position)
        val title = view.findViewById<TextView>(R.id.tv_ls_title)
        val meta = view.findViewById<TextView>(R.id.tv_ls_meta)
        val trStatus = view.findViewById<TextView>(R.id.tv_ls_tr_status)
        val progress = view.findViewById<ProgressBar>(R.id.pb_ls_tr)
        val trButton = view.findViewById<Button>(R.id.btn_ls_transcribe)
        val exportBtn = view.findViewById<Button>(R.id.btn_ls_export)
        val empty = view.findViewById<TextView>(R.id.tv_ls_empty)

        adapter = SegmentAdapter { ms -> playFrom(ms) }
        view.findViewById<RecyclerView>(R.id.rv_ls_segments).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ListenerSessionFragment.adapter
        }

        playBtn?.setOnClickListener { player?.toggle() }
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) positionText?.text = positionLabel(value * 1000L)
            }
            override fun onStartTrackingTouch(sb: SeekBar) { userSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                userSeeking = false
                player?.let { it.seekTo(sb.progress * 1000L, play = it.isPlaying) }
            }
        })
        exportBtn.setOnClickListener { chooseExport() }

        val df = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        viewModel.session.observe(viewLifecycleOwner) { s ->
            if (s == null) {
                // Deleted elsewhere.
                title.text = ""
                return@observe
            }
            val first = current == null
            current = s
            title.text = s.title
            meta.text = getString(
                R.string.listener_session_meta,
                df.format(Date(s.startedAt)),
                ListenerPolicy.formatElapsed(s.durationMs),
                ListenerPolicy.formatBytes(s.bytes)
            )
            if (first) loadAudio(s)
            renderTranscription(trStatus, progress, trButton)
        }
        viewModel.work.observe(viewLifecycleOwner) { infos ->
            activeWork = infos.firstOrNull { !it.state.isFinished }
            renderTranscription(trStatus, progress, trButton)
        }
        viewModel.segments.observe(viewLifecycleOwner) { segs ->
            adapter.submitSegments(segs)
            empty.visibility = if (segs.isEmpty()) View.VISIBLE else View.GONE
            exportBtn.isEnabled = segs.isNotEmpty()
        }
        viewModel.notes.observe(viewLifecycleOwner) { adapter.submitNotes(it) }
    }

    override fun onStart() {
        super.onStart()
        handler.post(tick)
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
        player?.pause()
        super.onStop()
    }

    override fun onDestroyView() {
        player?.release()
        player = null
        playBtn = null
        seekBar = null
        positionText = null
        super.onDestroyView()
    }

    // ---- audio ----

    private fun loadAudio(s: ListenerSessionEntity) {
        if (s.state == ListenerSessionEntity.STATE_RECORDING) {
            playBtn?.isEnabled = false
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val (files, durations) = viewModel.audio(s)
            if (view == null) return@launch
            if (files.isEmpty()) {
                playBtn?.isEnabled = false
                return@launch
            }
            val p = ChunkPlayer(files, durations, object : ChunkPlayer.Listener {
                override fun onPlayingChanged(playing: Boolean) {
                    playBtn?.setImageResource(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                    playBtn?.contentDescription = getString(if (playing) R.string.listener_player_pause else R.string.listener_player_play)
                }
                override fun onError() {
                    context?.let { Toast.makeText(it, R.string.listener_player_error, Toast.LENGTH_SHORT).show() }
                }
            })
            player = p
            seekBar?.max = (p.durationMs / 1000).toInt()
            if (pendingSeekMs >= 0) {
                p.seekTo(pendingSeekMs, play = true)
                scrollTo(pendingSeekMs)
                pendingSeekMs = -1
            }
            renderPosition()
        }
    }

    private fun playFrom(ms: Long) {
        val p = player
        if (p == null) {
            pendingSeekMs = ms
            return
        }
        p.seekTo(ms, play = true)
        renderPosition()
    }

    private fun scrollTo(ms: Long) {
        val index = adapter.positionFor(ms)
        if (index >= 0) view?.findViewById<RecyclerView>(R.id.rv_ls_segments)?.scrollToPosition(index)
    }

    private fun renderPosition() {
        val p = player ?: return
        if (userSeeking) return
        val pos = p.positionMs
        seekBar?.progress = (pos / 1000).toInt()
        positionText?.text = positionLabel(pos)
    }

    private fun positionLabel(ms: Long): String =
        TranscriptExporter.timestamp(ms) + " / " + TranscriptExporter.timestamp(player?.durationMs ?: 0)

    // ---- transcription ----

    private fun renderTranscription(status: TextView, progress: ProgressBar, button: Button) {
        val s = current ?: return
        val work = activeWork
        val running = work != null
        val done = s.transcribedChunks
        val total = s.segmentCount
        progress.visibility = View.GONE
        button.isEnabled = s.state != ListenerSessionEntity.STATE_RECORDING
        when {
            running -> {
                val pct = work!!.progress.getInt("progress", -1)
                if (work.state == WorkInfo.State.RUNNING && pct >= 0) {
                    status.text = getString(R.string.listener_tr_state_running, pct)
                    progress.isIndeterminate = false
                    progress.progress = pct
                } else {
                    status.setText(R.string.listener_tr_state_queued)
                    progress.isIndeterminate = true
                }
                progress.visibility = View.VISIBLE
                button.setText(R.string.listener_tr_cancel)
                button.setOnClickListener { viewModel.cancel() }
            }
            s.transcriptState == ListenerSessionEntity.TRANSCRIPT_DONE -> {
                status.setText(R.string.listener_tr_state_done)
                button.setText(R.string.listener_tr_again)
                button.setOnClickListener { confirmRestart() }
            }
            s.transcriptState == ListenerSessionEntity.TRANSCRIPT_FAILED -> {
                status.text = getString(R.string.listener_tr_state_failed, s.transcriptError ?: "")
                button.setText(if (done > 0) R.string.listener_tr_resume else R.string.listener_tr_transcribe)
                button.setOnClickListener { startTranscription() }
            }
            s.transcriptState == ListenerSessionEntity.TRANSCRIPT_CANCELLED -> {
                status.text = getString(R.string.listener_tr_state_cancelled, done, total)
                button.setText(R.string.listener_tr_resume)
                button.setOnClickListener { startTranscription() }
            }
            s.transcriptState == ListenerSessionEntity.TRANSCRIPT_QUEUED ||
                s.transcriptState == ListenerSessionEntity.TRANSCRIPT_RUNNING -> {
                // State says busy but no job exists (e.g. cancelled from the notification on old Android).
                status.text = getString(R.string.listener_tr_state_paused, done, total)
                button.setText(R.string.listener_tr_resume)
                button.setOnClickListener { startTranscription() }
            }
            else -> {
                status.setText(if (viewModel.hasModel) R.string.listener_tr_state_none else R.string.listener_stt_no_model)
                button.setText(R.string.listener_tr_transcribe)
                button.setOnClickListener { startTranscription() }
            }
        }
    }

    private fun startTranscription() {
        if (!viewModel.hasModel) {
            Toast.makeText(requireContext(), R.string.listener_stt_no_model, Toast.LENGTH_LONG).show()
            return
        }
        viewModel.transcribe()
    }

    private fun confirmRestart() {
        if (!viewModel.hasModel) {
            Toast.makeText(requireContext(), R.string.listener_stt_no_model, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.listener_tr_again_confirm)
            .setPositiveButton(R.string.listener_tr_again) { _, _ -> viewModel.restart() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- export ----

    private fun chooseExport() {
        val formats = arrayOf(TranscriptExporter.Format.MARKDOWN, TranscriptExporter.Format.PLAIN)
        val labels = arrayOf(getString(R.string.listener_export_markdown), getString(R.string.listener_export_text))
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.listener_export)
            .setItems(labels) { _, which -> export(formats[which]) }
            .show()
    }

    private fun export(format: TranscriptExporter.Format) {
        viewLifecycleOwner.lifecycleScope.launch {
            val file = viewModel.export(format)
            if (file == null) {
                Toast.makeText(requireContext(), R.string.listener_export_nothing, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, current?.title ?: file.nameWithoutExtension)
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(Intent.createChooser(send, getString(R.string.listener_export)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.listener_export_nothing, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- list ----

    /** Optional notes card (position 0) followed by transcript lines. */
    private class SegmentAdapter(private val onSeek: (Long) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var segments: List<TranscriptSegmentEntity> = emptyList()
        private var notes: SessionNotes? = null
        private val hasNotes get() = notes?.isEmpty == false

        fun submitSegments(list: List<TranscriptSegmentEntity>) {
            segments = list
            notifyDataSetChanged()
        }

        fun submitNotes(n: SessionNotes) {
            notes = n
            notifyDataSetChanged()
        }

        fun positionFor(ms: Long): Int {
            val i = segments.indexOfLast { it.startMs <= ms }.coerceAtLeast(0)
            return if (segments.isEmpty()) -1 else i + if (hasNotes) 1 else 0
        }

        override fun getItemViewType(position: Int) = if (hasNotes && position == 0) TYPE_NOTES else TYPE_LINE
        override fun getItemCount() = segments.size + if (hasNotes) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            val layout = if (viewType == TYPE_NOTES) R.layout.item_transcript_notes else R.layout.item_transcript_segment
            return object : RecyclerView.ViewHolder(inf.inflate(layout, parent, false)) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val v = holder.itemView
            if (getItemViewType(position) == TYPE_NOTES) {
                bindNotes(v.findViewById(R.id.ll_notes_lines), notes!!)
                return
            }
            val seg = segments[position - if (hasNotes) 1 else 0]
            v.findViewById<TextView>(R.id.tv_seg_time).text = TranscriptExporter.timestamp(seg.startMs)
            v.findViewById<TextView>(R.id.tv_seg_text).text = seg.text
            v.setOnClickListener { onSeek(seg.startMs) }
        }

        private fun bindNotes(container: LinearLayout, n: SessionNotes) {
            container.removeAllViews()
            val ctx = container.context
            fun section(titleRes: Int, lines: List<NoteLine>) {
                if (lines.isEmpty()) return
                container.addView(TextView(ctx).apply {
                    setText(titleRes)
                    setTextColor(ctx.getColor(R.color.text_primary))
                    setPadding(0, 16, 0, 4)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                lines.forEach { line ->
                    container.addView(TextView(ctx).apply {
                        text = "${TranscriptExporter.timestamp(line.startMs)}  ${line.text}"
                        setTextColor(ctx.getColor(R.color.text_primary))
                        setPadding(0, 6, 0, 6)
                        setOnClickListener { onSeek(line.startMs) }
                    })
                }
            }
            section(R.string.listener_notes_actions, n.actionItems)
            section(R.string.listener_notes_key, n.keyLines)
        }

        companion object {
            const val TYPE_NOTES = 0
            const val TYPE_LINE = 1
        }
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
        const val ARG_START_MS = "startMs"
    }
}
