import java.util.concurrent.atomic.AtomicReference
import javax.naming.Context

data class Location(val longitude: Double, val latitude: Double)

// need to explore the use of 'expect' and 'suspend' in the below class
expect class LocationService() {
    suspend fun getCurrentLocation(): Location
}

// Implementation of the above in an Android Service
actual class LocationService {

    // Initialising based on pre-built 'controllers'
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(RapidoLocationController.getConext() as Context)
    }

    /*
        The below is simply a co-routine suspend function -> note that the below is the Android Implementation
     */
    @SuppressLint("MissingPermission") // Here the suppression is based on whether the permission has been actually checked but in this case, no
    actual suspend fun getCurrentLocation(): Location = suspendCoroutine { continuation ->
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                continuation.resume(Location(it.latitude, it.longitude))
            } ?: run {
                continuation.resumeWithException(Exception("Unable to get current location"))
            }
        }.addOnFailureListener { e ->
            continuation.resumeWithException(e)
        }
    }
}

// Implement the LocationService in iOS
actual class LocationService {

    // Define a native CLLocationManager object
    private val locationManager = CLLocationManager()

    // Define an atomic reference to store the latest location
    private val latestLocation = AtomicReference<Location>(null)

    // Define a custom delegate that extends NSObject and implements CLLocationManagerDelegateProtocol
    private class LocationDelegate : NSObject(), CLLocationManagerDelegateProtocol {

        // Define a callback to receive location updates
        var onLocationUpdate: ((Location?) -> Unit)? = null

        override fun locationManager(manager: CLLocationManager, didUpdateLocation: List<*>) {
            didUpdateLocations.firstOrNull()?.let {
                val location = it as CLLocation
                location.coordinate.useContents {
                    onLocationUpdate?.invoke(Location(latitude, longitude))
                }
            }
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            onLocationUpdate?.invoke(null)
        }
    }

    // Implement the getCurrentLocation() method
    actual suspend fun getCurrentLocation(): Location = suspendCoroutine { continuation ->
        locationManager.requestWhenInUseAuthorization()
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = kCLDistanceFilterNone
        locationManager.startUpdatingLocation()

        // Define a callback to receive location updates
        val locationDelegate = LocationDelegate()
        locationDelegate.onLocationUpdate = { location ->
            locationManager.stopUpdatingLocation()
            latestLocation.value = location
            if (location != null) {
                continuation.resume(location)
            } else {
                continuation.resumeWithException(Exception("Unable to get current location"))
            }
        }

        // Assign the locationDelegate to the CLLocationManager delegate
        locationManager.delegate = locationDelegate
    }
}