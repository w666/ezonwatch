/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.w666.ezonwatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static android.view.View.*;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class
DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattCharacteristic ezonCharacteristic;
    private BluetoothGattCharacteristic resetEzonCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private byte[] stepsArr = null;

    private ProgressDialog dialog = null;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                final byte[] data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                displayData(new String(data) + "\n" + stringBuilder.toString());
                if (data[0] == (byte) 0x50) {
                    if (data[1] == (byte) 0x00) {
                        stepsArr = null;
                        dialog = new ProgressDialog(DeviceControlActivity.this);
                        dialog.setMessage("Reading  steps data ... ");
                        dialog.setIndeterminate(false);
                        dialog.setMax(1440);
                        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        dialog.show();
                    }
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    try {
                        if (stepsArr != null) {
                            outputStream.write(stepsArr);
                        }
                        outputStream.write( Arrays.copyOfRange(data, 2, 19));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    stepsArr = outputStream.toByteArray();
                    dialog.setProgress(stepsArr.length);
                    if (data[1] == (byte) 0x4f) {
                        dialog.dismiss();
                        showTodayStepsTarget();
                    }
                }
            }

        }
    };

    private int getStepsCount () {
        int stepsCount = 0;
        if (stepsArr == null) {
            return stepsCount;
        }
        for (int i = 0; i< stepsArr.length; i++) {
            stepsCount += stepsArr[i];
        }
        return stepsCount;
    }

    private int[] getStepsByHour () {
        int[] stepsByHourArr = new int[24];
        if (stepsArr == null) {
            return null;
        }
        for (int i = 0; i<24; i++) {
            int count = 0;
            for (int j=0; j<60; j++) {
                count += stepsArr[j+i];
            }
            stepsByHourArr[i] = count;
        }
        return stepsByHourArr;
    }

    private void showTodayStepsTarget () {
        final Dialog d = new Dialog(DeviceControlActivity.this);
        d.setTitle("Today steps target");
        d.setContentView(R.layout.daily_target);
        Button b1 = (Button) d.findViewById(R.id.daily_target_close);
        final ImageView imageView = (ImageView) d.findViewById(R.id.stepsTargetImageView);
        b1.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v) {
                d.dismiss(); // dismiss the dialog
            }
        });
        Display display = ((WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int scquareSide = 0;
        if (size.x >= size.y){
            scquareSide = (int) (size.y*0.8);
        }
        else {
            scquareSide = (int) (size.x*0.8);
        }
        Bitmap bitmap = Bitmap.createBitmap(scquareSide, scquareSide, Bitmap.Config.ARGB_8888);
        final float startAngle = 0f;
        int stepsCount = getStepsCount();
        int stepsTarget = Preferences.readStepsTarget(getApplicationContext());
        float percentile = 0f;
        if (stepsCount != 0) {
            percentile = (float) stepsCount/(stepsTarget/100)/100;
        }
        final float drawTo = startAngle + (percentile * 360);
        Canvas canvas = new Canvas(bitmap);
        //canvas.rotate(-90f, mArea.centerX(), mArea.centerY());
        canvas.rotate(-90f, scquareSide/2, scquareSide/2);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        //canvas.drawArc(mArea, startAngle, drawTo, true, paint);
        //RectF mArea = new RectF(left, top, right, bottom);
        int space = 100;
        // bold circle
        RectF rectFBold = new RectF(space+42, space+42, scquareSide-space-42, scquareSide-space-42);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(82);
        paint.setColor(Color.BLUE);
        canvas.drawArc(rectFBold, startAngle, drawTo, false, paint);
        // outter narrow circle
        RectF rectFNarrow = new RectF(space, space, scquareSide-space, scquareSide-space);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        canvas.drawArc(rectFNarrow, startAngle, 360, false, paint);
        // inner narrow circle
        rectFNarrow = new RectF(space+84, space+84, scquareSide-space-84, scquareSide-space-84);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        canvas.drawArc(rectFNarrow, startAngle, 360, false, paint);

        canvas.rotate(90f, scquareSide/2, scquareSide/2);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(scquareSide*0.1f);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(stepsCount+" steps", scquareSide/2, scquareSide/2, paint);
        imageView.setImageBitmap(bitmap);
        d.show();
    }

    private void clearUI() {
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = Preferences.readDeviceName(getApplicationContext());
        mDeviceAddress = Preferences.readDeviceAddress(getApplicationContext());

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        Button mEnableAlarm = (Button)findViewById(R.id.enable_alarm);
        mEnableAlarm.setOnClickListener(btnListener);
        Button mDisableAlarm = (Button)findViewById(R.id.disable_alarm);
        mDisableAlarm.setOnClickListener(btnListener);
        Button mSyncTime = (Button)findViewById(R.id.sync_time);
        mSyncTime.setOnClickListener(btnListener);
        Button mReqPin = (Button)findViewById(R.id.request_pin);
        mReqPin.setOnClickListener(btnListener);
        Button mCallReminder = (Button)findViewById(R.id.call_reminder);
        mCallReminder.setOnClickListener(btnListener);
        Button mGetSteps = (Button)findViewById(R.id.get_steps);
        mGetSteps.setOnClickListener(btnListener);
        Button mConnect = (Button)findViewById(R.id.connect);
        mConnect.setOnClickListener(btnListener);
        Button mSetTarget = (Button)findViewById(R.id.set_target);
        mSetTarget.setOnClickListener(btnListener);
        Button mDisableTarget = (Button)findViewById(R.id.disable_target);
        mDisableTarget.setOnClickListener(btnListener);
    }

    public void writeMain(byte[] data) {
        ezonCharacteristic.setValue(data);
        if (!mConnected) {
            mBluetoothLeService.connect(mDeviceAddress);
        }
        writeCharacteristic(ezonCharacteristic);
    }

    public void writeMain (String strData) {
        byte[] data = hexStringToByteArray(strData);
        ezonCharacteristic.setValue(data);
        if (!mConnected) {
            mBluetoothLeService.connect(mDeviceAddress);
        }
        writeCharacteristic(ezonCharacteristic);
    }

    public void writeReset (String strData) {
        byte[] data = hexStringToByteArray(strData);
        resetEzonCharacteristic.setValue(data);
        if (!mConnected) {
            mBluetoothLeService.connect(mDeviceAddress);
        }
        writeCharacteristic(resetEzonCharacteristic);
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        mBluetoothLeService.writeCharacteristic(characteristic);
    }

    private OnClickListener btnListener = new OnClickListener()
    {
        @Override
        public void onClick(View v) {
            if (mBluetoothLeService != null && ezonCharacteristic != null) {
                mBluetoothLeService.setCharacteristicNotification(ezonCharacteristic,true);
                switch (v.getId()) {
                    case R.id.enable_alarm: {
                        TimePickerDialog mTimePicker;
                        mTimePicker = new TimePickerDialog(DeviceControlActivity.this, new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
                                byte[] time = {(byte) selectedHour, (byte) selectedMinute};
                                //byte[] data = hexStringToByteArray("414301080045");
                                byte[] prefix = hexStringToByteArray("414301");
                                byte[] enable = {0x45};
                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
                                try {
                                    outputStream.write( prefix );
                                    outputStream.write( time );
                                    outputStream.write( enable );
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                byte data[] = outputStream.toByteArray( );
                                writeMain(data);
                            }
                        }, 8, 0, true);//Yes 24 hour time
                        mTimePicker.setTitle("Select Time");
                        mTimePicker.show();
                        break;
                    }
                    case R.id.disable_alarm: {
                        AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);
                        builder.setMessage("Disable alarm?")
                                .setCancelable(true)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        byte[] data = hexStringToByteArray("414301000044");
                                        writeMain(data);
                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.dismiss();
                                    }
                        });
                        AlertDialog alert = builder.create();
                        alert.show();
                        break;
                    }
                    case R.id.sync_time: {
                        /*
                        54 49 4d 45 | 07 e0 | 09  | 1d | 0f | 04 | 2b | 00     | d002 | 000000000000
                        just text   | year  |month|day |hour|min |sec | 12/24H | EOM  | nothing, just zeros
                         T  I  M  E | 2016  | 09  | 29 | 15 | 04 | 43 | 12H    |      |
                        */
                        AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);
                        builder.setMessage("Sync time?")
                                .setCancelable(true)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        String timeStr = "TIME";
                                        byte[] bytes = timeStr.getBytes(Charset.forName("US-ASCII"));
                                        Date date = Calendar.getInstance().getTime();
                                        Calendar cal = Calendar.getInstance();
                                        cal.setTime(date);
                                        byte[] datetime = {(byte) (cal.get(Calendar.YEAR) >> 8), (byte) cal.get(Calendar.YEAR), (byte) (cal.get(Calendar.MONTH)+1), (byte) cal.get(Calendar.DAY_OF_MONTH),
                                                (byte) cal.get(Calendar.HOUR_OF_DAY), (byte) cal.get(Calendar.MINUTE), (byte) cal.get(Calendar.SECOND), (byte) 0x01, (byte) 0xd0, (byte) 0x02};
                                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
                                        try {
                                            outputStream.write( bytes );
                                            outputStream.write( datetime );
                                            //outputStream.write( hexStringToByteArray("000000000000") );
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        byte data[] = outputStream.toByteArray( );
                                        writeMain(data);
                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog alert = builder.create();
                        alert.show();
                        break;
                    }
                    case R.id.request_pin: {
                        AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);
                        builder.setMessage("Request pin?")
                                .setCancelable(true)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        byte[] data = hexStringToByteArray("430003090608");
                                        writeMain(data);
                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.dismiss();
                                    }
                        });
                        AlertDialog alert = builder.create();
                        alert.show();
                        break;
                    }
                    case R.id.call_reminder: {
                        AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);
                        builder.setMessage("Display call reminder on the watch?")
                                .setCancelable(true)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        byte[] data = hexStringToByteArray("4e00455a4f4e4950484f4e45");
                                        writeMain(data);
                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog alert = builder.create();
                        alert.show();
                        break;
                    }
                    case R.id.connect: {
                        // yes, it really checks all those zeros
                        AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);
                        builder.setMessage("Connect again?")
                                .setCancelable(true)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        byte[] data = hexStringToByteArray("4300000000000000000000000000000000000000");
                                        writeMain(data);
                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog alert = builder.create();
                        alert.show();
                        break;
                    }
                    case R.id.get_steps: {
                        // Get list of available files
                        //byte[] data = hexStringToByteArray("4301000000000000000000000000000000000000");
                        AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);
                        builder.setMessage("Get steps?")
                                .setCancelable(true)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        Date date = Calendar.getInstance().getTime();
                                        Calendar cal = Calendar.getInstance();
                                        cal.setTime(date);
                                        // Get stepsArr data for Today
                                        byte[] data = {(byte) 0x43, (byte) 0x02, (byte) (cal.get(Calendar.YEAR)-2000), (byte) (cal.get(Calendar.MONTH)+1),(byte) cal.get(Calendar.DAY_OF_MONTH), (byte) 0xd0, (byte) 0x02};
                                        writeMain(data);
                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog alert = builder.create();
                        alert.show();
                        break;
                    }
                    case R.id.set_target: {
                        final Dialog d = new Dialog(DeviceControlActivity.this);
                        d.setTitle("Daily stepsArr target");
                        d.setContentView(R.layout.target_picker);
                        Button b1 = (Button) d.findViewById(R.id.stepsSet);
                        Button b2 = (Button) d.findViewById(R.id.stepsCancel);
                        final NumberPicker np = (NumberPicker) d.findViewById(R.id.stepsNumberPicker);
                        np.setEnabled(true);
                        final String[] targetValues = {"1000","2000","3000", "4000", "5000", "6000", "7000", "8000", "9000", "10000"};
                        np.setMinValue(0);
                        np.setMaxValue(targetValues.length - 1);
                        np.setValue(Preferences.readStepsTarget(getApplicationContext()));
                        np.setWrapSelectorWheel(false);
                        np.setDisplayedValues(targetValues);
                        b1.setOnClickListener(new OnClickListener()
                        {
                            @Override
                            public void onClick(View v) {
                                String timeStr = "STARGETE";
                                byte[] bytes = timeStr.getBytes(Charset.forName("US-ASCII"));
                                Preferences.writeStepsTarget(getApplicationContext(),Integer.parseInt(targetValues[np.getValue()]));
                                byte target  = (byte) (Integer.parseInt(targetValues[np.getValue()])/100);
                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
                                try {
                                    outputStream.write( bytes );
                                    outputStream.write( target );
                                    outputStream.write(hexStringToByteArray("0000000000000000000000"));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                byte data[] = outputStream.toByteArray( );
                                writeMain(data);
                                //byte[] data = hexStringToByteArray("4301000000000000000000000000000000000000");
                                //writeMain(data);
                                d.dismiss();
                            }
                        });
                        b2.setOnClickListener(new OnClickListener()
                        {
                            @Override
                            public void onClick(View v) {
                                d.dismiss(); // dismiss the dialog
                            }
                        });
                        d.show();
                        break;
                    }
                    case R.id.disable_target: {
                        AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);
                        builder.setMessage("Disable Target?")
                                .setCancelable(true)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        String timeStr = "STARGETD";
                                        byte[] data = timeStr.getBytes(Charset.forName("US-ASCII"));
                                        writeMain(data);
                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog alert = builder.create();
                        alert.show();
                        break;
                    }
                }
            }
            else {
                displayData("both service and char are null");
            }
        }

    };

    public byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
                if (gattCharacteristic.getUuid().equals(mBluetoothLeService.UUID_EZON_MAIN)) {
                    ezonCharacteristic = gattCharacteristic;
                    displayData("EZON Characteristic found " + gattCharacteristic.getUuid().toString());
                }
            }
            mGattCharacteristics.add(charas);
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
