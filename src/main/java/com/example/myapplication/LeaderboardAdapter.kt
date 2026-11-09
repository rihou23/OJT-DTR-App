package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class LeaderboardAdapter(private var list: List<LeaderboardItem>) :
    RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRank: TextView = view.findViewById(R.id.tvRank)
        val tvFullName: TextView = view.findViewById(R.id.tvFullName)
        val tvSection: TextView = view.findViewById(R.id.tvSection)
        val tvTotalHours: TextView = view.findViewById(R.id.tvTotalHours)
        val imgProfile: ImageView = view.findViewById(R.id.imgProfile)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_leaderboard, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvRank.text = "#${position + 1}"
        holder.tvFullName.text = "${item.firstName} ${item.lastName}"
        holder.tvSection.text = item.section
        holder.tvTotalHours.text = "${String.format("%.1f", item.totalHours)} hrs"

        Glide.with(holder.itemView.context)
            .load(item.profileImageUrl)
            .placeholder(android.R.drawable.ic_menu_report_image)
            .error(android.R.drawable.ic_menu_gallery)
            .into(holder.imgProfile)
    }

    override fun getItemCount() = list.size

    fun updateList(newList: List<LeaderboardItem>) {
        this.list = newList
        notifyDataSetChanged()
    }
}