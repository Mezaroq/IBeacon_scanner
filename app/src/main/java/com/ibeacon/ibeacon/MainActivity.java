package com.ibeacon.ibeacon;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private Handler scanHandler = new Handler();
    private int scan_interval_ms = 5000;
    private boolean isScanning = false;
    FirebaseDatabase firebaseDatabase;
    private List<iBeacon> ibeacon = new ArrayList<>();
    private iBeacon lastSwitchedBeacon;
    private TextView description;
    private TextView room;
    private TextView information;
    private ImageView image;
    private ViewGroup information_group;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        description = findViewById(R.id.description);
        room = findViewById(R.id.room);
        image = findViewById(R.id.image);
        information = findViewById(R.id.information);
        image.setImageDrawable(getDrawable(R.drawable.zi_logo));
        information_group = findViewById(R.id.information_group);
        information_group.setVisibility(View.INVISIBLE);

        checkPermission();
        loadBeaconsFromFirebase();

        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        showStatementBeforeScan();
        EnableScanning();

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);
    }

    private void EnableScanning() {
        //IF BLUETOOTH ENABLED
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter.isEnabled()) {
            //INIT BLUETOOTH LOW ENERGY
            btManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
            btAdapter = btManager.getAdapter();
            //CHECK IF LOCATION IS ON
            if(isLocationServiceEnabled()) {
                scanHandler.post(scanRunnable);
            }
        }
    }

    private void checkPermission() {
        int permissionCheckLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheckLocation != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, "Uprawnienia Lokalizacji są wymagane.", Toast.LENGTH_SHORT).show();
            } else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
    }

    private void showStatementBeforeScan() {
        if (isBluetoothEnabled() && isLocationServiceEnabled()) {
            description.setText("Uruchomiono skanowanie");
        }
        else {
            description.setText("Skanowanie wyłączone");
            information_group.setVisibility(View.VISIBLE);
            information.setText("Skanowanie do poprawnego działania wymaga Bluetooth oraz Lokalizację. Uruchom usługi po czym zrestartuj aplikację.");
        }
    }

    private boolean isBluetoothEnabled() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter.isEnabled()) {
            return true;
        }
        return false;
    }

    private boolean isLocationServiceEnabled() {
        LocationManager locationManager = null;
        boolean gps_enabled = false, network_enabled = false;

        if (locationManager == null)
            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        try {
            gps_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) { //EMPTY EXEPTION
        }

        try {
            network_enabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) { //EMPTY EXEPTION
        }

        return gps_enabled || network_enabled;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        break;
                    case BluetoothAdapter.STATE_ON:
                        scanHandler.post(scanRunnable);
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        scanHandler.post(scanRunnable);
                        break;
                }
            }
        }
    };

    //BLE SCANNING
    private Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            if (isLocationServiceEnabled()) {
                if (isScanning) {
                    if (btAdapter != null) {
                        btAdapter.getBluetoothLeScanner().stopScan(scanCallback);
                        for (iBeacon i : ibeacon) {
                            Log.i("Skan: ", "Major: "+i.getMajor()+" Minor: "+i.getMinor()+" Odleglosc: "+i.getDistanceMedian());
                        }
                        contentSwitcher();
                    }
                } else {
                    if (btAdapter != null) {
                        btAdapter.getBluetoothLeScanner().startScan(scanCallback);
                    }
                }
                isScanning = !isScanning;
            }
            scanHandler.postDelayed(this, scan_interval_ms);
        }
    };

    //SCANNING iBEACON
    private ScanCallback scanCallback = new ScanCallback() {

        public void onScanResult(int callbackType, final ScanResult result) {

            final int rssi = result.getRssi(); //RSSI value
            final byte[] record = result.getScanRecord().getBytes();

            int startByte = 2;
            boolean isIBeacon = false;

            while (startByte <= 5) {
                if (((int) record[startByte + 2] & 0xff) == 0x02 && //Identifies an iBeacon
                        ((int) record[startByte + 3] & 0xff) == 0x15) {
                    isIBeacon = true;
                    break;
                }
                startByte++;
            }

            if (isIBeacon) {
                byte[] uuidBytes = new byte[16];
                System.arraycopy(record, startByte + 4, uuidBytes, 0, 16);
                String hexString = bytesToHex(uuidBytes);

                //UUID
                String uuid = hexString.substring(0, 8) +
                        hexString.substring(8, 12) +
                        hexString.substring(12, 16) +
                        hexString.substring(16, 20) +
                        hexString.substring(20, 32);
                //MAJOR
                final int major = (record[startByte + 20] & 0xff) * 0x100 + (record[startByte + 21] & 0xff);
                //MINOR
                final int minor = (record[startByte + 22] & 0xff) * 0x100 + (record[startByte + 23] & 0xff);
                //REFERENCE VALUE
                final int txPower = record[startByte + 24];
                //DISTANCE
                final double distance = getDistance(txPower, rssi);

                for (iBeacon i : ibeacon) {
                    if ((i.getMinor() == minor) && (i.getMajor() == major)) {
                        i.addDistance(distance);
                    }
                }
            }
        }
    };

    static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private double getDistance(double rssi, double txPower) {
        return Math.pow(10, (-txPower - (-rssi)) / (10 * 4));
    }

    private iBeacon nearestBeacon() {
        double ibeaconDistance = 70;
        iBeacon lastibeacon = ibeacon.get(0);

        for (iBeacon i:ibeacon) {
            if ((i.getDistanceMedian() < ibeaconDistance) && (i.getDistanceMedian() != 0)) {
                ibeaconDistance = i.getDistanceMedian();
                lastibeacon = i;
            }
        }
        return lastibeacon;
    }

    //SLUZY DO PRZELACZANIA ZAWARTOSCI NA PODSTAWIE NAJBLIZSZEGO IBEACONA
    private void contentSwitcher() {
        iBeacon neareastBeacon = nearestBeacon();
        if (neareastBeacon.getDistanceMedian() < 3.0 && neareastBeacon.getDistanceMedian() != 0.0) {
            lastSwitchedBeacon = neareastBeacon;
            description.setText("Sala");
            room.setText(neareastBeacon.getSala());
            information.setText(neareastBeacon.getOpis());
            information_group.setVisibility(View.VISIBLE);
            Picasso.get().load(neareastBeacon.getZdjecie()).fit().into(image);
        }
        else if ((neareastBeacon.getDistanceMedian() > 4.0) || (neareastBeacon.getDistanceMedian() == 0)) {
            description.setText("Brak iBeaconów w okolicy");
            room.setText("trwa skanowanie...");
            information.setText(" ");
            information_group.setVisibility(View.INVISIBLE);
            image.setImageDrawable(getDrawable(R.drawable.zi_logo));
        }
        else if (lastSwitchedBeacon != null) {
            if (lastSwitchedBeacon != neareastBeacon) {
                description.setText("Brak iBeaconów w okolicy");
                room.setText("trwa skanowanie...");
                information.setText(" ");
                information_group.setVisibility(View.INVISIBLE);
                image.setImageDrawable(getDrawable(R.drawable.zi_logo));
            }
        }
        else {
            description.setText("Brak iBeaconów w okolicy");
            room.setText("trwa skanowanie...");
            information.setText(" ");
            information_group.setVisibility(View.INVISIBLE);
            image.setImageDrawable(getDrawable(R.drawable.zi_logo));
        }
        for (iBeacon i : ibeacon) {
            i.clearDistance();
        }
    }

    private void loadBeaconsFromFirebase() {
        firebaseDatabase = FirebaseDatabase.getInstance();
/*
        iBeacon ibeacon0 = new iBeacon(112,1,"b9407f30-f5f8-466e-aff9-25556b57fe6d","112","xD","xD");
        iBeacon ibeacon1 = new iBeacon(105,1,"b9407f30-f5f8-466e-aff9-25556b57fe6d","105","xD","xD");
        iBeacon ibeacon2 = new iBeacon(206,2,"b9407f30-f5f8-466e-aff9-25556b57fe6d","xD","xD","xD");
        iBeacon ibeacon3 = new iBeacon(208,2,"b9407f30-f5f8-466e-aff9-25556b57fe6d","xD","xD","xD");
        firebaseDatabase.getReference().child("iBeacon0").setValue(ibeacon0);
        firebaseDatabase.getReference().child("iBeacon1").setValue(ibeacon1);
        firebaseDatabase.getReference().child("iBeacon2").setValue(ibeacon2);
        firebaseDatabase.getReference().child("iBeacon3").setValue(ibeacon3);*/

        firebaseDatabase.getReference().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for(DataSnapshot child : dataSnapshot.getChildren()) {
                    ibeacon.add(child.getValue(iBeacon.class));
                    Log.d("FIREBASEc","Odczytano" + child.getValue(iBeacon.class).getMinor());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.d("FIREBASE","Odczyt ilosci iBeacon'ow nie powiódł się!"); //TODO: obsluga wylaczonego internetu
            }
        });
    }
}