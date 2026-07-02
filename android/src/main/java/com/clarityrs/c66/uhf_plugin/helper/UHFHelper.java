package com.clarityrs.c66.uhf_plugin.helper;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.TagLocationEntity;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.ITagLocate;
import com.rscja.deviceapi.interfaces.ITagLocationCallback;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;
import com.rscja.deviceapi.interfaces.IUHFLocationCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;


public class UHFHelper {
    private Context context;
    private static UHFHelper instance;
    public RFIDWithUHFUART mReader;
    Handler handler;
    private UHFListener uhfListener;
    private boolean isStart = false;
    private boolean isConnect = false;
    private boolean isLocating = false;
    private ITagLocate tagLocate;
    private String lastPowerLevel;
    private String lastFrequencyMode;
    private final Map<String, String> matchedRssiByEpc = new LinkedHashMap<>();
    // private boolean isSingleRead = false;
    private HashMap<String, EPC> tagList;
    private IUHFInventoryCallback inventoryCallback;
    // Non-null only while collectCandidatesByPartialEpc's scan window is open;
    // read from the inventory callback (see onTagRead) to decide whether an
    // incoming tag belongs to a partial-EPC locate scan instead of a
    // continuous read. Set/cleared on the scan's own background thread,
    // read on whatever thread the vendor SDK invokes the callback from.
    private volatile PartialEpcScanSession partialEpcScanSession;

    private UHFHelper(Context context) {
        this.context = context;
    }

    public static UHFHelper getInstance(Context context) {
        if (instance == null)
            instance = new UHFHelper(context);
        return instance;
    }

    // public RFIDWithUHFUART getReader() {
    // return mReader;
    // }

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public void setUhfListener(UHFListener uhfListener) {
        this.uhfListener = uhfListener;
    }

    public void init() {
        // this.context = context;
        // this.uhfListener = uhfListener;
        tagList = new HashMap<>();
        clearData();
        handler = new Handler(context.getMainLooper());
    }

    /**
     * Registers the reader's push-based tag callback, replacing the
     * deprecated readTagFromBuffer() polling loop. Safe to call repeatedly
     * (e.g. from both connect() and reconnect()) since it just re-attaches
     * the same callback instance to whatever mReader currently is.
     */
    private void installInventoryCallback() {
        if (mReader == null) {
            return;
        }
        if (inventoryCallback == null) {
            inventoryCallback = this::onTagRead;
        }
        mReader.setInventoryCallback(inventoryCallback);
    }

    /**
     * Fires for every tag read while an inventory session is active. Both
     * continuous multi-tag reading (start(false)) and the partial-EPC locate
     * scan (collectCandidatesByPartialEpc) start that session via
     * startInventoryTag(), so route based on whichever mode is currently
     * active rather than needing two separate reader threads.
     */
    private void onTagRead(UHFTAGInfo tag) {
        if (tag == null || TextUtils.isEmpty(tag.getEPC())) {
            return;
        }

        PartialEpcScanSession session = partialEpcScanSession;
        if (session != null) {
            String epc = tag.getEPC();
            if (matchesPartial(epc, session.partialEpc, session.matchType)) {
                synchronized (session) {
                    if (!session.uniqueMatches.containsKey(epc)) {
                        UHFTAGInfo locateTarget = new UHFTAGInfo();
                        locateTarget.setEPC(epc);
                        session.uniqueMatches.put(epc, locateTarget);
                        session.rssiByEpc.put(epc, tag.getRssi());
                    }
                }
            }
            return;
        }

        if (!isStart) {
            return;
        }

        String strTid = tag.getTid();
        String strResult;
        if (strTid.length() != 0 && !strTid.equals("0000000000000000")
                && !strTid.equals("000000000000000000000000")) {
            strResult = "TID:" + strTid + "\n";
        } else {
            strResult = "";
        }
        Log.i("data", "c" + tag.getEPC() + "|" + strResult);

        final String epcField = strResult + "EPC:" + tag.getEPC();
        final String rssi = tag.getRssi();
        // Continuous-read path: just record the tag. The periodic emitLoop
        // batches the (potentially expensive) JSON snapshot instead of
        // rebuilding/serializing the whole list on every single tag read,
        // which used to flood the main thread and could delay/block the
        // "stop" method-channel call.
        handler.post(() -> addEPCToList(epcField, rssi, false));
    }

