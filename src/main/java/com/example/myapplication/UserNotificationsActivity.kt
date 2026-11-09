package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class UserNotificationsActivity : AppCompatActivity() {

    private lateinit var rvNotifications: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var btnBack: ImageView

    private val db = FirebaseFirestore.getInstance()
    private var notificationList = mutableListOf<Notification>()
    private lateinit var uid: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_notifications)

        uid = intent.getStringExtra("user_id") ?: ""

        rvNotifications = findViewById(R.id.rvNotifications)
        progressBar = findViewById(R.id.progressBar)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        btnBack = findViewById(R.id.btnBack)

        rvNotifications.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener { finish() }

        fetchNotifications()
    }

    private fun fetchNotifications() {
        progressBar.visibility = View.VISIBLE

        db.collection("notifications")
            .whereEqualTo("user_id", uid)
            .orderBy("created_at", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                progressBar.visibility = View.GONE
                if (e != null || snapshots == null) return@addSnapshotListener

                notificationList.clear()
                for (doc in snapshots) {
                    val notif = doc.toObject(Notification::class.java).copy(id = doc.id)
                    notificationList.add(notif)

                    // Automatically mark as read when user sees them
                    if (notif.status == 0) {
                        db.collection("notifications").document(doc.id).update("status", 1)
                    }
                }

                if (notificationList.isEmpty()) {
                    layoutEmpty.visibility = View.VISIBLE
                    rvNotifications.visibility = View.GONE
                } else {
                    layoutEmpty.visibility = View.GONE
                    rvNotifications.visibility = View.VISIBLE
                    rvNotifications.adapter = NotificationAdapter(notificationList)
                }
            }
    }

    // ADAPTER CLASS
    inner class NotificationAdapter(private val list: List<Notification>) :
        RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvFrom: TextView = v.findViewById(R.id.tvFrom)
            val tvDesc: TextView = v.findViewById(R.id.tvDescription)
            val tvTime: TextView = v.findViewById(R.id.tvTimestamp)
            val indicator: View = v.findViewById(R.id.viewStatusIndicator)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tvFrom.text = item.from
            holder.tvDesc.text = item.description

            // Format Timestamp
            val date = item.created_at?.toDate()
            if (date != null) {
                val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                holder.tvTime.text = sdf.format(date)
            }

            // Show indicator only for unread (though we mark them read immediately in this logic)
            holder.indicator.visibility = if (item.status == 0) View.VISIBLE else View.INVISIBLE
        }

        override fun getItemCount() = list.size
    }
}