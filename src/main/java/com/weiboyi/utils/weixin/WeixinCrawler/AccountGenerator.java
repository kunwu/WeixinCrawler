package com.weiboyi.utils.weixin.WeixinCrawler;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class AccountGenerator {

    public static final String FOLDER_INPUT = "input";
    public static final String FILENAME_ARCHIVED = "ArchivedWeixinID.txt";
    private static final String FILENAME_ARCHIVED_SKIP = "ArchivedWeixinIDSkip.txt";
    public static final String ARCHIVE_STATUS_NOT_EXISTS = "NotExists";
    public static final String ARCHIVE_STATUS_COMPLETED = "completed";
    private static Iterator<String> ite = null;
    private static List<String> lstWeixinID = null;
    private static int total;
    private static int index;

    public static void LoadWeixinIDList(Boolean isSearchOfficialsMode) throws IOException {
        lstWeixinID = new ArrayList<String>(10);

        lstWeixinID = loadSourceWeixinID(isSearchOfficialsMode);
        List<String> archived = loadArchivedWeixinID();
        List<String> skipped = loadSkippedWeixinID();

        lstWeixinID.removeAll(archived);
        lstWeixinID.removeAll(skipped);

        ite = lstWeixinID.iterator();
        total = lstWeixinID.size();
        index = -1;
    }

    public static String GetNextWeixinID() {
        if (ite.hasNext()) {
            index++;
            return ite.next();
        } else {
            return null;
        }
    }

    public static int GetCurrentIndex() {
        return index;
    }

    public static int GetTotal() {
        return total;
    }

    public static int size() {
        return lstWeixinID.size();
    }

    public static void ArchiveWeixinIDCompleted(String weixinID) throws Exception {
        archiveWeixinIDWithStatus(weixinID, ARCHIVE_STATUS_COMPLETED);
    }

    public static void ArchiveWeixinIDNotExist(String weixinID) throws Exception {
        archiveWeixinIDWithStatus(weixinID, ARCHIVE_STATUS_NOT_EXISTS);
    }

    private static void archiveWeixinIDWithStatus(String weixinID, String status) throws Exception {
        String filename;

        if (status.equals(ARCHIVE_STATUS_NOT_EXISTS)) {
            filename = FILENAME_ARCHIVED_SKIP;
        } else if (status.equals(ARCHIVE_STATUS_COMPLETED)) {
            filename = FILENAME_ARCHIVED;
        } else {
            throw new Exception("Archive status incorrect:" + status);
        }

        File f = new File("." + File.separator + FOLDER_INPUT + File.separator + filename);
        if (!f.exists()) {
            boolean newFile = f.createNewFile();
            if (!newFile) {
                throw new IOException("Failed to create archived Weixin ID file.");
            }
        }

        String output = String.format("%s\t%s\t%s"
                , weixinID
                , (new SimpleDateFormat("yyyyMMdd_HHmmss")).format(new Date())
                , status
        );
        BufferedWriter wr = new BufferedWriter(new FileWriter(f, true));
        wr.write(output);
        wr.newLine();
        wr.flush();
    }

    private static List<String> loadArchivedWeixinID() throws IOException {
        ArrayList<String> lstWeixinID = new ArrayList<String>(10);
        String path = "." + File.separator + FOLDER_INPUT + File.separator + FILENAME_ARCHIVED;
        BufferedReader br = new BufferedReader(new FileReader(path));
        String line;
        while ((line = br.readLine()) != null) {
            String[] sections = line.split("\t");
            lstWeixinID.add(sections[0]);
        }
        return lstWeixinID;
    }

    private static List<String> loadSkippedWeixinID() throws IOException {
        ArrayList<String> lstWeixinID = new ArrayList<String>(10);
        String path = "." + File.separator + FOLDER_INPUT + File.separator + FILENAME_ARCHIVED_SKIP;
        try {
            BufferedReader br = new BufferedReader(new FileReader(path));
            String line;
            while ((line = br.readLine()) != null) {
                String[] sections = line.split("\t");
                lstWeixinID.add(sections[0]);
            }
        } catch (FileNotFoundException ignore) {
        }
        return lstWeixinID;
    }

    private static List<String> loadSourceWeixinID(boolean isSearchOfficalsMode) throws IOException {
        ArrayList<String> lstWeixinID = new ArrayList<String>(10);
        String path = "." + File.separator + FOLDER_INPUT + File.separator + "SourceWeixinID.txt";
        BufferedReader br = new BufferedReader(new FileReader(path));
        String line;
        while ((line = br.readLine()) != null) {
            int poundIdx = line.indexOf("#");
            if (poundIdx >= 0) {
                line = line.substring(0, poundIdx);
            }
            line = line.trim();
            if (line.equals("")) {
                continue;
            }
            if (isSearchOfficalsMode) {
                if (line.startsWith("gh_")) {
                    continue;
                }
            }
            if (!lstWeixinID.contains(line)) {
                lstWeixinID.add(line);
            }
        }
        return lstWeixinID;
    }
}
