package com.weiboyi.utils.weixin.WeixinCrawler;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ElapseTimer {

    private List<Date> lstDate;

    public ElapseTimer() {
        lstDate = new ArrayList<Date>();
        lstDate.add(new Date());
    }

    public void addCheckpoint() {
        lstDate.add(new Date());
    }

    public String addCheckpointAndGetLatestElapsed() {
        addCheckpoint();
        return calcLatestElapsed();
    }

    private String calcLatestElapsed() {
        Date dtBegin, dtEnd;
        if (lstDate.size() >= 2) {
            dtEnd = lstDate.get(lstDate.size() - 1);
            dtBegin = lstDate.get(lstDate.size() - 2);
        } else {
            dtEnd = new Date();
            dtBegin = lstDate.get(lstDate.size() - 1);
        }
        return formatElapsed(dtBegin, dtEnd);
    }

    private String formatElapsed(Date dtBegin, Date dtEnd) {
        long diffMill = dtEnd.getTime() - dtBegin.getTime();
        int hr = Math.round(diffMill / 3600000);
        int min = Math.round(diffMill % 3600000 / 60000);
        double sec = 1.0 * (diffMill % 60000) / 1000;
        return String.format("%02d:%02d:%02.1f", hr, min, sec);
    }

    public String addCheckpointAndGetTotalElapsed() {
        addCheckpoint();
        return calcTotalElapsed();
    }

    public String calcTotalElapsed() {
        Date dtBegin, dtEnd;
        dtBegin = lstDate.get(0);
        if (lstDate.size() >= 2) {
            dtEnd = lstDate.get(lstDate.size() - 1);
        } else {
            dtEnd = new Date();
        }

        return formatElapsed(dtBegin, dtEnd);
    }

    public long calcTotalElapsedInMill() {
        Date dtEnd = new Date();
        return dtEnd.getTime() - lstDate.get(0).getTime();
    }
}
