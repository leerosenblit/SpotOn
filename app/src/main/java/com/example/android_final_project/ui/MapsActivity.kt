package com.example.android_final_project.ui

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.android_final_project.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.android_final_project.databinding.ActivityMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.ceil
import kotlin.math.max
import java.util.Locale

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        setupNavigationMenu()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        try {
            val success = mMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)
            )
            if (!success) {
                Log.e("MapsActivity", "Style parsing failed.")
            }
        } catch (e: Resources.NotFoundException) {
            Log.e("MapsActivity", "Can't find style. Error: ", e)
        }

        loadSpotsFromFirebase()
        enableMyLocation()

        setupAddSpotButton()
        setupMarkerClickListener()
        setupSearchBar()
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            mMap.isMyLocationEnabled = true

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun setupAddSpotButton() {
        binding.fabAddSpot.setOnClickListener {
            try {
                val dialogView = layoutInflater.inflate(R.layout.dialog_add_spot, null)
                val etPrice = dialogView.findViewById<EditText>(R.id.etSpotPrice)
                val etDesc = dialogView.findViewById<EditText>(R.id.etSpotDescription)
                val etAddress = dialogView.findViewById<EditText>(R.id.etSpotAddress)

                AlertDialog.Builder(this@MapsActivity)
                    .setView(dialogView)
                    .setPositiveButton("Save Spot") { _, _ ->
                        val price = etPrice.text.toString()
                        val description = etDesc.text.toString()
                        val address = etAddress?.text?.toString() ?: ""

                        if (address.isNotEmpty()) {
                            val geocoder = Geocoder(this@MapsActivity, Locale.getDefault())
                            try {
                                val addresses = geocoder.getFromLocationName(address, 1)
                                if (!addresses.isNullOrEmpty()) {
                                    val location = addresses[0]
                                    saveSpotToFirebase(location.latitude, location.longitude, price, description)

                                    val newLatLng = LatLng(location.latitude, location.longitude)
                                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))
                                    return@setPositiveButton
                                } else {
                                    Toast.makeText(this@MapsActivity, "Address not found, using map center", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(this@MapsActivity, "Geocoding error, using map center", Toast.LENGTH_SHORT).show()
                            }
                        }

                        val centerLocation = mMap.cameraPosition.target
                        saveSpotToFirebase(centerLocation.latitude, centerLocation.longitude, price, description)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(this@MapsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private fun setupMarkerClickListener() {
        mMap.setOnMarkerClickListener { marker ->
            if (marker.title == null) return@setOnMarkerClickListener false

            val bottomSheetDialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.bottom_sheet_spot_details, null)
            bottomSheetDialog.setContentView(view)

            val tvPrice = view.findViewById<TextView>(R.id.tvSheetPrice)
            val tvDesc = view.findViewById<TextView>(R.id.tvSheetDescription)
            val rbSpotRating = view.findViewById<RatingBar>(R.id.rbSpotRating)
            val btnBook = view.findViewById<Button>(R.id.btnBookSpot)
            val btnNav = view.findViewById<Button>(R.id.btnNavigate)

            val spotId = marker.tag as? String
            val db = FirebaseFirestore.getInstance()
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

            tvPrice.text = marker.title
            tvDesc.text = marker.snippet ?: ""

            if (spotId != null) {
                db.collection("spots").document(spotId).get().addOnSuccessListener { doc ->
                    val ratingSum = doc.getDouble("ratingSum") ?: 0.0
                    val ratingCount = doc.getDouble("ratingCount") ?: 0.0
                    if (ratingCount > 0) {
                        rbSpotRating.rating = (ratingSum / ratingCount).toFloat()
                    } else {
                        rbSpotRating.rating = 0f
                    }
                }
            }

            if (marker.title == "Your Booked Spot") {
                btnBook.text = "Release Spot"
                btnBook.setBackgroundColor(Color.RED)

                btnBook.setOnClickListener {
                    val dialogView = layoutInflater.inflate(R.layout.dialog_rate_spot, null)
                    val rbUserRating = dialogView.findViewById<RatingBar>(R.id.rbUserRating)

                    AlertDialog.Builder(this)
                        .setView(dialogView)
                        .setPositiveButton("Submit & Release") { _, _ ->
                            val givenRating = rbUserRating.rating.toDouble()
                            processSpotRelease(spotId, givenRating, bottomSheetDialog)
                        }
                        .setNegativeButton("Skip") { _, _ ->
                            processSpotRelease(spotId, null, bottomSheetDialog)
                        }
                        .show()
                }
            } else {
                btnBook.setOnClickListener {
                    if (spotId != null) {
                        db.collection("spots").document(spotId)
                            .update(
                                "isAvailable", false,
                                "bookedBy", currentUserId,
                                "bookedAt", System.currentTimeMillis()
                            )
                            .addOnSuccessListener {

                                bottomSheetDialog.dismiss()

                                val successDialogView = layoutInflater.inflate(R.layout.dialog_booking_success, null)
                                val successDialog = Dialog(this@MapsActivity)

                                successDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                                successDialog.setContentView(successDialogView)
                                successDialog.setCancelable(false)

                                successDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                                successDialog.show()

                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (successDialog.isShowing) {
                                        successDialog.dismiss()
                                    }
                                }, 2500)
                            }
                    }
                }
            }

            btnNav.setOnClickListener {
                val uri = Uri.parse("google.navigation:q=${marker.position.latitude},${marker.position.longitude}")
                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            }

            bottomSheetDialog.show()
            true
        }
    }

    private fun processSpotRelease(spotId: String?, givenRating: Double?, bottomSheetDialog: BottomSheetDialog) {
        if (spotId == null) return
        val db = FirebaseFirestore.getInstance()

        db.collection("spots").document(spotId).get().addOnSuccessListener { doc ->
            val ownerId = doc.getString("ownerId") ?: return@addOnSuccessListener

            val priceStr = doc.getString("price") ?: "0"
            val cleanPrice = priceStr.replace(Regex("[^0-9]"), "")
            val pricePerHour = cleanPrice.toDoubleOrNull() ?: 0.0

            val bookedAt = doc.getLong("bookedAt") ?: System.currentTimeMillis()
            val diffInHours = (System.currentTimeMillis() - bookedAt).toDouble() / (1000 * 60 * 60)
            val hoursToCharge = max(1.0, ceil(diffInHours)).toInt()
            val finalPrice = (pricePerHour * hoursToCharge).toLong()

            val walletUpdate = hashMapOf("totalEarned" to FieldValue.increment(finalPrice))
            db.collection("users").document(ownerId)
                .set(walletUpdate, SetOptions.merge())
                .addOnSuccessListener {

                    val updates = mutableMapOf<String, Any?>(
                        "isAvailable" to true,
                        "bookedBy" to null,
                        "bookedAt" to null
                    )

                    if (givenRating != null) {
                        updates["ratingSum"] = FieldValue.increment(givenRating)
                        updates["ratingCount"] = FieldValue.increment(1.0)
                    }

                    db.collection("spots").document(spotId).update(updates)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Spot released! ₪$finalPrice added to owner's wallet.", Toast.LENGTH_LONG).show()
                            bottomSheetDialog.dismiss()
                        }
                }
        }
    }

    private fun setupNavigationMenu() {
        drawerLayout = findViewById(R.id.drawerLayout)
        val navView = findViewById<NavigationView>(R.id.navigationView)
        val btnMenu = findViewById<FloatingActionButton>(R.id.btnOpenMenu)

        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_my_spots -> {
                    val intent = Intent(this, MySpotsActivity::class.java)
                    startActivity(intent)
                }
                R.id.nav_my_bookings -> {
                    val intent = Intent(this, MyBookingsActivity::class.java)
                    startActivity(intent)
                }
                R.id.nav_wallet -> {
                    showWalletBottomSheet()
                }
                R.id.nav_settings -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                }
                R.id.nav_payment -> {
                    val intent = Intent(this, PaymentActivity::class.java)
                    startActivity(intent)
                }
                R.id.nav_logout -> {
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun saveSpotToFirebase(lat: Double, lng: Double, price: String, description: String) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "Unknown"

        val spotMap = hashMapOf(
            "ownerId" to userId,
            "latitude" to lat,
            "longitude" to lng,
            "price" to price,
            "description" to description,
            "isAvailable" to true,
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("spots")
            .add(spotMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Spot Saved Successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving spot: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadSpotsFromFirebase() {
        val db = FirebaseFirestore.getInstance()

        db.collection("spots")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(this, "Listen failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    mMap.clear()

                    for (document in snapshots) {
                        val isAvailable = document.getBoolean("isAvailable") ?: true
                        val bookedBy = document.getString("bookedBy") ?: ""
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

                        val lat = document.getDouble("latitude") ?: continue
                        val lng = document.getDouble("longitude") ?: continue
                        val price = document.getString("price") ?: ""
                        val description = document.getString("description") ?: ""
                        val position = LatLng(lat, lng)

                        if (isAvailable) {
                            val marker = mMap.addMarker(
                                MarkerOptions()
                                    .position(position)
                                    .title("Available: $price")
                                    .icon(bitmapDescriptorFromVector(this, R.drawable.ic_parking_available))
                            )
                            marker?.tag = document.id
                        } else if (bookedBy == currentUserId) {
                            val marker = mMap.addMarker(
                                MarkerOptions()
                                    .position(position)
                                    .title("Your Booked Spot")
                                    .snippet("Price: $price - $description")
                                    .icon(bitmapDescriptorFromVector(this, R.drawable.ic_car_booked))
                            )
                            marker?.tag = document.id
                        }
                    }
                }
            }
    }

    private fun setupSearchBar() {
        val etSearch = findViewById<EditText>(R.id.etSearchAddress)
        val btnSearch = findViewById<ImageButton>(R.id.btnSearchAddress)

        btnSearch.setOnClickListener {
            val locationName = etSearch.text.toString()
            if (locationName.isNotEmpty()) {
                val geocoder = Geocoder(this)
                try {
                    val addressList = geocoder.getFromLocationName(locationName, 1)

                    if (!addressList.isNullOrEmpty()) {
                        val address = addressList[0]
                        val latLng = LatLng(address.latitude, address.longitude)

                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))

                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)

                    } else {
                        Toast.makeText(this, "Location not found 🌍", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Search error. Check internet connection.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
        vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                Toast.makeText(this, "Permission denied. Can't show location.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showWalletBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_wallet, null)
        bottomSheetDialog.setContentView(view)

        val tvTotalEarnings = view.findViewById<TextView>(R.id.tvTotalEarnings)

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val db = FirebaseFirestore.getInstance()

        if (currentUserId != null) {
            db.collection("users").document(currentUserId).get()
                .addOnSuccessListener { document ->
                    val totalEarned = document.getLong("totalEarned") ?: 0L
                    tvTotalEarnings.text = "₪$totalEarned"
                }
                .addOnFailureListener {
                    Log.e("Wallet", "Error getting earnings", it)
                    tvTotalEarnings.text = "₪0"
                }
        }

        bottomSheetDialog.show()
    }
}