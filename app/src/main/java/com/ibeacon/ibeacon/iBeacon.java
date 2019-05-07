package com.ibeacon.ibeacon;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class iBeacon {
    public Integer minor;
    public Integer major;
    public String uuid;
    public String sala;
    public String zdjecie;
    public String opis;
    private List<Double> rssim = new ArrayList<>();

    public iBeacon(Integer minor, Integer major, String uuid, String sala, String zdjecie, String opis) {
        this.minor = minor;
        this.major = major;
        this.uuid = uuid;
        this.sala = sala;
        this.zdjecie = zdjecie;
        this.opis = opis;
    }

    public iBeacon() {
    }

    public Integer getMinor() {
        return minor;
    }

    public Integer getMajor() {
        return major;
    }

    public void addDistance(double distance) {
        rssim.add(distance);
    }

    public void clearDistance() {
        rssim.clear();
    }

    public double getDistanceMedian() {
        if( rssim.size() != 0 ) {
            Collections.sort(rssim);
            int middle = rssim.size() / 2;
            middle = middle > 0 && middle % 2 == 0 ? middle - 1 : middle;
            return rssim.get(middle);
        }
        return 0;
    }

    public String getSala() {
        return sala;
    }

    public String getZdjecie() {
        return zdjecie;
    }

    public String getOpis() {
        return opis;
    }
}
