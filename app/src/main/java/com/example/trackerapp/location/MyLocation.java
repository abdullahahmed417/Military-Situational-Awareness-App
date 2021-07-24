package com.example.trackerapp.location;

import android.location.Location;

public class MyLocation {
    private double latitude;
    private double longitude;
    private double altitude;
    private String name;
    Location location;

    public MyLocation(String name, double latitude, double longitude, double altitude) {
        this.name=name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude=altitude;
    }

    public MyLocation(){}

    public Location getLocation() {

        location = new Location("ARPoint");
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAltitude(altitude);
        return location;
    }


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
        this.latitude = altitude;
    }

    public String getName() {
        return name;
    }



}
