package de.plus_mint.cederik.wilduhr;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.support.constraint.ConstraintLayout;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScanActivity extends Activity {

    ListView devicelist;
    ConstraintLayout bluetoothwarning;
    TextView bluetoothdisabled;
    Button enablebluetooth;
    ProgressBar bluetoothenabling;
    ConstraintLayout locationwarning;
    TextView locationdisabled;
    Button enablelocation;
    Button doscan;
    ProgressBar scanning;
    ConstraintLayout scanheader;


    LocationManager locationManager;

    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothLeScanner bluetoothLeScanner;
    BLEScanCallback bluetoothScanCallback;

    BroadcastReceiver receiver;

    private Map<String, ScanResult> ScanResults = new HashMap<String, ScanResult>();
    volatile boolean Scanning = false;

    BluetoothDevice Wilduhr;

    boolean isBluetoothEnabled(){
        return BluetoothAdapter.getDefaultAdapter().isEnabled();
    }

    boolean isLocationEnabled(){
        return ((LocationManager) getSystemService(Context.LOCATION_SERVICE)).isLocationEnabled();
    }

    boolean enableBluetooth(){
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothAdapter.enable();
        return (bluetoothAdapter != null);
    }

    boolean initBT(){

        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);

        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){

            return false;

        }

        //bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {

            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);

        }

        //while(!bluetoothAdapter.isEnabled());

        return true;

    }

    boolean initLocation() {

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if(!locationManager.isLocationEnabled()) {
            buildAlertMessageNoGps();
        }

        return true;
    }

    void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Standortzugriff ist für BLE zwingend notwendig.")
                .setCancelable(false)
                .setPositiveButton("Zulassen", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("Nicht zulassen", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    void initReceiver(){

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String action = intent.getAction();

                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {

                    switch(bluetoothAdapter.getState()){

                        case BluetoothAdapter.STATE_OFF:
                            onBluetoothDisabled();
                            break;

                        case BluetoothAdapter.STATE_ON:
                            onBluetoothEnabled();
                            break;

                        default:
                            break;
                    }

                }
                else if(action.equals(LocationManager.PROVIDERS_CHANGED_ACTION)){
                    if(isLocationEnabled()){
                        onLocationEnabled();
                    }
                    else{
                        onLocationDisabled();
                    }
                }

            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);

        this.registerReceiver(receiver, filter);
    }
    void deinitReceiver(){

        this.unregisterReceiver(receiver);

    }

    void showBTWarning(){
        bluetoothwarning.setVisibility(View.VISIBLE);
        enablebluetooth.setVisibility(View.VISIBLE);
        bluetoothenabling.setVisibility(View.INVISIBLE);
    }
    void whileBTEnabling(){
        enablebluetooth.setVisibility(View.INVISIBLE);
        bluetoothenabling.setVisibility(View.VISIBLE);
    }
    void hideBTWarning(){
        bluetoothwarning.setVisibility(View.GONE);
    }

    void showLocationWarning(){
        locationwarning.setVisibility(View.VISIBLE);
        enablelocation.setVisibility(View.VISIBLE);
    }
    void hideLocationWarning(){
        locationwarning.setVisibility(View.GONE);
    }

    void showScanHeader(){
        scanheader.setVisibility(View.VISIBLE);
    }
    void hideScanHeader(){
        scanheader.setVisibility(View.INVISIBLE);
    }

    void onBluetoothDisabled(){
        showBTWarning();
        onEssentialsDisabled();
    }
    void onBluetoothEnabled(){
        hideBTWarning();
        onEssentialsEnabled();
    }

    void onLocationDisabled(){
        showLocationWarning();
        onEssentialsDisabled();
    }
    void onLocationEnabled(){
        hideLocationWarning();
        onEssentialsEnabled();
    }

    void onEssentialsEnabled(){
        if(isBluetoothEnabled() && isLocationEnabled())
            showScanHeader();
    }
    void onEssentialsDisabled(){
        if(!isBluetoothEnabled() || !isLocationEnabled())
            hideScanHeader();
    }


    void onScanStart(){
        Scanning = true;

        doscan.setText("STOP");
        doscan.setTextColor(getResources().getColor(R.color.errred));

        scanning.setVisibility(View.VISIBLE);
        ScanResults = new HashMap<>();
        ((BaseAdapter)devicelist.getAdapter()).notifyDataSetChanged();
    }
    void onScanStop(){
        Scanning = false;

        doscan.setText("SCAN");
        doscan.setTextColor(getResources().getColor(R.color.goodgreen));

        scanning.setVisibility(View.INVISIBLE);
    }

    void initUI(){

        devicelist = (ListView)findViewById(R.id.devicelist);
        bluetoothwarning = (ConstraintLayout)findViewById(R.id.btwarning);
        bluetoothdisabled = (TextView) findViewById(R.id.btdisabled);
        enablebluetooth = (Button)findViewById(R.id.enablebluetooth);
        bluetoothenabling = (ProgressBar)findViewById(R.id.btenabling);
        locationwarning = (ConstraintLayout)findViewById(R.id.locationwarning);
        locationdisabled = (TextView) findViewById(R.id.locationdisabled);
        enablelocation = (Button)findViewById(R.id.enablelocation);
        scanheader = (ConstraintLayout)findViewById(R.id.scanheader);
        doscan = (Button)findViewById(R.id.scan);
        scanning = (ProgressBar)findViewById(R.id.scanning);

        devicelist.setAdapter(new BluetoothDeviceAdapter(this));

        devicelist.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView parentView, View childView,
                                       int position, long id)
            {
                Log.d("onDeviceChosen", ((BluetoothDevice)devicelist.getAdapter().getItem(position)).toString());

                stopscan();

                startMainMenu((BluetoothDevice)devicelist.getAdapter().getItem(position));
            }

        });

        enablebluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enableBluetooth();
                whileBTEnabling();
            }
        });

        enablelocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initLocation();
            }
        });

        doscan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!Scanning){
                    startscan();
                }
                else{
                    stopscan();
                }
            }
        });
    }

    void startscan(){

        onScanStart();

        List<ScanFilter> filters = new ArrayList<>();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();

        bluetoothScanCallback = new BLEScanCallback();

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        bluetoothLeScanner.startScan(filters, settings, bluetoothScanCallback);
    }
    void stopscan(){
        bluetoothLeScanner.stopScan(bluetoothScanCallback);
        onScanStop();
    }

    void startMainMenu(BluetoothDevice device){
        Log.d("BTDevice", device.toString());

        Intent intent = new Intent(this, WilduhrMenuActivity.class);
        intent.putExtra("Wilduhr", device);
        startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();
        initBT();
        initLocation();
        initReceiver();

        if(!isBluetoothEnabled())
            onBluetoothDisabled();

        if(!isLocationEnabled())
            onLocationDisabled();

        if(isBluetoothEnabled() && isLocationEnabled())
            onEssentialsEnabled();

    }

    @Override
    protected void onStop(){
        super.onStop();
        deinitReceiver();
    }

    private class BluetoothDeviceAdapter extends BaseAdapter {

        private Context context;
        //private ArrayList<BluetoothDevice> items;

        private class ViewHolder {
            TextView name;
            TextView rssi;
            TextView mac;
        }

        public BluetoothDeviceAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getCount() {
            return ScanResults.values().size();
        }

        @Override
        public Object getItem(int position) {
            return (new ArrayList<ScanResult>(ScanResults.values()).get(position)).getDevice();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder viewholder;

            if (convertView == null) {
                viewholder = new ViewHolder();
                convertView = getLayoutInflater().inflate(R.layout.devicelistentry, parent, false);

                viewholder.name = (TextView) convertView.findViewById(R.id.name);
                viewholder.rssi = (TextView) convertView.findViewById(R.id.rssi);
                viewholder.mac = (TextView) convertView.findViewById(R.id.mac);

                convertView.setTag(viewholder);
            }
            else{
                viewholder = (ViewHolder)convertView.getTag();
            }


            BluetoothDevice device= (BluetoothDevice) getItem(position);

            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView rssi =(TextView) convertView.findViewById(R.id.rssi);
            TextView mac = (TextView)convertView.findViewById(R.id.mac);

            name.setText(device.getName());
            rssi.setText(ScanResults.get(device.getAddress()).getRssi() + "dBm");
            mac.setText(device.getAddress());

            // returns the view for the current row
            return convertView;
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
            final ScanActivity.BLEScanCallback blueoothScanCallback = new ScanActivity.BLEScanCallback();

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

    private class BLEScanCallback extends ScanCallback {

        BLEScanCallback(){}

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

            if(result.getDevice().getName() != null) {

                String deviceAddress = result.getDevice().getAddress();
                ScanResults.put(deviceAddress, result);
                ((BaseAdapter) devicelist.getAdapter()).notifyDataSetChanged();
                Log.d("Discovered Device", ScanResults.get(deviceAddress).toString());

            }
        }
    };

}