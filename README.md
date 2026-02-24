# SpotOn: Smart Parking App 🚗

An advanced Android application designed to manage and share parking spots in real-time.

## 🌟 Key Features

* **Real-Time Interactive Map:** Integration with Google Maps API, featuring a custom Dark Mode styling for enhanced UX.
* **Smart Spot Management:** Users can add new parking spots to the map using their current GPS location or by typing a specific address (Geocoding integration).
* **Dynamic Booking System:** Spot availability is updated in real-time. When a user books a spot, it locks out other users instantly.
* **Time-Based Billing Algorithm:** The system calculates the exact parking duration (timestamp-based) and charges the user dynamically ("per hour or part thereof").
* **"My Wallet" Dashboard:** Spot owners have a dedicated digital wallet that securely accumulates and tracks their earnings from released spots.
* **Premium UI/UX:** * Smooth Lottie animations with transparent custom Dialogs.
    * Bottom Sheet Dialogs for seamless navigation without obstructing the map.
    * Direct navigation intent to Google Maps/Waze.

## 🛠️ Tech Stack & Architecture

* **Language:** Kotlin
* **Architecture:** Layered Architecture (UI, Models, Utils) for clean code separation and scalability.
* **Backend & Database:** Firebase Authentication (User Management) & Firebase Firestore (Real-time NoSQL Database).
* **Location Services:** FusedLocationProviderClient & Geocoder API.
* **Third-Party Libraries:** Lottie (Airbnb) for high-performance animations.

## 🎥 Project Demonstration

Check out the full video demonstration explaining the architecture, features, and edge-case handling:
**[Insert YouTube Video Link Here]**

## 💡 Technical Challenges & Edge Cases Handled

During development, several complex challenges were resolved to ensure app stability:
1.  **Asynchronous Data Loading:** Handled Firebase's async nature by utilizing success callbacks and state management to prevent `NullPointerExceptions` when loading the Wallet and Map UI.
2.  **Firestore Composite Indexing:** Resolved query failures on multiple fields (user ID + spot status) by configuring server-side Composite Indexes.
3.  **UI Lifecycle Integrity:** Prevented `BadTokenException` by validating Activity state (`isFinishing`) before rendering Bottom Sheets or Dialogs.
4.  **Custom Dialog Transparency:** Overcame Android's default `DecorView` limitations to render truly transparent Lottie animations over the map.

## 👨‍💻 Author
**Lee** - CS Student, Class of 2026.
