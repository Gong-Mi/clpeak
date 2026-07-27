package kr.clpeak

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kr.clpeak.databinding.FragmentHistoryBinding
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.clearHistory.setOnClickListener {
            BenchmarkHistoryStore(requireContext()).clear()
            render(emptyList())
        }
        render(BenchmarkHistoryStore(requireContext()).load())
    }

    private fun render(runs: List<BenchmarkRun>) {
        binding.historyRows.removeAllViews()
        binding.emptyHistory.visibility = if (runs.isEmpty()) View.VISIBLE else View.GONE
        binding.clearHistory.visibility = if (runs.isEmpty()) View.GONE else View.VISIBLE
        for (run in runs) binding.historyRows.addView(runCard(run))
    }

    private fun runCard(run: BenchmarkRun): View {
        val context = requireContext()
        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 12, 16, 0) }
            radius = 16f
            cardElevation = 0f
            setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant))
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
        }
        val format = DateFormat.getMediumDateFormat(context).format(Date(run.completedAtMs)) + " " +
            DateFormat.getTimeFormat(context).format(Date(run.completedAtMs))
        content.addView(label("$format  ·  ${if (run.exitCode == 0) getString(R.string.history_completed) else getString(R.string.history_exit_code, run.exitCode)}", true))
        content.addView(label(run.backends.joinToString(" · ").ifBlank { getString(R.string.history_no_metrics) }))
        content.addView(label(run.devices.joinToString(" · ")))
        content.addView(label(getString(R.string.history_metric_count, run.entries.count { it.status == "ok" }, run.entries.size)))

        val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val expand = MaterialButton(context, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.history_show_metrics)
            setOnClickListener {
                val expanded = details.visibility != View.VISIBLE
                details.visibility = if (expanded) View.VISIBLE else View.GONE
                text = getString(if (expanded) R.string.history_hide_metrics else R.string.history_show_metrics)
            }
        }
        for (entry in run.entries) {
            val measured = if (entry.status == "ok") String.format(Locale.getDefault(), "%.2f %s", entry.value, entry.unit)
                else "${entry.status}: ${entry.reason}"
            details.addView(label("${entry.backend} · ${entry.test} · ${entry.metric}: $measured"))
        }
        content.addView(expand)
        content.addView(details)
        card.addView(content)
        return card
    }

    private fun label(text: String, title: Boolean = false) = TextView(requireContext()).apply {
        this.text = text
        textSize = if (title) 16f else 14f
        setPadding(0, 3, 0, 3)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
