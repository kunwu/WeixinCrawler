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
    private static Iterator<String> ite = null;
    private static List<String> lstWeixinID = null;

    public static void LoadWeixinIDList(Boolean isSearchOfficalsMode) throws IOException {
        lstWeixinID = new ArrayList<String>(10);

        lstWeixinID = loadSourceWeixinID(isSearchOfficalsMode);
        List<String> archived = loadArchivedWeixinID();

        lstWeixinID.removeAll(archived);

        ite = lstWeixinID.iterator();
    }

    public static String GetNextWeixinID()  {
        if (ite.hasNext()) {
            return ite.next();
        } else {
            return null;
        }
    }

    public static int size() {
        return lstWeixinID.size();
    }

    public static void ArchiveWeixinIDCompleted(String weixinID) throws IOException {
        archiveWeixinIDWithStatus(weixinID, "completed");
    }

    public static void ArchiveWeixinIDNotExist(String weixinID) throws IOException {
        archiveWeixinIDWithStatus(weixinID, "NotExists");
    }

    private static void archiveWeixinIDWithStatus(String weixinID, String status) throws IOException {
        File f = new File("." + File.separator + FOLDER_INPUT + File.separator + FILENAME_ARCHIVED);
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
