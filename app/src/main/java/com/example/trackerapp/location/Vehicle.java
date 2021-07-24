package com.example.trackerapp.location;

import android.location.Location;

public class Vehicle {
    private double latitude;
    private double longitude;
    private double altitude;
    Location location;


    public Vehicle(double latitude, double longitude, double altitude) {
        this.latitude = latitude;
        this.longitude = longitude;
       this.altitude= altitude;
    }
    public Location getVehicle() {

        location = new Location("ARPoint");

        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAltitude(altitude);
        return location;
    }
    public Vehicle(){}

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }
}