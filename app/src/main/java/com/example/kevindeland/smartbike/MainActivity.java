package com.example.kevindeland.smartbike;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SensorEventListener {


    private static final String TAG = "BIKE";

    //
    // Sensor things
    //
    private SensorManager sensorManager;
    private Sensor orientationSensor;
    private Sensor lightSensor;

    //
    // turning things
    //
    // transition states
    private final static String LEFT_TURN_STATE = "left";
    private final static String RIGHT_TURN_STATE = "right";
    private final static String STEADY_STATE = "steady";
    private String currentState;

    private final static int TURN_THRESHOLD = 6;
    private final static int TURN_STREAK_THRESHOLD = 20;
    int leftThresholdStreak = 0;
    int rightThresholdStreak = 0;

    private final static int UNTURN_THRESHOLD = 2;
    private final static int UNTURN_STREAK_THRESHOLD = 20;
    int unturnStreak = 0;


    //
    // lighting things
    //
    TextView lightView;
    private final static String LOW_LIGHT_STATE = "low";
    private final static String BRIGHT_LIGHT_STATE = "bright";
    private String lightState = BRIGHT_LIGHT_STATE;

    private final static int LOW_LIGHT_THRESHOLD = 10;
    private final static int LOW_LIGHT_STREAK_THRESHOLD = 20;
    int lowThresholdStreak = 0;
    private final static int BRIGHT_LIGHT_THRESHOLD = 20;
    private final static int BRIGHT_LIGHT_STREAK_THRESHOLD = 20;
    int brightThresholdStreak = 0;

    //
    // Bluetooth things
    //
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice = null;
    BluetoothAdapter mBluetoothAdapter;

    ParcelUuid[] partnerParcel = null;
    UUID piUUID = null;

    final class workerThread implements Runnable {

        private String btMsg;

        public workerThread(String msg) {
            btMsg = msg;
        }

        @Override
        public void run() {
            sendBtMsg(btMsg);

        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Sensor thingssiz
        sensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        sensorManager.registerListener(this, orientationSensor, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);

        lightView = (TextView) findViewById(R.id.lightValue);

        currentState = STEADY_STATE;

        // initialize mBluetoothAdapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();


        if(!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }



        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0) {
            Log.d(TAG, "found >0 paired devices");
            boolean foundPi = false;
            for(BluetoothDevice device : pairedDevices) {
                // had to add Blue Z... pretty weird
                if(device.getName().contains("pi") || device.getName().contains("BlueZ")) {
                    Log.e(TAG, "found device " + device.getName() + " at " + device.getAddress());


                    partnerParcel = device.getUuids();
                    for (ParcelUuid uuid : device.getUuids())
                        Log.e(TAG, "has uuid " + uuid);

                    mmDevice = device;

                    device.fetchUuidsWithSdp();
                    foundPi = true;


                    partnerParcel = device.getUuids();
                    updateConnectionDisplay("paired with " + device.getName());

                    break;
                }
            }
            if(!foundPi) {
                Log.d(TAG, "did not find pi");
                updateConnectionDisplay("no pi");
            }

        } else {
            Log.d(TAG, "found 0 paired devices");
            updateConnectionDisplay("no pi");

        }

    }

    public void connectToBluetooth(View view) {

        try {
            UUID uuid = partnerParcel[0].getUuid();

            Log.d(TAG, "Connecting to bluetooth at " + uuid.toString());
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);


            if(!mmSocket.isConnected()) {
                Log.d(TAG, "not connected... connecting");
                mmSocket.connect();
                updateConnectionDisplay("connected");
            } else {
                Log.d(TAG, "connection already open");
                updateConnectionDisplay("connected");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor mySensor = sensorEvent.sensor;


        if (mySensor.getType() == Sensor.TYPE_ORIENTATION) {
            processOrientationData(sensorEvent);
        } else if (mySensor.getType() == Sensor.TYPE_LIGHT) {
            processLightSensor(sensorEvent);
        }


    }

    private void processLightSensor(SensorEvent sensorEvent) {

        int lightVal = (int) sensorEvent.values[0];
        lightView.setText(" " + lightVal + "\n" + lightState);

        // TODO... mimic below code

        // if state is bright
        if (lightState == BRIGHT_LIGHT_STATE) {
            /// if light is less than threshold
            if (lightVal <= LOW_LIGHT_THRESHOLD) {
                // increment low_light_threshold_counter
                lowThresholdStreak++;

                // if counter > counter_threshold
                if(lowThresholdStreak > LOW_LIGHT_STREAK_THRESHOLD) {
                    lightState = LOW_LIGHT_STATE;
                    Log.d(TAG, "switching to low light state");
                    // send low light message
                    if(mmSocket != null)
                        (new Thread(new workerThread(LOW_LIGHT_STATE))).start();

                }
            } else {
                lowThresholdStreak = 0;
            }
        } else if (lightState == LOW_LIGHT_STATE) {

            if (lightVal >= BRIGHT_LIGHT_THRESHOLD) {
                brightThresholdStreak++;

                if(brightThresholdStreak > BRIGHT_LIGHT_STREAK_THRESHOLD) {
                    lightState = BRIGHT_LIGHT_STATE;
                    Log.d(TAG, "switching to bright light state");
                    // send bright light message
                    if(mmSocket != null)
                        (new Thread(new workerThread(BRIGHT_LIGHT_STATE))).start();
                }
            } else {
                brightThresholdStreak = 0;
            }
        }


    }

    private void processOrientationData(SensorEvent sensorEvent) {

        int z = (int) sensorEvent.values[2];

        if (z >= TURN_THRESHOLD) {
            leftThresholdStreak++;

            if(leftThresholdStreak >= TURN_STREAK_THRESHOLD) {
                currentState = LEFT_TURN_STATE;
                updateStateDisplay(LEFT_TURN_STATE);
            }
        } else {
            leftThresholdStreak = 0;
        }

        if (z*-1 >= TURN_THRESHOLD) {
            rightThresholdStreak++;

            if(rightThresholdStreak >= TURN_STREAK_THRESHOLD) {
                currentState = RIGHT_TURN_STATE;
                updateStateDisplay(RIGHT_TURN_STATE);
            }
        } else {
            rightThresholdStreak = 0;
        }


        if(currentState.equals(LEFT_TURN_STATE)) {
            if(z < UNTURN_THRESHOLD) {
                unturnStreak++;

                if(unturnStreak >= UNTURN_STREAK_THRESHOLD) {
                    unturnStreak = 0;
                    currentState = STEADY_STATE;
                    updateStateDisplay(STEADY_STATE);
                    if(mmSocket != null)
                        (new Thread(new workerThread(LEFT_TURN_STATE))).start();
                }
            } else {
                unturnStreak = 0;
            }
        } else if (currentState.equals(RIGHT_TURN_STATE)) {
            if (z * -1 < UNTURN_THRESHOLD) {
                unturnStreak++;

                if(unturnStreak >= UNTURN_STREAK_THRESHOLD) {
                    unturnStreak = 0;
                    currentState = STEADY_STATE;
                    updateStateDisplay(STEADY_STATE);
                    if(mmSocket != null)
                        (new Thread(new workerThread(RIGHT_TURN_STATE))).start();
                }
            }

        } else {
            // DO NOTHING
        }
    }

    public void sendBtMsg(String turnState) {

        try {

            // if not connected, connect
            if(mmSocket != null && !mmSocket.isConnected()) {
                mmSocket.connect();
            }

            // write message
            OutputStream mmOutputStream = mmSocket.getOutputStream();
            mmOutputStream.write(turnState.getBytes());

        } catch (IOException e) {
            Log.d(TAG, "Connection Lost");
            //updateUserInterface("Connection", "Connection failed");

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateConnectionDisplay("disconnected");
                }
            });
            e.printStackTrace();
        }
    }


    private void updateStateDisplay(String turnState) {
        TextView textView = (TextView) findViewById(R.id.stateDisplay);
        textView.setText(turnState);
    }

    private void updateConnectionDisplay(String state) {
        TextView textView = (TextView) findViewById(R.id.connectionStatus);
        textView.setText(state);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    @Override
    public void onPause() {
        super.onPause();

        /*
        try {

            if(mmSocket != null && mmSocket.isConnected()) {
                mmSocket.close();

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        */
    }

    @Override
    public void onResume() {
        super.onResume();

        if(mmSocket != null && mmSocket.isConnected()) {
           updateConnectionDisplay("connected");
        } else {
            updateConnectionDisplay("disconnected");
        }
        // commented out, as Pi doesn't accept new connections
        /*
        try {

            if(mmSocket == null) {

            }

            if(!mmSocket.isConnected()) {
                mmSocket.connect();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        */
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {

            if(mmSocket != null && mmSocket.isConnected()) {
                mmSocket.close();

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
