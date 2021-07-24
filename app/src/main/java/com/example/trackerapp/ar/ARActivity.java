package com.example.trackerapp.ar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.example.trackerapp.location.MyLocation;
import com.example.trackerapp.location.People;
import com.example.trackerapp.R;
import com.example.trackerapp.location.Vehicle;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

import static android.hardware.SensorManager.AXIS_MINUS_X;
import static android.hardware.SensorManager.AXIS_MINUS_Y;
import static android.hardware.SensorManager.AXIS_X;
import static android.hardware.SensorManager.AXIS_Y;
import static android.hardware.SensorManager.SENSOR_DELAY_NORMAL;
import static android.hardware.SensorManager.getOrientation;
import static android.hardware.SensorManager.getRotationMatrixFromVector;
import static android.hardware.SensorManager.remapCoordinateSystem;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

// Please Notice:
//-If more than 1 person has the application opened, the current user location shown on the minimap will keep changing
//-Please enable location and camera permissions to use the application

//reference:https://github.com/dat-ng/ar-location-based-android

public class ARActivity extends FragmentActivity implements SensorEventListener, LocationListener, OnMapReadyCallback, RoutingListener {

    final static String TAG = "ARActivity";
    private SurfaceView surfaceView;
    private FrameLayout cameraContainerLayout;
    private AROverlayView arOverlayView;
    private Camera camera;
    private ARCamera arCamera;
    private TextView tvCurrentLocation;
    private TextView tvBearing;

    private SensorManager sensorManager;
    private final static int REQUEST_CAMERA_PERMISSIONS_CODE = 11;
    public static final int REQUEST_LOCATION_PERMISSIONS_CODE = 0;

    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 0; // 10 meters
    private static final long MIN_TIME_BW_UPDATES = 0;//1000 * 60 * 1; // 1 minute

    private LocationManager locationManager;
    public Location location;
    boolean isGPSEnabled;
    boolean isNetworkEnabled;
    boolean locationServiceAvailable;
    private float declination;
    private  double bearing;
    private GoogleMap mMap;
    private DatabaseReference reference; // reference for map initialise
    private DatabaseReference mDatabase; // reference for data retrieve
    private LocationManager manager;
    MyLocation prelocation;
    List<MyLocation> locationList = new ArrayList<>();
    List<People> peopleList = new ArrayList<>();
    List<Vehicle> vehicleList = new ArrayList<>();
    List<Polyline> polylines = new ArrayList<>();
    //control the frequency of location updates
    private final int MIN_TIME = 8000; // 1 sec
    private final int MIN_DISTANCE = 1; // 1 meter

