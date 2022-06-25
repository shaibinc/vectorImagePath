package com.jiangpeng.android.antrace.autobgremover;

import android.content.Context;

public class TFModel {
   private static SegmentBitmapsLoader segmentBitmapsLoader = null;
    public synchronized static SegmentBitmapsLoader getInstance(Context context){
        if(segmentBitmapsLoader == null || !segmentBitmapsLoader.isInitialized)
        {
            segmentBitmapsLoader = new SegmentBitmapsLoader(context);
        }
        return segmentBitmapsLoader;

    }
}
