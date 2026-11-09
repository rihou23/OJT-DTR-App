package com.example.myapplication

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.*
import com.google.firebase.firestore.FirebaseFirestore

class StudentRankFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: StudentAdapter
    private var allStudents = mutableListOf<StudentItem>()
    private var myDept = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_student_rank, container, false)
        val rv = v.findViewById<RecyclerView>(R.id.rvStudents)
        val tvMyRank = v.findViewById<TextView>(R.id.tvMyRankHeader)
        val myId = arguments?.getString("MY_ID") ?: ""

        rv.layoutManager = LinearLayoutManager(context)
        adapter = StudentAdapter(emptyList())
        rv.adapter = adapter

        db.collection("user").document(myId).get().addOnSuccessListener { myDoc ->
            val section = myDoc.getString("section") ?: ""
            myDept = if (section.contains("BSIT")) "BSIT" else "BSBA"
            v.findViewById<Button>(R.id.btnFilterDept).text = myDept

            db.collection("user").get().addOnSuccessListener { users ->
                val userMap = mutableMapOf<String, StudentItem>()
                for (d in users) {
                    val s = d.getString("section") ?: ""
                    val dpt = if (s.contains("BSIT")) "BSIT" else "BSBA"
                    userMap[d.id] = StudentItem(d.id, d.getString("firstname") ?: "", d.getString("lastname") ?: "", s, d.getString("profile_image_url") ?: "", 0.0, dpt == myDept)
                }

                db.collectionGroup("records").get().addOnSuccessListener { records ->
                    for (r in records) {
                        val uid = r.reference.parent.parent?.id ?: continue
                        if (userMap.containsKey(uid)) userMap[uid]!!.totalHours += r.getDouble("total_hours") ?: 0.0
                    }
                    allStudents = userMap.values.sortedByDescending { it.totalHours }.toMutableList()
                    adapter.updateList(allStudents)

                    val rank = allStudents.indexOfFirst { it.userId == myId }
                    tvMyRank.text = "Your Overall Rank: #${if (rank != -1) rank + 1 else "N/A"}"
                }
            }
        }

        v.findViewById<Button>(R.id.btnFilterAll).setOnClickListener { adapter.updateList(allStudents) }
        v.findViewById<Button>(R.id.btnFilterDept).setOnClickListener {
            adapter.updateList(allStudents.filter { it.section.contains(myDept) })
        }
        return v
    }
}