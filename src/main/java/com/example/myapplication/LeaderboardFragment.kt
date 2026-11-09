package com.example.myapplication



import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.*
import com.google.firebase.firestore.FirebaseFirestore



class LeaderboardFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var rv: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rv = RecyclerView(requireContext())
        rv.layoutManager = LinearLayoutManager(context)
        rv.setBackgroundColor(android.graphics.Color.WHITE)

        val type = arguments?.getString("TYPE") ?: "STUDENT"
        val myId = arguments?.getString("MY_ID") ?: ""

        loadData(type, myId)
        return rv
    }

    private fun loadData(type: String, myId: String) {
        // 1. Get current user's department to handle privacy masking
        db.collection("user").document(myId).get().addOnSuccessListener { myDoc ->
            val myFullSection = myDoc.getString("section") ?: ""
            val myDept = if (myFullSection.contains("BSIT")) "BSIT" else "BSBA"

            // 2. Get all users
            db.collection("user").get().addOnSuccessListener { userDocs ->
                val userMap = mutableMapOf<String, StudentItem>()

                for (doc in userDocs) {
                    val sec = doc.getString("section") ?: "N/A"
                    val dept = if (sec.contains("BSIT")) "BSIT" else "BSBA"

                    userMap[doc.id] = StudentItem(
                        userId = doc.id,
                        firstname = doc.getString("firstname") ?: "",
                        lastname = doc.getString("lastname") ?: "",
                        section = sec,
                        profile_image_url = doc.getString("profile_image_url") ?: "",
                        isSameDept = (dept == myDept)
                    )
                }

                // 3. Get hours from 'records' subcollections
                db.collectionGroup("records").get().addOnSuccessListener { records ->
                    val sectionTotals = mutableMapOf<String, Double>()

                    for (r in records) {
                        val uid = r.reference.parent.parent?.id ?: continue
                        val h = r.getDouble("total_hours") ?: 0.0

                        if (userMap.containsKey(uid)) {
                            userMap[uid]!!.totalHours += h
                            val sName = userMap[uid]!!.section
                            sectionTotals[sName] = (sectionTotals[sName] ?: 0.0) + h
                        }
                    }

                    if (type == "SECTION") {
                        // All sections: BSBA 4-1, BSBA 4-2, BSIT 4-1, BSIT 4-2
                        val list = sectionTotals.map { SectionItem(it.key, it.value) }
                            .sortedByDescending { it.totalHours }
                        rv.adapter = SectionAdapter(list)
                    } else {
                        // Global Student Ranking
                        val list = userMap.values.sortedByDescending { it.totalHours }.toList()
                        rv.adapter = StudentAdapter(list)
                    }
                }
            }
        }
    }
}