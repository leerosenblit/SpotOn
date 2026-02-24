package com.example.android_final_project.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.android_final_project.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class PaymentActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser

    private lateinit var cardSavedPayment: View
    private lateinit var tvEmptyPayment: TextView
    private lateinit var tvCardNumber: TextView
    private lateinit var tvCardExpiry: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        val btnAddCard = findViewById<Button>(R.id.btnAddPaymentMethod)
        val btnDeleteCard = findViewById<ImageButton>(R.id.btnDeleteCard)

        cardSavedPayment = findViewById(R.id.cardSavedPayment)
        tvEmptyPayment = findViewById(R.id.tvEmptyPayment)
        tvCardNumber = findViewById(R.id.tvCardNumber)
        tvCardExpiry = findViewById(R.id.tvCardExpiry)

        cardSavedPayment.visibility = View.GONE
        tvEmptyPayment.visibility = View.VISIBLE

        loadSavedCard()

        btnAddCard.setOnClickListener {
            showAddCardDialog()
        }

        btnDeleteCard.setOnClickListener {
            deleteCardFromFirebase()
        }
    }

    private fun loadSavedCard() {
        val userId = currentUser?.uid ?: return

        db.collection("payment_methods").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    tvCardNumber.text = document.getString("maskedNumber")
                    tvCardExpiry.text = document.getString("expiry")

                    cardSavedPayment.visibility = View.VISIBLE
                    tvEmptyPayment.visibility = View.GONE
                } else {
                    cardSavedPayment.visibility = View.GONE
                    tvEmptyPayment.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load payment method", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAddCardDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_card, null)
        val etCardNumber = dialogView.findViewById<EditText>(R.id.etCardNumber)
        val etExpiry = dialogView.findViewById<EditText>(R.id.etExpiry)

        etExpiry.addTextChangedListener(object : TextWatcher {
            var isDeleting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                isDeleting = count > after
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isDeleting) return
                val input = s.toString()
                if (input.length == 2 && !input.contains("/")) {
                    etExpiry.setText("$input/")
                    etExpiry.setSelection(etExpiry.text.length)
                }
            }
        })

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val cardNumber = etCardNumber.text.toString().trim()
                val expiry = etExpiry.text.toString().trim()
                val expiryParts = expiry.split("/")

                if (expiryParts.size == 2 && cardNumber.length >= 4) {
                    val inputMonth = expiryParts[0].toIntOrNull() ?: 0
                    val inputYear = expiryParts[1].toIntOrNull() ?: 0

                    val calendar = Calendar.getInstance()
                    val currentYear = calendar.get(Calendar.YEAR) % 100
                    val currentMonth = calendar.get(Calendar.MONTH) + 1

                    if (inputMonth !in 1..12) {
                        Toast.makeText(this, "Invalid month", Toast.LENGTH_SHORT).show()
                    } else if (inputYear < currentYear || (inputYear == currentYear && inputMonth < currentMonth)) {
                        Toast.makeText(this, "Card is expired!", Toast.LENGTH_SHORT).show()
                    } else {
                        val last4Digits = cardNumber.takeLast(4)
                        val maskedCard = "•••• •••• •••• $last4Digits"
                        val fullExpiryText = "Expires $expiry"

                        val userId = currentUser?.uid ?: return@setPositiveButton
                        val cardData = hashMapOf(
                            "maskedNumber" to maskedCard,
                            "expiry" to fullExpiryText
                        )

                        db.collection("payment_methods").document(userId)
                            .set(cardData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Card saved securely!", Toast.LENGTH_SHORT).show()
                                loadSavedCard()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Error saving card", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Toast.makeText(this, "Invalid card details", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCardFromFirebase() {
        val userId = currentUser?.uid ?: return

        db.collection("payment_methods").document(userId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Card removed", Toast.LENGTH_SHORT).show()
                loadSavedCard()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error removing card", Toast.LENGTH_SHORT).show()
            }
    }
}