    public boolean connect() {
        try {
            mReader = RFIDWithUHFUART.getInstance();
        } catch (Exception ex) {
            uhfListener.onConnect(false, 0);
            return false;
        }

        if (mReader != null) {
            isConnect = mReader.init(context);
            installInventoryCallback();
            // mReader.setFrequencyMode(2);
            // mReader.setPower(29);
            uhfListener.onConnect(isConnect, 0);
            return isConnect;
        }
        uhfListener.onConnect(false, 0);
        return false;
    }

    public boolean start(boolean isSingleRead) {
        if (!isStart) {
            if (isSingleRead) {// Single Read
                UHFTAGInfo strUII = mReader.inventorySingleTag();
                if (strUII != null) {
                    String strEPC = strUII.getEPC();
                    addEPCToList(strEPC, strUII.getRssi(), true);
                    return true;
                } else {
                    return false;
                }
            } else {// Auto read multi .startInventoryTag((byte) 0, (byte) 0))
                // mContext.mReader.setEPCTIDMode(true);
                if (mReader != null && mReader.startInventoryTag()) {
                    isStart = true;
                    handler.post(emitLoop);
                    return true;
                }
                // startInventoryTag() returns false when the underlying UHF
                // module has been powered off — typically because another
                // process (or a hot-restarted copy of ours) constructed a
                // second UhfBase and broadcast UHF_POWER_OFF at us. Try once
                // to recover by freeing + re-init'ing the reader.
                if (reconnect() && mReader != null && mReader.startInventoryTag()) {
                    isStart = true;
                    handler.post(emitLoop);
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    public void clearData() {
        tagList.clear();
    }

    public boolean stop() {
        boolean hasStopped = false;
        if (isStart && mReader != null) {
            isStart = false;
            hasStopped = mReader.stopInventory();
        }
        if (isLocating) {
            hasStopped = stopFindByPartialEpc() || hasStopped;
        }
        isStart = false;
        handler.removeCallbacks(emitLoop);
        // Flush any tags read since the last batch so the caller sees the
        // final state before we clear it, rather than silently dropping
        // whichever reads landed in the last (<EMIT_INTERVAL_MS) window.
        emitTagListIfDirty();
        clearData();
        return hasStopped;
    }

    public void close() {
        isStart = false;
        handler.removeCallbacks(emitLoop);
        stopFindByPartialEpc();
        if (mReader != null) {
            mReader.free();
            isConnect = false;
        }
        clearData();
    }

    public boolean writeEPC(String writeData, String accessPwd) {
        if (mReader != null) {
            return mReader.writeDataToEpc(accessPwd, writeData);
        }
        return false;
    }

    public boolean setPowerLevel(String level) {
        // 1-30 dBm
        if (mReader != null) {
            boolean ok = mReader.setPower(Integer.parseInt(level));
            if (ok) lastPowerLevel = level;
            return ok;
        }
        return false;
    }

    public Integer getPowerLevel() {
        if (mReader != null) {
            return mReader.getPower();
        }
        return -1;
    }

    public boolean setFrequencyMode(String area) {
        /*
         * 0x01：China Standard(840~845MHz)
         * 0x02：China Standard2(920~925MHz)
         * 0x04：Europe Standard(865~868MHz)
         * 0x08：USA(902-928MHz)
         * 0x16：Korea(917~923MHz)
         * 0x32: Japan(952~953MHz)
         * 0x33: South Africa(915~919MHz)
         * 0x34: China Taiwan
         * 0x35:Vietnam(918~923MHz)
         * 0x36:Peru(915MHz-928MHz)
         * 0x37:Russia( 860MHz-867.6MHz)
         * 0x80 Morocco
         */

        if (mReader != null) {
            boolean ok = mReader.setFrequencyMode(Integer.parseInt(area));
            if (ok) lastFrequencyMode = area;
            return ok;
        }
        return false;
    }

    /**
     * Free and re-initialize the UHF reader. Use this to recover from a
     * UHF_POWER_OFF broadcast — either from a hot-restart that left a second
     * UhfBase alive, or from another UHF-using app on the device. Re-applies
     * the last power level / frequency mode set through this helper so callers
     * don't have to remember them.
     *
     * @return true if the reader came back up.
     */
    public boolean reconnect() {
        try {
            if (isStart && mReader != null) {
                mReader.stopInventory();
            }
        } catch (Throwable ignored) {
        }
        try {
            stopFindByPartialEpc();
        } catch (Throwable ignored) {
        }
        isStart = false;
        isLocating = false;

        if (mReader != null) {
            try {
                mReader.free();
            } catch (Throwable ignored) {
            }
        }
        mReader = null;
        isConnect = false;

        try {
            mReader = RFIDWithUHFUART.getInstance();
        } catch (Exception ex) {
            if (uhfListener != null) uhfListener.onConnect(false, 0);
            return false;
        }
        if (mReader == null) {
            if (uhfListener != null) uhfListener.onConnect(false, 0);
            return false;
        }

        isConnect = mReader.init(context);
        installInventoryCallback();
        if (uhfListener != null) uhfListener.onConnect(isConnect, 0);

        if (isConnect) {
            if (lastFrequencyMode != null) {
                try {
                    mReader.setFrequencyMode(Integer.parseInt(lastFrequencyMode));
                } catch (Throwable ignored) {
                }
            }
            if (lastPowerLevel != null) {
                try {
                    mReader.setPower(Integer.parseInt(lastPowerLevel));
                } catch (Throwable ignored) {
                }
            }
        }
        return isConnect;
    }

    public Integer getFrequencyMode() {
        if (mReader != null) {
            return mReader.getFrequencyMode();
        }
        return -1;
    }

    public interface FindByPartialEpcCallback {
        void onResult(boolean started);
    }

    /**
     * Runs the scan on a background thread and delivers the result via
     * callback on the main looper. collectCandidatesByPartialEpc blocks for
     * at least scanWindowMs (default 1500ms); doing that on the caller's
     * thread (the platform/UI thread, for MethodChannel callers) drops
     * frames, so the whole lookup+locate sequence is offloaded here.
     */
    public void startFindByPartialEpc(String partialEpc, String matchType, int scanWindowMs,
                                       FindByPartialEpcCallback callback) {
        if (mReader == null || !isConnect || TextUtils.isEmpty(partialEpc)) {
            callback.onResult(false);
            return;
        }

        if (isStart) {
            stop();
        }
        stopFindByPartialEpc();

        new Thread(() -> {
            boolean started = doStartFindByPartialEpc(partialEpc, matchType, scanWindowMs);
            handler.post(() -> callback.onResult(started));
        }).start();
    }

    private boolean doStartFindByPartialEpc(String partialEpc, String matchType, int scanWindowMs) {
        List<UHFTAGInfo> candidates = collectCandidatesByPartialEpc(partialEpc, matchType, scanWindowMs);
        if (candidates.isEmpty()) {
            return false;
        }

        String targetEpc = pickBestCandidateEpc(candidates);
        if (!TextUtils.isEmpty(targetEpc)) {
            boolean startedSingleLocate = mReader.startLocation(context, targetEpc, 1, 32,
                    new IUHFLocationCallback() {
                        @Override
                        public void getLocationValue(int value, boolean valid) {
                            if (uhfListener == null || !isLocating) {
                                return;
                            }
                            JSONObject json = new JSONObject();
                            try {
                                json.put("epc", targetEpc);
                                json.put("rssi", matchedRssiByEpc.get(targetEpc));
                                json.put("signalValue", value);
                                json.put("valid", valid);
                            } catch (JSONException ignored) {
                            }
                            handler.post(() -> uhfListener.onLocate(json.toString()));
                        }
                    });

            if (startedSingleLocate) {
                isLocating = true;
                return true;
            }
        }

        tagLocate = mReader.getTagLocate(context);
        if (tagLocate == null) {
            return false;
        }

        boolean started = tagLocate.startMultiTagsLocate(candidates,
                new ITagLocationCallback<TagLocationEntity>() {
                    @Override
                    public void tagLocationCallback(TagLocationEntity entity) {
                        if (entity == null || uhfListener == null || !isLocating) {
                            return;
                        }
                        JSONObject json = new JSONObject();
                        try {
                            UHFTAGInfo tag = entity.getUhftagInfo();
                            json.put("epc", tag != null ? tag.getEPC() : "");
                            json.put("rssi", tag != null ? tag.getRssi() : "");
                            if (entity.getTagLocationInfo() != null) {
                                json.put("signalValue", entity.getTagLocationInfo().getSignalValue());
                                json.put("valid", entity.getTagLocationInfo().isValid());
                            } else {
                                json.put("signalValue", 0);
                                json.put("valid", false);
                            }
                        } catch (JSONException ignored) {
                        }
                        handler.post(() -> uhfListener.onLocate(json.toString()));
                    }
                });

        isLocating = started;
        return started;
    }

    public boolean stopFindByPartialEpc() {
        boolean stopped = false;
        if (mReader != null) {
            stopped = mReader.stopLocation() || stopped;
            stopped = mReader.stopRadarLocation() || stopped;
        }
        if (tagLocate != null) {
            stopped = tagLocate.stopMultiTagsLocate() || stopped;
        }
        isLocating = false;
        tagLocate = null;
        matchedRssiByEpc.clear();
        return stopped;
    }

    public boolean isLocating() {
        return isLocating;
    }

    private List<UHFTAGInfo> collectCandidatesByPartialEpc(String partialEpc, String matchType, int scanWindowMs) {
        List<UHFTAGInfo> matchedTags = new ArrayList<>();
        matchedRssiByEpc.clear();

        int safeScanWindowMs = Math.max(300, scanWindowMs);
        String safeMatchType = matchType == null ? "startswith" : matchType.toLowerCase(Locale.ROOT);

        if (!mReader.startInventoryTag()) {
            return matchedTags;
        }

        // Matches accumulate via onTagRead (the shared IUHFInventoryCallback)
        // instead of polling readTagFromBuffer(); just hold the scan window
        // open for safeScanWindowMs and let the callback populate the session.
        PartialEpcScanSession session = new PartialEpcScanSession(partialEpc, safeMatchType);
        partialEpcScanSession = session;
        try {
            Thread.sleep(safeScanWindowMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            partialEpcScanSession = null;
            mReader.stopInventory();
        }

        synchronized (session) {
            matchedTags.addAll(session.uniqueMatches.values());
            matchedRssiByEpc.putAll(session.rssiByEpc);
        }
        return matchedTags;
    }

    /** Accumulates matches for one collectCandidatesByPartialEpc scan window. */
    private static final class PartialEpcScanSession {
        final String partialEpc;
        final String matchType;
        final Map<String, UHFTAGInfo> uniqueMatches = new LinkedHashMap<>();
        final Map<String, String> rssiByEpc = new LinkedHashMap<>();

        PartialEpcScanSession(String partialEpc, String matchType) {
            this.partialEpc = partialEpc;
            this.matchType = matchType;
        }
    }

    private String pickBestCandidateEpc(List<UHFTAGInfo> candidates) {
        String bestEpc = null;
        int bestRssi = Integer.MIN_VALUE;

        for (UHFTAGInfo tag : candidates) {
            if (tag == null || TextUtils.isEmpty(tag.getEPC())) {
                continue;
            }
            String epc = tag.getEPC();
            String rawRssi = matchedRssiByEpc.get(epc);
            int parsedRssi = parseRssi(rawRssi);
            if (bestEpc == null || parsedRssi > bestRssi) {
                bestEpc = epc;
                bestRssi = parsedRssi;
            }
        }

        return bestEpc;
    }

    private int parseRssi(String rawRssi) {
        if (TextUtils.isEmpty(rawRssi)) {
            return Integer.MIN_VALUE;
        }
        try {
            return Integer.parseInt(rawRssi.trim());
        } catch (Exception ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private boolean matchesPartial(String epc, String partial, String matchType) {
        if (TextUtils.isEmpty(epc) || TextUtils.isEmpty(partial)) {
            return false;
        }
        String epcNormalized = epc.toLowerCase(Locale.ROOT);
        String partialNormalized = partial.toLowerCase(Locale.ROOT);

        switch (matchType) {
            case "contains":
                return epcNormalized.contains(partialNormalized);
            case "endswith":
                return epcNormalized.endsWith(partialNormalized);
            case "exact":
                return epcNormalized.equals(partialNormalized);
            case "startswith":
            default:
                return epcNormalized.startsWith(partialNormalized);
        }
    }

    // How often we push a fresh tag-list snapshot to Dart while continuously
    // scanning. Reads land on tagList as fast as the reader buffer produces
    // them (can be hundreds/sec), but rebuilding+serializing the whole list
    // on every single read isn't necessary — batching caps that cost to a
    // fixed rate regardless of how many tags have been found.
    private static final long EMIT_INTERVAL_MS = 200;
    private boolean tagListDirty = false;

    private final Runnable emitLoop = new Runnable() {
        @Override
        public void run() {
            emitTagListIfDirty();
            if (isStart) {
                handler.postDelayed(this, EMIT_INTERVAL_MS);
            }
        }
    };

    /**
     * 添加EPC到列表中
     *
     * @param epc
     * @param emitImmediately whether to push a snapshot right away (single-read
     *                        path, where there's no periodic emitLoop running)
     *                        instead of waiting for the next batched flush.
     */
    private void addEPCToList(String epc, String rssi, boolean emitImmediately) {
        if (!TextUtils.isEmpty(epc)) {
            EPC tag = new EPC();

            tag.setId("");
            tag.setEpc(epc);
            tag.setCount(String.valueOf(1));
            tag.setRssi(rssi);

            if (tagList.containsKey(epc)) {
                int tagCount = Integer.parseInt(Objects.requireNonNull(tagList.get(epc)).getCount()) + 1;
                tag.setCount(String.valueOf(tagCount));
            }
            tagList.put(epc, tag);
            tagListDirty = true;

            if (emitImmediately) {
                emitTagListIfDirty();
            }
        }
    }

    private void emitTagListIfDirty() {
        if (!tagListDirty) {
            return;
        }
        tagListDirty = false;

        final JSONArray jsonArray = new JSONArray();

        for (EPC epcTag : tagList.values()) {
            JSONObject json = new JSONObject();
            try {
                json.put(TagKey.ID, Objects.requireNonNull(epcTag).getId());
                json.put(TagKey.EPC, epcTag.getEpc());
                json.put(TagKey.RSSI, epcTag.getRssi());
                json.put(TagKey.COUNT, epcTag.getCount());
                jsonArray.put(json);
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
        uhfListener.onRead(jsonArray.toString());
    }

    public boolean isEmptyTags() {
        return tagList != null && !tagList.isEmpty();
    }

    public boolean isStarted() {
        return isStart;
    }

    public boolean isConnected() {
        return isConnect;
    }

}
