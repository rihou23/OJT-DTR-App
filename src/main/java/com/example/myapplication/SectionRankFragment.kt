package com.example.myapplication



import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.*
import com.google.firebase.firestore.FirebaseFirestore



class SectionRankFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rv = RecyclerView(requireContext())
        rv.layoutManager = LinearLayoutManager(context)

        db.collection("user").get().addOnSuccessListener { users ->
            val uToS = mutableMapOf<String, String>()
            for (d in users) uToS[d.id] = d.getString("section") ?: "Unknown"

            db.collectionGroup("records").get().addOnSuccessListener { records ->
                val sMap = mutableMapOf<String, Double>()
                for (r in records) {
                    val uid = r.reference.parent.parent?.id ?: continue
                    val sec = uToS[uid] ?: continue
                    sMap[sec] = (sMap[sec] ?: 0.0) + (r.getDouble("total_hours") ?: 0.0)
                }
                val list = sMap.map { SectionItem(it.key, it.value) }.sortedByDescending { it.totalHours }
                rv.adapter = SectionAdapter(list)
            }
        }
        return rv
    }
}