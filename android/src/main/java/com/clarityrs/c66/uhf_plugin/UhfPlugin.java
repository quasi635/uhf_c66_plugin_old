package com.clarityrs.c66.uhf_plugin;

import com.clarityrs.c66.uhf_plugin.helper.UHFHelper;
import com.clarityrs.c66.uhf_plugin.helper.UHFListener;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
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

import android.content.Context;

/**
 * UhfPlugin
 */
public class UhfPlugin implements FlutterPlugin, MethodCallHandler {
    private Context context;

    private static final String CHANNEL_IsStarted = "isStarted";
    private static final String CHANNEL_StartSingle = "startSingle";
    private static final String CHANNEL_StartContinuous = "startContinuous";
    private static final String CHANNEL_Stop = "stop";
    private static final String CHANNEL_ClearData = "clearData";
    private static final String CHANNEL_IsEmptyTags = "isEmptyTags";
    private static final String CHANNEL_Close = "close";
    private static final String CHANNEL_Connect = "connect";
    private static final String CHANNEL_IsConnected = "isConnected";
    private static final String CHANNEL_WriteEPC = "writeEpc";
    private static final String CHANNEL_SetPowerLevel = "setPowerLevel";
    private static final String CHANNEL_GetPowerLevel = "getPowerLevel";
    private static final String CHANNEL_SetFrequencyMode = "setFrequencyMode";
    private static final String CHANNEL_GetFrequencyMode = "getFrequencyMode";
    private static final String CHANNEL_ConnectedStatus = "ConnectedStatus";
    private static final String CHANNEL_TagsStatus = "TagsStatus";
    private static PublishSubject<Boolean> connectedStatus = PublishSubject.create();
    private static PublishSubject<String> tagsStatus = PublishSubject.create();


    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        this.context = flutterPluginBinding.getApplicationContext();

        final MethodChannel channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "uhf_c66_plugin");
        initConnectedEvent(flutterPluginBinding.getBinaryMessenger());
        initReadEvent(flutterPluginBinding.getBinaryMessenger());

        channel.setMethodCallHandler(this);
        UHFHelper.getInstance(context).init();
        UHFHelper.getInstance(context).setUhfListener(new UHFListener() {
            @Override
            public void onRead(String tagsJson) {
                if (tagsJson != null)
                    tagsStatus.onNext(tagsJson);
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
            default:
                result.notImplemented();
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    }

}
