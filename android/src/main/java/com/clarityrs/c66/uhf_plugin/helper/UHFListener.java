package com.clarityrs.c66.uhf_plugin.helper;

public interface UHFListener {
    void onRead(String tagsJson);

    void onLocate(String locateJson);

    void onConnect(boolean isConnected, int powerLevel);

}