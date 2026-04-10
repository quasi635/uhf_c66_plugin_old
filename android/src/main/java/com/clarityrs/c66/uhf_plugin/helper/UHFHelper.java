package com.clarityrs.c66.uhf_plugin.helper;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.TagLocationEntity;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.ITagLocate;
import com.rscja.deviceapi.interfaces.ITagLocationCallback;
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
    private final Map<String, String> matchedRssiByEpc = new LinkedHashMap<>();
    // private boolean isSingleRead = false;
    private HashMap<String, EPC> tagList;

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
        handler = new Handler(context.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                String result = msg.obj + "";
                String[] strs = result.split("@");
                addEPCToList(strs[0], strs[1]);
            }
        };

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
                    addEPCToList(strEPC, strUII.getRssi());
                    return true;
                } else {
                    return false;
                }
            } else {// Auto read multi .startInventoryTag((byte) 0, (byte) 0))
                // mContext.mReader.setEPCTIDMode(true);
                if (mReader.startInventoryTag()) {
                    isStart = true;
                    new TagThread().start();
                    return true;
                } else {
                    return false;
                }
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
        clearData();
        return hasStopped;
    }

    public void close() {
        isStart = false;
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
            return mReader.setPower(Integer.parseInt(level));
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

        if (mReader != null)
            return mReader.setFrequencyMode(Integer.parseInt(area));
        return false;
    }

    public Integer getFrequencyMode() {
        if (mReader != null) {
            return mReader.getFrequencyMode();
        }
        return -1;
    }

    public boolean startFindByPartialEpc(String partialEpc, String matchType, int scanWindowMs) {
        if (mReader == null || !isConnect || TextUtils.isEmpty(partialEpc)) {
            return false;
        }

        if (isStart) {
            stop();
        }
        stopFindByPartialEpc();

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
        Map<String, UHFTAGInfo> uniqueMatches = new LinkedHashMap<>();
        matchedRssiByEpc.clear();

        int safeScanWindowMs = Math.max(300, scanWindowMs);
        String safeMatchType = matchType == null ? "startswith" : matchType.toLowerCase(Locale.ROOT);

        if (!mReader.startInventoryTag()) {
            return matchedTags;
        }

        long endTime = System.currentTimeMillis() + safeScanWindowMs;
        try {
            while (System.currentTimeMillis() < endTime) {
                UHFTAGInfo scanResult = mReader.readTagFromBuffer();
                if (scanResult == null || TextUtils.isEmpty(scanResult.getEPC())) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                String epc = scanResult.getEPC();
                if (matchesPartial(epc, partialEpc, safeMatchType) && !uniqueMatches.containsKey(epc)) {
                    UHFTAGInfo locateTarget = new UHFTAGInfo();
                    locateTarget.setEPC(epc);
                    uniqueMatches.put(epc, locateTarget);
                    matchedRssiByEpc.put(epc, scanResult.getRssi());
                }
            }
        } finally {
            mReader.stopInventory();
        }

        matchedTags.addAll(uniqueMatches.values());
        return matchedTags;
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

    /**
     * 添加EPC到列表中
     *
     * @param epc
     */
    private void addEPCToList(String epc, String rssi) {
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

    class TagThread extends Thread {
        public void run() {
            String strTid;
            String strResult;
            UHFTAGInfo res = null;
            while (isStart) {
                res = mReader.readTagFromBuffer();
                if (res != null) {
                    strTid = res.getTid();
                    if (strTid.length() != 0 && !strTid.equals("0000000" +
                            "000000000") && !strTid.equals("000000000000000000000000")) {
                        strResult = "TID:" + strTid + "\n";
                    } else {
                        strResult = "";
                    }
                    Log.i("data", "c" + res.getEPC() + "|" + strResult);
                    Message msg = handler.obtainMessage();
                    msg.obj = strResult + "EPC:" + res.getEPC() + "@" + res.getRssi();

                    handler.sendMessage(msg);
                }
            }
        }
    }

}
