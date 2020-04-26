package de.plus_mint.cederik.wilduhr;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private final UUID HM10_CUSTOM_SERVICE = new UUID(0x0000ffe000001000L, 0x800000805f9b34fbL);
    private final UUID HM10_DATA_CHARACTERISTIC = new UUID(0x0000ffe100001000L, 0x800000805f9b34fbL);


    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothLeScanner bluetoothLeScanner;

    BroadcastReceiver BluetoothTurnOffReceiver;

    private Map<String, ScanResult> ScanResults;

    BluetoothDevice Wilduhr;

    boolean initBT(){

        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);

        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){             //Gerät ist nicht BLE-fähig? Beenden!

            return false;

        }

        bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {

            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);

        }

        BluetoothTurnOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String action = intent.getAction();

                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {

                    if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF) {

                        Toast.makeText(getApplicationContext(), "Sie sollten Bluetooth nicht ausschalten!", Toast.LENGTH_SHORT).show();
                        return;

                    }

                    if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {

                        Toast.makeText(getApplicationContext(), "Diese Anwendung ist auf Bluetooth angewiesen!", Toast.LENGTH_LONG).show();
                        try{
                            Thread.sleep(1000);
                        }
                        catch(InterruptedException e){}

                        finish();
                        return;

                    }

                }

            }
        };

        this.registerReceiver(BluetoothTurnOffReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        while(!bluetoothAdapter.isEnabled());

        return true;

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!initBT()) {

            Toast.makeText(getApplicationContext(), "Bluetooth konnte nicht aktiviert werden oder wird nicht unterstützt...", Toast.LENGTH_SHORT).show();
            finish();

        }

    }

    private class ConnectTask extends AsyncTask<Void, Void, Boolean> {

        protected void onPreExecute() {

            Log.d("ConnectTask", "onPreExecute");

        }

        protected Boolean doInBackground(Void... voids) {

            List<BluetoothDevice> devices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT);

            for (BluetoothDevice device : devices) {

                if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE && device.getName() != null) {

                    Log.e("ConnetTask", "Already connected to a device named Wilduhr");
                    if (device.getName().startsWith("Wilduhr"))
                        return true;

                }

            }

            List<ScanFilter> filters = new ArrayList<>();
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();

            ScanResults = new HashMap<>();
            final MainActivity.BLECallback blueoothScanCallback = new MainActivity.BLECallback();

            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            bluetoothLeScanner.startScan(filters, settings, blueoothScanCallback);

            try {
                Thread.sleep(getResources().getInteger(R.integer.discoverytimeout));
            } catch (InterruptedException e) {
            }

            Log.e("ConnectTask", "Stopped scan after " + String.valueOf(getResources().getInteger(R.integer.discoverytimeout)) + " ms");

            bluetoothLeScanner.stopScan(blueoothScanCallback);

            for (Map.Entry<String, ScanResult> entry : ScanResults.entrySet()) {

                String key = entry.getKey();
                ScanResult value = entry.getValue();

                if (value.getDevice().getName() != null && value.getDevice().getName().startsWith("Wilduhr")) {

                    Wilduhr = value.getDevice();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Wilduhr gefunden...", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return true;
                    break;

                }

            }
            return false;

        }

        protected void onProgressUpdate() {

        }

        protected void onPostExecute(Boolean connected) {

            Log.d("ConnectTask", "onPostExecute");

        }

    }

    private class BLECallback extends ScanCallback {

        BLECallback(){}

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d("ScanCallback", "New Result incoming...");
            addResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                addResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Error", "BLE Scan Failed with code " + errorCode);
        }

        private void addResult(ScanResult result) {

            String deviceAddress = result.getDevice().getAddress();
            ScanResults.put(deviceAddress, result);
            Log.d("Discovered Device", ScanResults.get(deviceAddress).toString());

        }
    };

}