package de.plus_mint.cederik.wilduhr;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class WilduhrMenuActivity extends AppCompatActivity {

    Wilduhr wu;

    TextView devicename;
    Button disconnect;
    Button getinfo;

    void initUI(){
        devicename = (TextView)findViewById(R.id.devicename);
        disconnect = (Button)findViewById(R.id.disconnect);
        getinfo = (Button)findViewById(R.id.getinfo);

        disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wu.disconnect();
                finish();
            }
        });

        getinfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wilduhr_menu);

        initUI();

        wu = new Wilduhr((BluetoothDevice)getIntent().getParcelableExtra("Wilduhr"));
        wu.connect();

        devicename.setText(wu.getName());

    }

    class Wilduhr extends BluetoothGattCallback{

        final UUID WU_SERVICE =             UUID.fromString("00002020-9CBF-4F73-A92F-DAD011379CA9");
        final UUID WU_CMD_CHARACTERISTIC =  UUID.fromString("00002021-9CBF-4F73-A92F-DAD011379CA9");
        final UUID WU_DATA_CHARACTERISTIC = UUID.fromString("00002022-9CBF-4F73-A92F-DAD011379CA9");
        final UUID CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

        final byte WU_CMD_ACK = 0x00;
        final byte WU_CMD_GET_VERSION = 0x01;
        final byte WU_CMD_GET_STAMP_COUNT = 0x02;
        final byte WU_CMD_GET_MAX_STAMPS = 0x03;
        final byte WU_CMD_READ_STAMPS = 0x04;
        final byte WU_CMD_DELETE_STAMPS = 0x05;
        final byte WU_CMD_GET_TIME = 0x06;
        final byte WU_CMD_SET_TIME = 0x07;
        final byte WU_CMD_GET_DELAY = 0x08;
        final byte WU_CMD_SET_DELAY = 0x09;

        BluetoothDevice bleDevice;
        BluetoothGatt bleGATT;
        BluetoothGattService WUService;
        BluetoothGattCharacteristic CMD;
        BluetoothGattCharacteristic DATA;
        List<BluetoothGattService> serviceList;

        volatile boolean deviceReady = false;
        volatile boolean dataAvailable = false;

        Wilduhr(BluetoothDevice device){
            bleDevice = device;
        }

        void awaitCompletion(){
            Log.d("Wilduhr", "Waiting for deviceReady...");
            while(!deviceReady);
            Log.d("Wilduhr", "Device is ready.");
        }

        boolean connect(){

            bleGATT = bleDevice.connectGatt(getApplicationContext(), false, this);
            awaitCompletion();
            return (bleGATT != null);
        }
        void disconnect(){
            bleGATT.disconnect();
        }

        void writeCMD(byte[] val){

            awaitCompletion();

            deviceReady = false;

            CMD.setValue(val);
            Log.d("Wilduhr.writeCMD", "Characteristic Value: " + Arrays.toString(val));
            CMD.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            if(!bleGATT.writeCharacteristic(CMD)){
                Log.e("Wilduhr.writeCMD", "Failed!");
                return;
            }

            awaitCompletion();

            Log.d("Wilduhr.writeCMD", "Wrote Characteristic.");
            
        }
        byte[] readDATA(){
            awaitCompletion();
            while(!dataAvailable);
            Log.d("Wilduhr.readDATA", "Data available!");
            dataAvailable = false;
            return DATA.getValue();
        }

        String getName(){return bleDevice.getName();}
        String getVersion(){
            Log.d("Wilduhr", "getVersion");

            final byte[] GET_VERSION = new byte[]{WU_CMD_GET_VERSION};

            writeCMD(GET_VERSION);
            byte[] response = readDATA();
            if(response != null) {

                Log.d("Wilduhr.getVersion", new String(response).trim());

                return new String(response).trim();
            }
            else
                return null;
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d("Wilduhr", "Connected " + String.valueOf(gatt == bleGATT));

                bleGATT.discoverServices();
                Log.d("Wilduhr", "Discovering Services...");
            }
            else if(newState==BluetoothGatt.STATE_DISCONNECTED){

                Log.d("Wilduhr", "Disconnected");
                deviceReady = false;

            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            serviceList = gatt.getServices();

            Log.d("Wilduhr", "Services discovered. " + serviceList.toString());

            CMD = gatt.getService(WU_SERVICE).getCharacteristic(WU_CMD_CHARACTERISTIC);
            DATA = gatt.getService(WU_SERVICE).getCharacteristic(WU_DATA_CHARACTERISTIC);

            gatt.setCharacteristicNotification(DATA, true);
            BluetoothGattDescriptor descriptor = DATA.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);;
            bleGATT.writeDescriptor(descriptor);

            deviceReady = true;

        }

        @Override
        public void onCharacteristicChanged (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic){
            gatt.readCharacteristic(characteristic);
            Log.d("onCharacteristicChanged", String.valueOf(characteristic.getValue().length)+" bytes");
            dataAvailable = true;
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){
            Log.d("OnCharacteristicWrite", String.valueOf(status));
            if(status == BluetoothGatt.GATT_SUCCESS)
                deviceReady = true;
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){
            Log.d("OnCharacteristicRead", String.valueOf(status));
            if(status == BluetoothGatt.GATT_SUCCESS)
                deviceReady = true;
        }

    };

}
