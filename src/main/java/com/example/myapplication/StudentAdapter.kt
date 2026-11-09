package com.example.myapplication

import android.graphics.Color
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class StudentAdapter(private var list: List<StudentItem>) : RecyclerView.Adapter<StudentAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvRank: TextView = v.findViewById(R.id.tvRank)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvSection: TextView = v.findViewById(R.id.tvSection)
        val tvHours: TextView = v.findViewById(R.id.tvHours)
        val imgProfile: ImageView = v.findViewById(R.id.imgProfile)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_student, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]

        // Trophy Logic
        when (position) {
            0 -> { holder.tvRank.text = "🏆"; holder.tvRank.setTextColor(Color.parseColor("#FFD700")) }
            1 -> { holder.tvRank.text = "🥈"; holder.tvRank.setTextColor(Color.parseColor("#C0C0C0")) }
            2 -> { holder.tvRank.text = "🥉"; holder.tvRank.setTextColor(Color.parseColor("#CD7F32")) }
            else -> { holder.tvRank.text = "${position + 1}"; holder.tvRank.setTextColor(Color.BLACK) }
        }

        // Privacy Logic
        val displayName = if (item.isSameDept) {
            "${item.firstname} ${item.lastname}"
        } else {
            "${item.firstname} [Hidden]"
        }

        holder.tvName.text = displayName

        // Adjust text size based on name length
        holder.tvName.textSize = when {
            displayName.length > 25 -> 12f  // Very long name
            displayName.length > 20 -> 13f  // Long name
            displayName.length > 15 -> 14f  // Medium name
            else -> 15f                      // Normal size
        }

        holder.tvSection.text = item.section
        holder.tvHours.text = "${String.format("%.1f", item.totalHours)} hrs"

        Glide.with(holder.itemView.context).load(item.profile_image_url)
            .placeholder(android.R.drawable.ic_menu_gallery).into(holder.imgProfile)
    }

    override fun getItemCount() = list.size
    fun updateList(newList: List<StudentItem>) { this.list = newList; notifyDataSetChanged() }
}