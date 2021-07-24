package com.example.trackerapp.ar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.location.Location;
import android.opengl.Matrix;
import android.util.Log;
import android.view.View;

import com.example.trackerapp.location.MyLocation;
import com.example.trackerapp.location.People;
import com.example.trackerapp.R;
import com.example.trackerapp.location.Vehicle;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;


public class AROverlayView extends View {

    //Since we have never learned AR scenes before
    //(applying for location permissions, applying for camera permissions, displaying the algorithm of the marker in AR, and the related code of the sensor),
    //We have done a lot of study and research on Dnt Nguten's code in the AR function and made a lot of modifications and improvements, and solved multiple bugs:
    //reference: https://github.com/dat-ng/ar-location-based-android

    Context context;
    private float[] rotatedProjectionMatrix = new float[16];
    private Location currentLocation;
    private List<MyLocation> locationList = new ArrayList<>();
    private Bitmap bitmap;
    private Bitmap peoplebitmap;
    private Bitmap vehiclebitmap;
    private DatabaseReference mDatabase;// reference for data retrieve
    private List<People> peopleList = new ArrayList<>();
    private List<Vehicle> vehicleList = new ArrayList<>();

    public AROverlayView(Context context) {
        super(context);
        this.context = context;
        mDatabase = FirebaseDatabase.getInstance().getReference().child("Building");
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    MyLocation location = postSnapshot.getValue(MyLocation.class);
                    // add to location list
                    locationList.add(location);
                }
                // loop through the list
                for (int i = 0; i<locationList.size();i++) {
                    String name=locationList.get(i).getName();
                    double latitude = locationList.get(i).getLatitude();
                    double longitude = locationList.get(i).getLongitude();
                    double altitude = locationList.get(i).getAltitude();
                    bitmap= BitmapFactory.decodeResource(getResources(), R.mipmap.icon_add);
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
//people
        mDatabase = FirebaseDatabase.getInstance().getReference().child("People");
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    People people = postSnapshot.getValue(People.class);
                    // add to location list
                    peopleList.add(people);
                }
                // loop through the list
                for (int i = 0; i<peopleList.size();i++) {
                    double latitude = peopleList.get(i).getLatitude();
                    double longitude = peopleList.get(i).getLongitude();
                    double altitude = peopleList.get(i).getAltitude();

                    peoplebitmap= BitmapFactory.decodeResource(getResources(), R.drawable.arworker);
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
//vehicle
        mDatabase = FirebaseDatabase.getInstance().getReference().child("Vehicle");
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    Vehicle vehicle = postSnapshot.getValue(Vehicle.class);
                    // add to location list
                   vehicleList.add(vehicle);
                }
                // loop through the list
                for (int i = 0; i<vehicleList.size();i++) {
                    double latitude = vehicleList.get(i).getLatitude();
                    double longitude = vehicleList.get(i).getLongitude();
                    double altitude = vehicleList.get(i).getAltitude();

                    vehiclebitmap= BitmapFactory.decodeResource(getResources(), R.drawable.arvehicle);
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }


    public void updateRotatedProjectionMatrix(float[] rotatedProjectionMatrix) {
        this.rotatedProjectionMatrix = rotatedProjectionMatrix;
        this.invalidate();
    }

    public void updateCurrentLocation(Location currentLocation){
        this.currentLocation = currentLocation;
        this.invalidate();
    }
//set the name of red cross building
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(40);
        if (currentLocation == null) {
            return;
        }
        for (int i = 0; i < locationList.size(); i ++) {
            float[] currentLocationInECEF = LocationHelper.WSG84toECEF(currentLocation);
            float[] pointInECEF = LocationHelper.WSG84toECEF(locationList.get(i).getLocation());
            float[] pointInENU = LocationHelper.ECEFtoENU(currentLocation, currentLocationInECEF, pointInECEF);

            float[] cameraCoordinateVector = new float[4];
            Matrix.multiplyMV(cameraCoordinateVector, 0, rotatedProjectionMatrix, 0, pointInENU, 0);

            // cameraCoordinateVector[2] is z, that always less than 0 to display on right position
            // if z > 0, the point will display on the opposite
            if (cameraCoordinateVector[2] < 0) {
                float x  = -((0.5f + cameraCoordinateVector[0]/cameraCoordinateVector[3]) * canvas.getWidth());
                float y = ((0.5f - cameraCoordinateVector[1]/cameraCoordinateVector[3]) * canvas.getHeight())/2;
               // cahnge the white point into red cross emblem
                canvas.drawBitmap(bitmap,x,y,paint);
                canvas.drawText(locationList.get(i).getName(), x - ( locationList.get(i).getName().length() / 2), y - 40, paint);
            }
        }
         //people
        for (int i = 0; i < peopleList.size(); i ++) {
            float[] currentLocationInECEF = LocationHelper.WSG84toECEF(currentLocation);
            float[] pointInECEF = LocationHelper.WSG84toECEF(peopleList.get(i).getPeople());
            float[] pointInENU = LocationHelper.ECEFtoENU(currentLocation, currentLocationInECEF, pointInECEF);

            float[] cameraCoordinateVector = new float[4];
            Matrix.multiplyMV(cameraCoordinateVector, 0, rotatedProjectionMatrix, 0, pointInENU, 0);

            // cameraCoordinateVector[2] is z, that always less than 0 to display on right position
            // if z > 0, the point will display on the opposite
            if (cameraCoordinateVector[2] < 0) {
                float x  = -((0.5f + cameraCoordinateVector[0]/cameraCoordinateVector[3]) * canvas.getWidth());
                float y = ((0.5f - cameraCoordinateVector[1]/cameraCoordinateVector[3]) * canvas.getHeight())/2;
                // cahnge the white point into red cross emblem
                canvas.drawBitmap(peoplebitmap,x,y,paint);
            }
        }

        //vehicle
        for (int i = 0; i < vehicleList.size(); i ++) {
            float[] currentLocationInECEF = LocationHelper.WSG84toECEF(currentLocation);
            float[] pointInECEF = LocationHelper.WSG84toECEF(vehicleList.get(i).getVehicle());
            float[] pointInENU = LocationHelper.ECEFtoENU(currentLocation, currentLocationInECEF, pointInECEF);

            float[] cameraCoordinateVector = new float[4];
            Matrix.multiplyMV(cameraCoordinateVector, 0, rotatedProjectionMatrix, 0, pointInENU, 0);

            // cameraCoordinateVector[2] is z, that always less than 0 to display on right position
            // if z > 0, the point will display on the opposite
            if (cameraCoordinateVector[2] < 0) {
                float x  = -((0.5f + cameraCoordinateVector[0]/cameraCoordinateVector[3]) * canvas.getWidth());
                float y = ((0.5f - cameraCoordinateVector[1]/cameraCoordinateVector[3]) * canvas.getHeight())/2;
                // cahnge the white point into red cross emblem
                canvas.drawBitmap(vehiclebitmap,x,y,paint);
            }
        }
    }
}
