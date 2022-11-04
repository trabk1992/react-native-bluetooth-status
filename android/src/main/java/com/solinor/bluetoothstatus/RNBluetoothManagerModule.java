package com.solinor.bluetoothstatus;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class RNBluetoothManagerModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

    final static String MODULE_NAME = "RNBluetoothManager";
    final static String BT_STATUS_EVENT = "bluetoothStatus";
    final static String BT_STATUS_PARAM = "status";
    final static String BT_STATUS_ON = "on";
    final static String BT_STATUS_OFF = "off";
    final static int REQUEST_ENABLE_BT = 100;
    final static String E_ACTIVITY_DOES_NOT_EXIST = "E_ACTIVITY_DOES_NOT_EXIST";

    private final ReactApplicationContext reactContext;

    private BluetoothAdapter btAdapter;

    private void sendEvent(ReactContext reactContext,
                           String eventName,
                           @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            WritableMap params = Arguments.createMap();
            if (action != null && action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        params.putString(BT_STATUS_PARAM, BT_STATUS_OFF);
                        sendEvent(reactContext, BT_STATUS_EVENT, params);
                        break;
                    case BluetoothAdapter.STATE_ON:
                        params.putString(BT_STATUS_PARAM, BT_STATUS_ON);
                        sendEvent(reactContext, BT_STATUS_EVENT, params);
                        break;
                }
            }
        }
    };

    private Promise mBluetoothPromise;
    private final ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {
        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
            if (requestCode == REQUEST_ENABLE_BT) {
                if (mBluetoothPromise != null) {
                    if (resultCode == Activity.RESULT_CANCELED) {
                        mBluetoothPromise.resolve(false);
                    } else if (resultCode == Activity.RESULT_OK) {
                        mBluetoothPromise.resolve(true);
                    }

                    mBluetoothPromise = null;
                }
            }
        }
    };

    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        reactContext.registerReceiver(receiver, filter);
    }

    public RNBluetoothManagerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        reactContext.addLifecycleEventListener(this);
        reactContext.addActivityEventListener(mActivityEventListener);
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        registerBroadcastReceiver();
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @ReactMethod
    public void getBluetoothState(Promise promise) {
        boolean isEnabled = false;
        if (btAdapter != null) {
            isEnabled = btAdapter.isEnabled();
        }
        promise.resolve(isEnabled);
    }

    @ReactMethod
    public void setBluetoothState(boolean enabled) {
        if  (btAdapter != null) {
            if (enabled) {
                btAdapter.enable();
            } else {
                btAdapter.disable();
            }
        }
    }

    @ReactMethod
    public void enableBluetooth(final Promise promise) {
        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {
            promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist");
            return;
        }
        mBluetoothPromise = promise;
        try {
            if (btAdapter != null) {
                if (!btAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    currentActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
            } else {
                promise.reject("Bluetooth not support", "Bluetooth not support");
            }
        } catch (Exception e) {
          promise.reject("exception_bluetooth", e);
          mBluetoothPromise = null;
        }
    }

    @Override
    public void onHostResume() {
        registerBroadcastReceiver();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                WritableMap params = Arguments.createMap();
                String enabled = btAdapter != null && btAdapter.isEnabled() ? BT_STATUS_ON : BT_STATUS_OFF;
                params.putString(BT_STATUS_PARAM, enabled);
                sendEvent(reactContext, BT_STATUS_EVENT, params);

            }
        }, 10);
    }

    @Override
    public void onHostPause() {

    }

    @Override
    public void onHostDestroy() {
        reactContext.unregisterReceiver(receiver);
    }
}
