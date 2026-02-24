package com.example.android_final_project.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android_final_project.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class BookedSpot(val id: String, val price: String, val description: String)

class MyBookingsActivity : AppCompatActivity() {

    private lateinit var rvMyBookings: RecyclerView
    private val db = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_bookings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Bookings"

        rvMyBookings = findViewById(R.id.rvMyBookings)
        rvMyBookings.layoutManager = LinearLayoutManager(this)

        loadMyBookings()

        val btnBack = findViewById<TextView>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }
    }


    private fun loadMyBookings() {
        if (currentUser == null) return

        db.collection("spots")
            .whereEqualTo("bookedBy", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->
                val tvEmptyBookings = findViewById<TextView>(R.id.tvEmptyBookings)

                if (documents.isEmpty) {
                    tvEmptyBookings.visibility = View.VISIBLE
                    rvMyBookings.visibility = View.GONE
                } else {
                    tvEmptyBookings.visibility = View.GONE
                    rvMyBookings.visibility = View.VISIBLE

                    val bookingsList = mutableListOf<BookedSpot>()
                    for (doc in documents) {
                        val id = doc.id
                        val price = doc.getString("price") ?: "Free"
                        val desc = doc.getString("description") ?: ""
                        bookingsList.add(BookedSpot(id, price, desc))
                    }
                    rvMyBookings.adapter = BookingsAdapter(bookingsList)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading bookings", Toast.LENGTH_SHORT).show()
            }
    }

    private fun releaseSpot(spotId: String) {
        db.collection("spots").document(spotId)
            .update("isAvailable", true, "bookedBy", null)
            .addOnSuccessListener {
                Toast.makeText(this, "Spot released successfully!", Toast.LENGTH_SHORT).show()
                loadMyBookings()
            }
    }

    inner class BookingsAdapter(private val bookings: List<BookedSpot>) : RecyclerView.Adapter<BookingsAdapter.BookingViewHolder>() {

        inner class BookingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvPrice: TextView = view.findViewById(R.id.tvBookingPrice)
            val tvDesc: TextView = view.findViewById(R.id.tvBookingDesc)
            val btnRelease: Button = view.findViewById(R.id.btnReleaseBooking)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_booking, parent, false)
            return BookingViewHolder(view)
        }

        override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
            val spot = bookings[position]
            holder.tvPrice.text = spot.price
            holder.tvDesc.text = spot.description

            holder.btnRelease.setOnClickListener {
                releaseSpot(spot.id)
            }
        }

        override fun getItemCount(): Int = bookings.size
    }
}