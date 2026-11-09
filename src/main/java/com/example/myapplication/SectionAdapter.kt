package com.example.myapplication


import android.graphics.Color
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SectionAdapter(private val list: List<SectionItem>) : RecyclerView.Adapter<SectionAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val rank: TextView = v.findViewById(R.id.tvSectionRank)
        val name: TextView = v.findViewById(R.id.tvSectionName)
        val hours: TextView = v.findViewById(R.id.tvSectionHours)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_section, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]

        // TROPHY LOGIC FOR SECTION
        when (position) {
            0 -> { holder.rank.text = "🏆"; holder.rank.textSize = 22f }
            1 -> { holder.rank.text = "🥈"; holder.rank.textSize = 22f }
            2 -> { holder.rank.text = "🥉"; holder.rank.textSize = 22f }
            else -> {
                holder.rank.text = "#${position + 1}"
                holder.rank.textSize = 16f
                holder.rank.setTextColor(Color.BLACK)
            }
        }

        holder.name.text = item.sectionName
        holder.hours.text = "${String.format("%.1f", item.totalHours)} hrs"
    }

    override fun getItemCount() = list.size
}