    // marker that is going to be placed on the map
    Marker myMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar);

        sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        cameraContainerLayout = findViewById(R.id.camera_container_layout);
        surfaceView = findViewById(R.id.surface_view);
        tvCurrentLocation = findViewById(R.id.tv_current_location);
         tvBearing = findViewById(R.id.tv_bearing);
         arOverlayView = new AROverlayView(this);
         registerSensors();

        manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        // firebase reference setup
        reference = FirebaseDatabase.getInstance().getReference().child("Current User");
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        readChanges();
    }

    @Override
    public void onResume() {
        super.onResume();
        requestPermission();
        initAROverlayView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseCamera();
    }
     // try to get camera and location's permission
    public void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                this.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED&&
                this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA,Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CAMERA_PERMISSIONS_CODE);
        } else {
            initARCameraView();
            initLocationService();
        }
    }

    public void initAROverlayView() {
        if (arOverlayView.getParent() != null) {
            ((ViewGroup) arOverlayView.getParent()).removeView(arOverlayView);
        }
        ViewGroup.LayoutParams layoutParams=new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        cameraContainerLayout.addView(arOverlayView,layoutParams);
    }

    public void initARCameraView() {
        reloadSurfaceView();
        if (arCamera == null) {
            arCamera = new ARCamera(this, surfaceView);
        }
        if (arCamera.getParent() != null) {
            ((ViewGroup) arCamera.getParent()).removeView(arCamera);
        }
        cameraContainerLayout.addView(arCamera);
        arCamera.setKeepScreenOn(true);
        initCamera();
    }

    private void initCamera() {
        int numCams = Camera.getNumberOfCameras();
        if(numCams > 0){
            try{
                camera = Camera.open();
                camera.startPreview();
                arCamera.setCamera(camera);
            } catch (RuntimeException ex){
                Toast.makeText(this, "Camera not found", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void reloadSurfaceView() {
        if (surfaceView.getParent() != null) {
            ((ViewGroup) surfaceView.getParent()).removeView(surfaceView);
        }
        cameraContainerLayout.addView(surfaceView);
    }

    private void releaseCamera() {
        try {
            if(camera != null) {
                //  camera.setPreviewCallback(null);
                camera.stopPreview();
                // arCamera.setCamera(null);
                camera.release();
                camera = null;
            }
        }catch (Exception e){

        }
    }

    private void registerSensors() {
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrixFromVector = new float[16];
            float[] rotationMatrix = new float[16];
            getRotationMatrixFromVector(rotationMatrixFromVector, sensorEvent.values);
            final int screenRotation = this.getWindowManager().getDefaultDisplay()
                    .getRotation();

            switch (screenRotation) {
                case ROTATION_90:
                    remapCoordinateSystem(rotationMatrixFromVector,
                            AXIS_Y,
                            AXIS_MINUS_X, rotationMatrix);
                    break;
                case ROTATION_270:
                    remapCoordinateSystem(rotationMatrixFromVector,
                            AXIS_MINUS_Y,
                            AXIS_X, rotationMatrix);
                    break;
                case ROTATION_180:
                    remapCoordinateSystem(rotationMatrixFromVector,
                            AXIS_MINUS_X, AXIS_MINUS_Y,
                            rotationMatrix);
                    break;
                default:
                    remapCoordinateSystem(rotationMatrixFromVector,
                            AXIS_X, AXIS_Y,
                            rotationMatrix);
                    break;
            }
            if (arCamera==null){
                return;
            }
            float[] projectionMatrix = arCamera.getProjectionMatrix();
            float[] rotatedProjectionMatrix = new float[16];
            Matrix.multiplyMM(rotatedProjectionMatrix, 0, projectionMatrix, 0, rotationMatrix, 0);
            this.arOverlayView.updateRotatedProjectionMatrix(rotatedProjectionMatrix);

            //Heading
            float[] orientation = new float[3];
            getOrientation(rotatedProjectionMatrix, orientation);
            bearing = Math.toDegrees(orientation[0]) + declination;
            tvBearing.setText(String.format("Bearing: %s", bearing));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            Log.w("DeviceOrientation", "Orientation compass unreliable");
        }
    }

    private void initLocationService() {

        if ( Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission( this, Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED) {
            return  ;
        }

        try   {
            this.locationManager = (LocationManager) this.getSystemService(this.LOCATION_SERVICE);

            // Get GPS and network status
            this.isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            this.isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!isNetworkEnabled && !isGPSEnabled)    {
                // cannot get location
                this.locationServiceAvailable = false;
            }

            this.locationServiceAvailable = true;

            if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_BW_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                if (locationManager != null)   {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    updateLatestLocation();
                }
            }

            if (isGPSEnabled)  {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        MIN_TIME_BW_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                if (locationManager != null)  {
                    location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    updateLatestLocation();
                }
            }
        } catch (Exception ex)  {
            Log.e(TAG, ex.getMessage());

        }
    }
   // update user's location
    private void updateLatestLocation() {
        if (arOverlayView !=null && location != null) {
            arOverlayView.updateCurrentLocation(location);
            tvCurrentLocation.setText(String.format("lat: %s \nlon: %s \naltitude: %s \n",
                    location.getLatitude(), location.getLongitude(), location.getAltitude()));
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        updateLatestLocation();
        if(location != null){
            // when location changes we pass location to saveLocation method
            saveLocation(location);
        }else{
            Toast.makeText(this,"No Location",Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    private void readChanges() {
        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // check new changes
                if(dataSnapshot.exists()){
                    try{
                        // get new location
                        MyLocation location = dataSnapshot.getValue(MyLocation.class);
                        // check if there is a new location update
                        if(location!=null){
//                            float bearing;
//                            if (prelocation == null) {
//                                // set bearing to 0 if there is no previous location yet
//                                bearing = 0;
//                            } else {
//                                // otherwise calculate the bearing
//                                bearing = (float) bearingBetweenLocations(prelocation, location);
//                            }
                            // set marker on the map
                            myMarker.setPosition(new LatLng(location.getLatitude(),location.getLongitude()));
                            // update camera
                            updateCameraBearing(mMap, (float)bearing, location);
                            // store previous location
                            prelocation = location;
                        }
                    }catch (Exception e){
                        Toast.makeText(ARActivity.this,e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    // rotate the map view according to user's moving direction using bearing
    private void updateCameraBearing(GoogleMap googleMap, float bearing, MyLocation ml) {
        LatLng newLocation = new LatLng(ml.getLatitude(), ml.getLongitude());
        if ( googleMap == null) return;
        CameraPosition camPos = CameraPosition
                .builder(
                        googleMap.getCameraPosition() // current Camera
                )
                .bearing(bearing)
                .target(newLocation)
                .zoom(18)
                .build();
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(camPos));
    }

    // get location updates from either gps or network
    private void getLocationUpdates() {
        if(manager!=null){
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME, MIN_DISTANCE, this);
                } else if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME, MIN_DISTANCE, this);
                } else {
                    Toast.makeText(this, "No Provider Enabled", Toast.LENGTH_SHORT).show();
                }
            }else{
                // request for permission if it is not granted
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},101);
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // initialise the map
        LatLng sydney = new LatLng(31, 55.97);
        // Add a marker in Sydney
        // change the icon display for markers
        myMarker = mMap.addMarker(new MarkerOptions().position(sydney).title("User").icon(BitmapDescriptorFactory.fromResource(R.drawable.location)));
        // ui settings
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setAllGesturesEnabled(true);
        googleMap.setMyLocationEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setZoomControlsEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(true);


        // set red cross building, people and vehicle marker
        getBuildingLocationData();
        getPeopleLocation();
        getVehicleLocation();
        // set route using current location
        setRoute();
    }

    public void setRoute() {
        mDatabase = FirebaseDatabase.getInstance().getReference().child("Current User");
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // get latitude and longitude of current user
                MyLocation currentLocation = dataSnapshot.getValue(MyLocation.class);
                double latitude = currentLocation.getLatitude();
                double longitude = currentLocation.getLongitude();

                // Reference: https://stackoverflow.com/a/57341379
                LatLng start = new LatLng(latitude, longitude);
                LatLng end = new LatLng(-33.912275, 151.223958);
                // set route between current location and destination
                Routing routing = new Routing.Builder()
                        .travelMode(Routing.TravelMode.WALKING)
                        .withListener(ARActivity.this)
                        .waypoints(start, end)
                        .key("")//Enter Google Maps API Key here
                        .build();
                routing.execute();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }


    public void getBuildingLocationData() {
        // database reference
        mDatabase = FirebaseDatabase.getInstance().getReference().child("Building");
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // get all building location stored in "Building"
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    MyLocation location = postSnapshot.getValue(MyLocation.class);
                    // add to location list
                    locationList.add(location);
                }
                // loop through the list
                for (int i = 0; i<locationList.size();i++) {
                    // get latitude and longitude of each building
                    double latitude = locationList.get(i).getLatitude();
                    double longitude = locationList.get(i).getLongitude();
                    // add marker
                    mMap.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("red cross building").icon(BitmapDescriptorFactory.fromResource(R.drawable.redcross)));
                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public void getPeopleLocation() {
        // database reference
        mDatabase = FirebaseDatabase.getInstance().getReference().child("People");
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // get all people location stored in "People"
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    People people = postSnapshot.getValue(People.class);
                    // add to people list
                    peopleList.add(people);
                }
                // loop through the list
                for (int i = 0; i<peopleList.size();i++) {
                    // get latitude and longitude of each person
                    double latitude = peopleList.get(i).getLatitude();
                    double longitude = peopleList.get(i).getLongitude();
                    // add marker
                    mMap.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("red cross worker").icon(BitmapDescriptorFactory.fromResource(R.drawable.red_cross_worker)));
                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public void getVehicleLocation() {
        // database reference
        mDatabase = FirebaseDatabase.getInstance().getReference().child("Vehicle");
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    // get all vehicle location stored in "Vehicle"
                    Vehicle vehicle = postSnapshot.getValue(Vehicle.class);
                    // add to vehicle list
                    vehicleList.add(vehicle);
                }
                // loop through the list
                for (int i = 0; i<vehicleList.size();i++) {
                    // get latitude and longitude of each vehicle
                    double latitude = vehicleList.get(i).getLatitude();
                    double longitude = vehicleList.get(i).getLongitude();
                    // add marker
                    mMap.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("red cross vehicle").icon(BitmapDescriptorFactory.fromResource(R.drawable.red_cross_vehicle)));
                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    // save location in firebase database
    private void saveLocation(Location location) {
        reference.setValue(location);
    }


    // if fail to set route
    @Override
    public void onRoutingFailure(RouteException e) {
    }

    @Override
    public void onRoutingStart() {
        Log.e("check", "onRoutingStart");
    }

    // get route successfully
    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {
        Log.e("check", "onRoutingSuccess");

        // remove existing polylines
        if (polylines.size() > 0) {
            for (Polyline poly : polylines) {
                poly.remove();
            }
        }

        polylines = new ArrayList<>();
        //add route(s) to the map.
        for (int i = 0; i < route.size(); i++) {
            //In case of more than 5 alternative routes
            PolylineOptions polyOptions = new PolylineOptions();
            // set route color
            polyOptions.color(Color.GRAY);
            polyOptions.width(10 + i * 3);
            polyOptions.addAll(route.get(i).getPoints());
            // add route to the map
            Polyline polyline = mMap.addPolyline(polyOptions);
            polylines.add(polyline);
        }
    }

    @Override
    public void onRoutingCancelled() {
        Log.e("check", "onRoutingCancelled");
    }
}
