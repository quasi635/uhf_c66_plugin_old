package com.clarityrs.c66.uhf_plugin.helper;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Console;
import java.util.HashMap;
import java.util.Objects;


public class UHFHelper {
    private Context context;
    private static UHFHelper instance;
    public RFIDWithUHFUART mReader;
    Handler handler;
    private UHFListener uhfListener;
    private boolean isStart = false;
    private boolean isConnect = false;
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
        if (isStart && mReader != null) {
            isStart = false;
            return mReader.stopInventory();
        }
        isStart = false;
        clearData();
        return false;
    }

    public void close() {
        isStart = false;
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
