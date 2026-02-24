package com.example.android_final_project.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android_final_project.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class Spot(val id: String, val price: String, val description: String)

class MySpotsActivity : AppCompatActivity() {

    private lateinit var rvMySpots: RecyclerView
    private val db = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_spots)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage My Spots"
        rvMySpots = findViewById(R.id.rvMySpots)
        rvMySpots.layoutManager = LinearLayoutManager(this)

        loadMySpots()

        val btnBack = findViewById<TextView>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadMySpots() {
        if (currentUser == null) return

        db.collection("spots")
            .whereEqualTo("ownerId", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->
                val tvEmptySpots = findViewById<TextView>(R.id.tvEmptySpots)

                if (documents.isEmpty) {
                    tvEmptySpots.visibility = View.VISIBLE
                    rvMySpots.visibility = View.GONE
                } else {
                    tvEmptySpots.visibility = View.GONE
                    rvMySpots.visibility = View.VISIBLE

                    val spotsList = mutableListOf<Spot>()
                    for (doc in documents) {
                        val id = doc.id
                        val price = doc.getString("price") ?: "Free"
                        val desc = doc.getString("description") ?: ""
                        spotsList.add(Spot(id, price, desc))
                    }
                    rvMySpots.adapter = SpotsAdapter(spotsList)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading spots", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteSpot(spotId: String) {
        db.collection("spots").document(spotId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Spot deleted successfully!", Toast.LENGTH_SHORT).show()
                loadMySpots()
            }
    }

    private fun showEditDialog(spot: Spot) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_spot, null)
        val etPrice = dialogView.findViewById<EditText>(R.id.etEditPrice)
        val etDesc = dialogView.findViewById<EditText>(R.id.etEditDescription)

        etPrice.setText(spot.price)
        etDesc.setText(spot.description)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val newPrice = etPrice.text.toString().trim()
                val newDesc = etDesc.text.toString().trim()

                if (newPrice.isNotEmpty()) {
                    db.collection("spots").document(spot.id)
                        .update(
                            "price", newPrice,
                            "description", newDesc
                        )
                        .addOnSuccessListener {
                            Toast.makeText(this, "Spot updated!", Toast.LENGTH_SHORT).show()
                            loadMySpots()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to update", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class SpotsAdapter(private val spots: List<Spot>) : RecyclerView.Adapter<SpotsAdapter.SpotViewHolder>() {


        inner class SpotViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val btnEdit: ImageButton = view.findViewById(R.id.btnEditSpot)
            val tvPrice: TextView = view.findViewById(R.id.tvItemPrice)
            val tvDesc: TextView = view.findViewById(R.id.tvItemDesc)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteSpot)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpotViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_spot, parent, false)
            return SpotViewHolder(view)
        }

        override fun onBindViewHolder(holder: SpotViewHolder, position: Int) {
            val spot = spots[position]
            holder.tvPrice.text = spot.price
            holder.tvDesc.text = spot.description

            holder.btnEdit.setOnClickListener {
                showEditDialog(spot)
            }
            holder.btnDelete.setOnClickListener {
                deleteSpot(spot.id)
            }
        }

        override fun getItemCount(): Int = spots.size
    }
}