package de.plus_mint.cederik.wilduhr;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.util.List;
import java.util.UUID;

public class WilduhrMenuActivity extends AppCompatActivity {

    Wilduhr wu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wilduhr_menu);

        wu = new Wilduhr((BluetoothDevice)getIntent().getParcelableExtra("Wilduhr"));
        wu.connect();
    }

    class Wilduhr extends BluetoothGattCallback{

        final UUID WU_SERVICE =             UUID.fromString("00002020-9CBF-4F73-A92F-DAD011379CA9");
        final UUID WU_CMD_CHARACTERISTIC =  UUID.fromString("00002021-9CBF-4F73-A92F-DAD011379CA9");
        final UUID WU_DATA_CHARACTERISTIC = UUID.fromString("00002022-9CBF-4F73-A92F-DAD011379CA9");

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

        volatile boolean cmd_ready = false;
        volatile boolean data_ready = false;

        Wilduhr(BluetoothDevice device){
            bleDevice = device;
        }

        boolean connect(){

            bleGATT = bleDevice.connectGatt(getApplicationContext(), false, this);
            return (bleGATT != null);
        }
        void disconnect(){
            bleGATT.disconnect();
        }

        boolean writeCMD(byte[] val){

            cmd_ready = false;
            CMD.setValue(val);
            bleGATT.writeCharacteristic(CMD);
            Log.d("Wilduhr.writeCMD", "Wrote Characteristic.");
            long startTime = System.currentTimeMillis();
            //while(!cmd_ready || (System.currentTimeMillis() - startTime) < 1000);
            return cmd_ready;
            
        }
        byte[] readDATA(){

            long startTime = System.currentTimeMillis();
            while(!data_ready || (System.currentTimeMillis() - startTime) < 1000);
            if(data_ready)
                return DATA.getValue();
            else
                return null;

        }

        String getVersion(){
            Log.d("Wilduhr", "getVersion");
            if(cmd_ready){
                final byte[] GET_VERSION = new byte[]{WU_CMD_GET_VERSION};

                writeCMD(GET_VERSION);
                byte[] response = readDATA();

                Log.d("Wilduhr.getVersion", response.toString());

                return new String(response);
            }
            else{
                return null;
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d("Wilduhr", "Connected");

                gatt.discoverServices();
                Log.d("Wilduhr", "Discovering Services...");
            }
            else if(newState==BluetoothGatt.STATE_DISCONNECTED){

                Log.d("Wilduhr", "Disconnected");
                cmd_ready = false;

            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            serviceList = gatt.getServices();

            Log.d("Wilduhr", "Services discovered. " + serviceList.toString());

            CMD = gatt.getService(WU_SERVICE).getCharacteristic(WU_CMD_CHARACTERISTIC);
            DATA = gatt.getService(WU_SERVICE).getCharacteristic(WU_DATA_CHARACTERISTIC);
            gatt.setCharacteristicNotification(DATA, true);

            cmd_ready = true;
            getVersion();

        }

        @Override
        public void onCharacteristicChanged (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic){

            gatt.readCharacteristic(characteristic);
            Log.d("onCharacteristicChanged", characteristic.getValue().toString());

            if(characteristic == DATA)
                data_ready = true;

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){
            Log.d("OnCharacteristicWrite", String.valueOf(status));
            if(status == BluetoothGatt.GATT_SUCCESS)
                cmd_ready = true;
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){
            Log.d("OnCharacteristicRead", String.valueOf(status));
            if(status == BluetoothGatt.GATT_SUCCESS)
                cmd_ready = true;
        }

    };

}
