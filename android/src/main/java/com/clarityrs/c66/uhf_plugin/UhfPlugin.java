package com.clarityrs.c66.uhf_plugin;

import com.clarityrs.c66.uhf_plugin.helper.BarcodeHelper;
import com.clarityrs.c66.uhf_plugin.helper.UHFHelper;
import com.clarityrs.c66.uhf_plugin.helper.UHFListener;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
//import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import android.app.Activity;
import android.content.Context;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * UhfPlugin
 */
public class UhfPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
    private Context context;
    private Activity activity;
    private Window.Callback originalWindowCallback;

    private static final String CHANNEL_IsStarted = "isStarted";
    private static final String CHANNEL_StartSingle = "startSingle";
    private static final String CHANNEL_StartContinuous = "startContinuous";
    private static final String CHANNEL_Stop = "stop";
    private static final String CHANNEL_ClearData = "clearData";
    private static final String CHANNEL_IsEmptyTags = "isEmptyTags";
    private static final String CHANNEL_Close = "close";
    private static final String CHANNEL_Connect = "connect";
    private static final String CHANNEL_Reconnect = "reconnect";
    private static final String CHANNEL_IsConnected = "isConnected";
    private static final String CHANNEL_WriteEPC = "writeEpc";
    private static final String CHANNEL_SetPowerLevel = "setPowerLevel";
    private static final String CHANNEL_GetPowerLevel = "getPowerLevel";
    private static final String CHANNEL_SetFrequencyMode = "setFrequencyMode";
    private static final String CHANNEL_GetFrequencyMode = "getFrequencyMode";
    private static final String CHANNEL_StartFindByPartialEpc = "startFindByPartialEpc";
    private static final String CHANNEL_StopFindByPartialEpc = "stopFindByPartialEpc";
    private static final String CHANNEL_IsLocating = "isLocating";
    private static final String CHANNEL_ConnectedStatus = "ConnectedStatus";
    private static final String CHANNEL_TagsStatus = "TagsStatus";
    private static final String CHANNEL_LocateStatus = "LocateStatus";
    private static final String CHANNEL_TriggerKey = "TriggerKey";
    private static final String CHANNEL_BarcodeStatus = "BarcodeStatus";
    private static final String CHANNEL_StartBarcodeScan = "startBarcodeScan";
    private static final String CHANNEL_StopBarcodeScan = "stopBarcodeScan";
    private static final String CHANNEL_CloseBarcodeScanner = "closeBarcodeScanner";
    private static PublishSubject<Boolean> connectedStatus = PublishSubject.create();
    private static PublishSubject<String> tagsStatus = PublishSubject.create();
    private static PublishSubject<String> locateStatus = PublishSubject.create();
    private static PublishSubject<Map<String, Object>> triggerKey = PublishSubject.create();
    private static PublishSubject<Map<String, Object>> barcodeStatus = PublishSubject.create();


    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        this.context = flutterPluginBinding.getApplicationContext();

        final MethodChannel channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "uhf_c66_plugin");
        initConnectedEvent(flutterPluginBinding.getBinaryMessenger());
        initReadEvent(flutterPluginBinding.getBinaryMessenger());
        initLocateEvent(flutterPluginBinding.getBinaryMessenger());
        initTriggerKeyEvent(flutterPluginBinding.getBinaryMessenger());
        initBarcodeEvent(flutterPluginBinding.getBinaryMessenger());

        channel.setMethodCallHandler(this);
        BarcodeHelper.getInstance(context).setListener((resultCode, data) -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("resultCode", resultCode);
            payload.put("data", data);
            barcodeStatus.onNext(payload);
        });
        UHFHelper.getInstance(context).init();
        UHFHelper.getInstance(context).setUhfListener(new UHFListener() {
            @Override
            public void onRead(String tagsJson) {
                if (tagsJson != null)
                    tagsStatus.onNext(tagsJson);
            }

            @Override
            public void onLocate(String locateJson) {
                if (locateJson != null)
                    locateStatus.onNext(locateJson);
            }

            @Override
            public void onConnect(boolean isConnected, int powerLevel) {
                connectedStatus.onNext(isConnected);
            }
        });
    }

    private static void initConnectedEvent(BinaryMessenger messenger) {
        final EventChannel scannerEventChannel = new EventChannel(messenger, CHANNEL_ConnectedStatus);
        scannerEventChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object o, final EventChannel.EventSink eventSink) {
                connectedStatus
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Boolean>() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onNext(Boolean isConnected) {
                                eventSink.success(isConnected);
                            }

                            @Override
                            public void onError(Throwable e) {

                            }

                            @Override
                            public void onComplete() {

                            }
                        });
            }

            @Override
            public void onCancel(Object o) {

            }
        });
    }

    private static void initReadEvent(BinaryMessenger messenger) {
        final EventChannel scannerEventChannel = new EventChannel(messenger, CHANNEL_TagsStatus);
        scannerEventChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object o, final EventChannel.EventSink eventSink) {
                tagsStatus
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<String>() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onNext(String tag) {
                                eventSink.success(tag);
                            }

                            @Override
                            public void onError(Throwable e) {

                            }

                            @Override
                            public void onComplete() {

                            }
                        });
            }

            @Override
            public void onCancel(Object o) {

            }
        });
    }

    private static void initLocateEvent(BinaryMessenger messenger) {
        final EventChannel scannerEventChannel = new EventChannel(messenger, CHANNEL_LocateStatus);
        scannerEventChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object o, final EventChannel.EventSink eventSink) {
                locateStatus
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<String>() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onNext(String locate) {
                                eventSink.success(locate);
                            }

                            @Override
                            public void onError(Throwable e) {

                            }

                            @Override
                            public void onComplete() {

                            }
                        });
            }

            @Override
            public void onCancel(Object o) {

            }
        });
    }

    private static void initBarcodeEvent(BinaryMessenger messenger) {
        final EventChannel barcodeEventChannel = new EventChannel(messenger, CHANNEL_BarcodeStatus);
        barcodeEventChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object o, final EventChannel.EventSink eventSink) {
                barcodeStatus
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Map<String, Object>>() {
                            @Override public void onSubscribe(Disposable d) {}
                            @Override public void onNext(Map<String, Object> payload) { eventSink.success(payload); }
                            @Override public void onError(Throwable e) {}
                            @Override public void onComplete() {}
                        });
            }

            @Override
            public void onCancel(Object o) {}
        });
    }

    private static void initTriggerKeyEvent(BinaryMessenger messenger) {
        final EventChannel triggerKeyChannel = new EventChannel(messenger, CHANNEL_TriggerKey);
        triggerKeyChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object o, final EventChannel.EventSink eventSink) {
                triggerKey
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Map<String, Object>>() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onNext(Map<String, Object> payload) {
                                eventSink.success(payload);
                            }

                            @Override
                            public void onError(Throwable e) {

                            }

                            @Override
                            public void onComplete() {

                            }
                        });
            }

            @Override
            public void onCancel(Object o) {

            }
        });
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        handleMethods(call, result);
    }

    private void handleMethods(MethodCall call, Result result) {
        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + android.os.Build.VERSION.RELEASE);
                break;
            case CHANNEL_IsStarted:
                result.success(UHFHelper.getInstance(context).isStarted());
                break;
            case CHANNEL_StartSingle:
                result.success(UHFHelper.getInstance(context).start(true));
                break;
            case CHANNEL_StartContinuous:
                result.success(UHFHelper.getInstance(context).start(false));
                break;
            case CHANNEL_Stop:
                result.success(UHFHelper.getInstance(context).stop());
                break;
            case CHANNEL_ClearData:
                UHFHelper.getInstance(context).clearData();
                result.success(true);
                break;
            case CHANNEL_IsEmptyTags:
                result.success(UHFHelper.getInstance(context).isEmptyTags());
                break;
            case CHANNEL_Close:
                UHFHelper.getInstance(context).close();
                result.success(true);
                break;
            case CHANNEL_Connect:
                result.success(UHFHelper.getInstance(context).connect());
                break;
            case CHANNEL_Reconnect:
                result.success(UHFHelper.getInstance(context).reconnect());
                break;
            case CHANNEL_IsConnected:
                result.success(UHFHelper.getInstance(context).isConnected());
                break;
            case CHANNEL_WriteEPC:
                String writeData = call.argument("writeData");
                String accessPwd = call.argument("accessPwd");
                result.success(UHFHelper.getInstance(context).writeEPC(writeData, accessPwd));
                break;
            case CHANNEL_SetPowerLevel:
                String powerLevel = call.argument("value");
                result.success(UHFHelper.getInstance(context).setPowerLevel(powerLevel));
                break;
            case CHANNEL_GetPowerLevel:
                result.success(UHFHelper.getInstance(context).getPowerLevel());
                break;
            case CHANNEL_SetFrequencyMode:
                String frequencyMode = call.argument("value");
                result.success(UHFHelper.getInstance(context).setFrequencyMode(frequencyMode));
                break;
            case CHANNEL_GetFrequencyMode:
                result.success(UHFHelper.getInstance(context).getFrequencyMode());
                break;
            case CHANNEL_StartFindByPartialEpc:
                String partialEpc = call.argument("partialEpc");
                String matchType = call.argument("matchType");
                Integer scanWindowMs = call.argument("scanWindowMs");
                result.success(UHFHelper.getInstance(context)
                        .startFindByPartialEpc(partialEpc, matchType, scanWindowMs == null ? 1500 : scanWindowMs));
                break;
            case CHANNEL_StopFindByPartialEpc:
                result.success(UHFHelper.getInstance(context).stopFindByPartialEpc());
                break;
            case CHANNEL_IsLocating:
                result.success(UHFHelper.getInstance(context).isLocating());
                break;
            case CHANNEL_StartBarcodeScan:
                result.success(BarcodeHelper.getInstance(context).startScan());
                break;
            case CHANNEL_StopBarcodeScan:
                result.success(BarcodeHelper.getInstance(context).stopScan());
                break;
            case CHANNEL_CloseBarcodeScanner:
                BarcodeHelper.getInstance(context).close();
                result.success(true);
                break;
            default:
                result.notImplemented();
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    }

    // ---- ActivityAware: capture hardware trigger key at the Activity level ----

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        this.activity = binding.getActivity();
        installKeyInterceptor();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        uninstallKeyInterceptor();
        this.activity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        this.activity = binding.getActivity();
        installKeyInterceptor();
    }

    @Override
    public void onDetachedFromActivity() {
        uninstallKeyInterceptor();
        this.activity = null;
    }

    private void installKeyInterceptor() {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;
        Window.Callback current = window.getCallback();
        if (current instanceof TriggerWindowCallback) return;
        originalWindowCallback = current;
        window.setCallback(new TriggerWindowCallback(current));
    }

    private void uninstallKeyInterceptor() {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;
        if (window.getCallback() instanceof TriggerWindowCallback) {
            window.setCallback(originalWindowCallback);
        }
        originalWindowCallback = null;
    }

    /**
     * Wraps the Activity's Window.Callback. dispatchKeyEvent fires for every
     * hardware key reaching the Activity — including the C66 trigger, which
     * bypasses the FlutterView focus chain. We forward the keyCode/action as
     * a Map over the TriggerKey EventChannel; the Dart side filters.
     */
    private static class TriggerWindowCallback implements Window.Callback {
        private final Window.Callback delegate;

        TriggerWindowCallback(Window.Callback delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            int action = event.getAction();
            if (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("action", action == KeyEvent.ACTION_DOWN ? "down" : "up");
                payload.put("keyCode", event.getKeyCode());
                payload.put("repeatCount", event.getRepeatCount());
                triggerKey.onNext(payload);
            }
            return delegate != null && delegate.dispatchKeyEvent(event);
        }

        @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) { return delegate != null && delegate.dispatchKeyShortcutEvent(event); }
        @Override public boolean dispatchTouchEvent(MotionEvent event) { return delegate != null && delegate.dispatchTouchEvent(event); }
        @Override public boolean dispatchTrackballEvent(MotionEvent event) { return delegate != null && delegate.dispatchTrackballEvent(event); }
        @Override public boolean dispatchGenericMotionEvent(MotionEvent event) { return delegate != null && delegate.dispatchGenericMotionEvent(event); }
        @Override public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) { return delegate != null && delegate.dispatchPopulateAccessibilityEvent(event); }
        @Override public View onCreatePanelView(int featureId) { return delegate != null ? delegate.onCreatePanelView(featureId) : null; }
        @Override public boolean onCreatePanelMenu(int featureId, Menu menu) { return delegate != null && delegate.onCreatePanelMenu(featureId, menu); }
        @Override public boolean onPreparePanel(int featureId, View view, Menu menu) { return delegate != null && delegate.onPreparePanel(featureId, view, menu); }
        @Override public boolean onMenuOpened(int featureId, Menu menu) { return delegate != null && delegate.onMenuOpened(featureId, menu); }
        @Override public boolean onMenuItemSelected(int featureId, MenuItem item) { return delegate != null && delegate.onMenuItemSelected(featureId, item); }
        @Override public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) { if (delegate != null) delegate.onWindowAttributesChanged(attrs); }
        @Override public void onContentChanged() { if (delegate != null) delegate.onContentChanged(); }
        @Override public void onWindowFocusChanged(boolean hasFocus) { if (delegate != null) delegate.onWindowFocusChanged(hasFocus); }
        @Override public void onAttachedToWindow() { if (delegate != null) delegate.onAttachedToWindow(); }
        @Override public void onDetachedFromWindow() { if (delegate != null) delegate.onDetachedFromWindow(); }
        @Override public void onPanelClosed(int featureId, Menu menu) { if (delegate != null) delegate.onPanelClosed(featureId, menu); }
        @Override public boolean onSearchRequested() { return delegate != null && delegate.onSearchRequested(); }
        @Override public boolean onSearchRequested(SearchEvent searchEvent) { return delegate != null && delegate.onSearchRequested(searchEvent); }
        @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) { return delegate != null ? delegate.onWindowStartingActionMode(callback) : null; }
        @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) { return delegate != null ? delegate.onWindowStartingActionMode(callback, type) : null; }
        @Override public void onActionModeStarted(ActionMode mode) { if (delegate != null) delegate.onActionModeStarted(mode); }
        @Override public void onActionModeFinished(ActionMode mode) { if (delegate != null) delegate.onActionModeFinished(mode); }
    }

}
