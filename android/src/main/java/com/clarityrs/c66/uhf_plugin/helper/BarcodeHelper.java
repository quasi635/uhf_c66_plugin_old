package com.clarityrs.c66.uhf_plugin.helper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.rscja.barcode.BarcodeUtility;

/**
 * Drives the C66 scanner via {@link BarcodeUtility} in broadcast output mode.
 *
 * The C66 ships with the Chainway scanner service running in "keyboard
 * emulator" mode (output mode 3) — decoded scans are typed as keystrokes into
 * whichever view has focus, and the in-app {@code BarcodeDecoder} path returns
 * a stub that throws on {@code setTimeOut}. Switching to broadcast output mode
 * (2) and registering a {@link BroadcastReceiver} lets us programmatically
 * start/stop scans and receive results regardless of focus.
 */
public class BarcodeHelper {
    private static final String TAG = "BarcodeHelper";

    private static final String SCAN_ACTION = "com.clarityrs.c66.uhf_plugin.SCAN_RESULT";
    private static final String SCAN_EXTRA = "scan_data";
    private static final int OUTPUT_MODE_BROADCAST = 2;

    private static BarcodeHelper instance;

    public interface BarcodeListener {
        /**
         * @param resultCode one of {@link #RESULT_SUCCESS}, {@link #RESULT_FAILURE}
         * @param data decoded text on success, null on failure
         */
        void onDecode(int resultCode, String data);
    }

    public static final int RESULT_SUCCESS = 1;
    public static final int RESULT_FAILURE = 2;

    private final Context appContext;
    private final BarcodeUtility utility;
    private BarcodeListener listener;
    private BroadcastReceiver receiver;
    private boolean opened = false;

    private BarcodeHelper(Context context) {
        this.appContext = context.getApplicationContext();
        this.utility = BarcodeUtility.getInstance();
    }

    public static synchronized BarcodeHelper getInstance(Context context) {
        if (instance == null) instance = new BarcodeHelper(context);
        return instance;
    }

    public void setListener(BarcodeListener listener) {
        this.listener = listener;
    }

    public synchronized boolean open() {
        if (opened) return true;
        try {
            utility.setOutputMode(appContext, OUTPUT_MODE_BROADCAST);
            utility.setScanResultBroadcast(appContext, SCAN_ACTION, SCAN_EXTRA);
            utility.enableContinuousScan(appContext, false);
            utility.enablePlaySuccessSound(appContext, true);
            utility.open(appContext, BarcodeUtility.ModuleType.BARCODE_2D);
            registerReceiver();
            opened = true;
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "open failed", t);
            return false;
        }
    }

    public synchronized boolean startScan() {
        if (!opened && !open()) return false;
        try {
            utility.startScan(appContext, BarcodeUtility.ModuleType.BARCODE_2D);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "startScan failed", t);
            return false;
        }
    }

    public synchronized boolean stopScan() {
        if (!opened) return false;
        try {
            utility.stopScan(appContext, BarcodeUtility.ModuleType.BARCODE_2D);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "stopScan failed", t);
            return false;
        }
    }

    public synchronized void close() {
        if (!opened) return;
        try {
            utility.stopScan(appContext, BarcodeUtility.ModuleType.BARCODE_2D);
            utility.close(appContext, BarcodeUtility.ModuleType.BARCODE_2D);
        } catch (Throwable t) {
            Log.e(TAG, "close failed", t);
        }
        unregisterReceiver();
        opened = false;
    }

    private void registerReceiver() {
        if (receiver != null) return;
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (!SCAN_ACTION.equals(intent.getAction())) return;
                String data = intent.getStringExtra(SCAN_EXTRA);
                if (listener == null) return;
                if (data != null && !data.isEmpty()) {
                    listener.onDecode(RESULT_SUCCESS, data);
                } else {
                    listener.onDecode(RESULT_FAILURE, null);
                }
            }
        };
        IntentFilter filter = new IntentFilter(SCAN_ACTION);
        // Registered in the plugin's application context; no export needed.
        appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterReceiver() {
        if (receiver == null) return;
        try {
            appContext.unregisterReceiver(receiver);
        } catch (Throwable ignored) {
        }
        receiver = null;
    }
}
