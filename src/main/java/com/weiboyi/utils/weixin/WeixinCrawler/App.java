package com.weiboyi.utils.weixin.WeixinCrawler;

import com.google.gson.*;
import io.appium.java_client.AppiumDriver;
import org.apache.commons.lang3.StringEscapeUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.net.*;
import java.net.Proxy;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How to run:
 * mvn compile exec:java -Dexec.mainClass="com.weiboyi.utils.weixin.WeixinCrawler.App" -Dexec.args="_xpath searchOfficialsOnly"
 */
public class App {

    public static final String PAGE_ADD_CONTACTS_TITLE = "//android.widget.TextView[@text='Add Contacts']";
    public static final String PAGE_ADD_CONTACTS_INPUT_WEIXIN_ID = "//android.widget.EditText";
    public static final String BUTTON_SEARCH_WEIXIN_ID = "//android.widget.Button[@text='Search']";
    public static final String SEARCH_WEIXIN_ID_RESULT_USER_NOT_EXIST = "//android.widget.TextView[@text='This user does not exist']";
    public static final String SEARCH_WEIXIN_ID_RESULT_PROFILE = "//android.widget.TextView[@text='Profile']";

    public static final String EOL = System.getProperty("line.separator");
    public static final String STAGE_HISTORY = "HISTORY";
    public static final String STAGE_PROFILE = "PROFILE";
    public static final String STAGE_INIT = "INIT";

    public static final String SEARCH_MODE_DIRECT = "DIRECT";
    public static final String SEARCH_MODE_OFFICIAL = "OFFICIAL";

    public static final int WAIT_INTERVAL_XXS = 100;
    public static final int WAIT_INTERVAL_XS = 500;
    public static final int WAIT_INTERVAL_S = 1000;
    public static final int WAIT_INTERVAL_M = 3000;
    private static final int WAIT_INTERVAL_L = 10000;

    public static final int RETRY_M = 2;

    public static final int MAX_MESSAGES_EACH_ACCOUNT = 100;

    private static final boolean DBG_OUTPUT_MESSAGE_FULL_HTML = false;
    private static final boolean DBG_OUTPUT_MESSAGE_LIST_HTML = false;

    // use mid for group id, other than comm_msg_info.id
    private static final boolean GROUP_ID_USE_MID = true;

    private static BufferedWriter bwOutputFile = null;

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Hello World!");
        boolean dbgNoFetch = false;
        boolean trySearchUnderOfficialAccountsModeIfDirectModeFailed = false;

        List<String> lstArgs = Arrays.asList(args);
        String searchAccountMode = lstArgs.contains("searchOfficialsOnly") ? SEARCH_MODE_OFFICIAL : SEARCH_MODE_DIRECT;
        boolean testXPath = lstArgs.contains("xpath");

        trace("Load weixin ID list ... ", false);
        AccountGenerator.LoadWeixinIDList(searchAccountMode.equals(SEARCH_MODE_OFFICIAL));
        trace(String.format("%d loaded.", AccountGenerator.size()));

        prepareOutputFile();

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        URL serverUrl = new URL("http://127.0.0.1:4723/wd/hub");
        DesiredCapabilities capabilities = new DesiredCapabilities();
        capabilities.setCapability("automationName", "Appium");
        capabilities.setCapability("platformName", "Android");
        capabilities.setCapability("platformVersion", "4.2.2");
        capabilities.setCapability("deviceName", "Android");
        capabilities.setCapability("appPackage", "com.tencent.mm");
//        capabilities.setCapability("appActivity", ".ui.LauncherUI");
        capabilities.setCapability("appActivity", ".ui.pluginapp.AddMoreFriendsUI");
//        capabilities.setCapability("appActivity", ".plugin.brandservice.ui.SearchOrRecommendBizUI");
        capabilities.setCapability("noReset", "true");
        capabilities.setCapability("newCommandTimeout", "86400");
        AppiumDriver driver = null;

        ElapseTimer timer = new ElapseTimer();
        int currentIndex = 0;
        int totalWeixinIDs = 0;

        try {
            trace("Create driver ... ", false);
            driver = new AppiumDriver(serverUrl, capabilities);
            trace("Created.");

            trace("Wait 3\" for init.");
            Thread.sleep(WAIT_INTERVAL_M);

            timer.addCheckpoint();

            try {
                while (true) {
                    String stage = STAGE_INIT;
                    String weixinID = AccountGenerator.GetNextWeixinID();
                    if (weixinID == null) {
                        trace("No more weixin ID.");
                        break;
                    }
                    currentIndex = AccountGenerator.GetCurrentIndex();
                    totalWeixinIDs = AccountGenerator.GetTotal();

                    trace("Wait for page (Add Contacts) ... ", false);
                    WebDriverWait waitInputSearchById = new WebDriverWait(driver, 5);
                    waitInputSearchById.until(ExpectedConditions.presenceOfElementLocated(By.xpath(PAGE_ADD_CONTACTS_TITLE)));
                    trace("Found.");

                    if (searchAccountMode.equals(SEARCH_MODE_DIRECT)) {
                        trace(String.format("Locate input (weixin ID) %d/%d ... "
                                , currentIndex + 1, totalWeixinIDs)
                                , false);
                        WebElement elmInputWeixinID = driver.findElement(By.xpath(PAGE_ADD_CONTACTS_INPUT_WEIXIN_ID));
                        trace("Located.");

                        trace(String.format("Type in weixin ID (%s) ... ", weixinID), false);
                        try {
                            elmInputWeixinID.clear();
                        } catch (Exception ignore) {
                        }
                        elmInputWeixinID.sendKeys(weixinID);
                        trace(weixinID);

                        trace("Locate button (Search) ... ", false);
                        WebElement elmBtnSearch = driver.findElement(By.xpath(BUTTON_SEARCH_WEIXIN_ID));
                        trace("Located.");

                        trace("Click button (Search)");
                        elmBtnSearch.click();

                        List<By> lstCaseSearchWeixinIDResult = new ArrayList<By>();
                        lstCaseSearchWeixinIDResult.add(By.xpath(SEARCH_WEIXIN_ID_RESULT_USER_NOT_EXIST));
                        lstCaseSearchWeixinIDResult.add(By.xpath(SEARCH_WEIXIN_ID_RESULT_PROFILE));
                        trace("Search weixin ID and determine result ... ", false);
                        try {
                            Integer choiceIndex = trySwitch(driver, lstCaseSearchWeixinIDResult, 10);
                            if (choiceIndex == null) {
                                if (trySearchUnderOfficialAccountsModeIfDirectModeFailed) {
                                    stage = searchUnderOfficialAccountsMode(driver, stage, weixinID, currentIndex, totalWeixinIDs);
                                } else {
                                    throw new ElementNotFoundException("Weixin ID search result not found on activity:" + driver.currentActivity());
                                }
                            } else {
                                if (choiceIndex == 0) {
                                    trace("0: user not exist.");
                                    trace("Dismiss dialog ... ", false);
                                    WebElement btnDismissDlgUserNotExist = driver.findElement(By.xpath("//android.widget.Button[@text='OK']"));
                                    btnDismissDlgUserNotExist.click();
                                    AccountGenerator.ArchiveWeixinIDNotExist(weixinID);
                                    trace("OK");
                                } else if (choiceIndex == 1) {
                                    trace("1: found.");
                                    stage = STAGE_PROFILE;
                                } else {
                                    trace(choiceIndex.toString() + " invalid choice.");
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (searchAccountMode.equals(SEARCH_MODE_OFFICIAL)) {
                        try {
                            stage = searchUnderOfficialAccountsMode(driver, stage, weixinID, currentIndex, totalWeixinIDs);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    try {
                        if (stage.equals(STAGE_PROFILE)) {
                            trace("Locate button (View History) ... ", false);
                            WebElement btnViewHistory = driver.findElement(By.xpath("//android.widget.TextView[@text='View history' or @text='查看历史消息']"));
                            trace("Found.");

                            trace("Click button (View History)");
                            btnViewHistory.click();

                            trace("Wait for page (History) ... ", false);
                            int retry = RETRY_M;
                            while (retry-- > 0) {
                                Thread.sleep(WAIT_INTERVAL_S);
                                try {
                                    waitOnElement(driver, By.xpath("//android.widget.TextView[@text='history']"), 30);
                                    break;
                                } catch (WebDriverException ignore) {
                                    trace("Retry " + (RETRY_M - retry) + " ", false);
                                }
                            }
                            trace("Loaded.");

                            trace("Get last request from proxy server ... ", false);
                            String lastRequestInfo = getLastRequestFromProxyServer();
                            trace(lastRequestInfo);

                            writeMessageInfo("LastRequestInfo:" + lastRequestInfo + EOL);

                            trace("Fetch history message info list");
                            List<HashMap<String, String>> lstMessageInfoOneAccount = fetchHistoryMessageInfoList(weixinID, lastRequestInfo, dbgNoFetch);
                            if (lstMessageInfoOneAccount == null) {
                                // failed to fetch history message info list
                                trace("Failed.");
                            } else {
                                trace("Found " + lstMessageInfoOneAccount.size() + ".");

                                writeMessageInfoList(lstMessageInfoOneAccount);
                                AccountGenerator.ArchiveWeixinIDCompleted(weixinID);
                            }

                            stage = STAGE_HISTORY;
                        }
                    } catch (NoSuchElementException e) {
                        trace("\tNo history found. Skip.");
                    } catch (ConnectException eCE) {
                        trace("\tFailed to get request info from proxy due to connection error. Proxy program error likely. Stop.");
//                        stage = STAGE_HISTORY;
                        throw new Exception("Failed to fetch last request info. Please check your proxy program.");
                    } catch (IOException eIE) {
                        trace("\tFailed to get request info from proxy. Skip.");
                        stage = STAGE_HISTORY;
                    }

                    try {
                        if (stage.equals(STAGE_HISTORY)) {
                            trace("Locate back button on History Page ... ", false);
                            WebElement btnBackFromHistoryPage = driver.findElement(
                                    By.xpath("//android.widget.LinearLayout[@content-desc='Navigate up']"));
                            trace("Located.");

                            trace("Go back to Profile page ... ", false);
                            btnBackFromHistoryPage.click();
                            WebDriverWait waitForGoBackToProfilePage = new WebDriverWait(driver, 5);
                            waitForGoBackToProfilePage.until(ExpectedConditions.presenceOfElementLocated(
                                    By.xpath(SEARCH_WEIXIN_ID_RESULT_PROFILE)));
                            trace("Arrived.");

                            stage = STAGE_PROFILE;
                        }

                        if (stage.equals(STAGE_PROFILE)) {
                            trace("Locate back button on Profile page and click ... ", false);
                            WebElement btnBackFromProfilePage = driver.findElement(
                                    By.xpath("//android.widget.FrameLayout[@content-desc='Navigate up']"));
                            btnBackFromProfilePage.click();
                            trace("Clicked.");
                        }

                        // test "Official Accounts" mode or "Add Contacts" mode
                        // click back in case of "Official Accounts" mode
                        try {
                            trace("Test if at Official Accounts mode (WeChat) ... ", false);
                            WebElement elmTitleWeChat = driver.findElement(By.xpath("//android.widget.TextView[@text='WeChat']"));
                            elmTitleWeChat.click();
                            trace("Yes and go back.");
                        } catch (NoSuchElementException ignore) {
                            trace("No.");
                        }

                    } catch (NoSuchElementException e) {
                        trace(e.getMessage());
                        e.printStackTrace();
                    }

                    trace(String.format("===== Latest round elapsed: %s of %s, processed %d of %d, avg: %.1f\", est: %s"
                            , timer.addCheckpointAndGetLatestElapsed()
                            , timer.calcTotalElapsed()
                            , currentIndex + 1
                            , totalWeixinIDs
                            , 1.0 * timer.calcTotalElapsedInMill() / 1000 / (currentIndex + 1)
                            , calcLeftEstimation(currentIndex + 1, totalWeixinIDs, timer.calcTotalElapsedInMill())
                    ));
                }

            } catch (NoSuchElementException e) {
                e.printStackTrace();
            }

            trace("continue");

            if (testXPath) {
                trace("Test XPath");
                testXPathOnConsoleTill("quit", br, driver);

                trace("Wait on Settings page then quit.");
                waitOnActivityThenQuit(driver, ".ui.setting.SettingsUI");
            }

            trace("Done.");
            System.out.print("Press ENTER to quit.");
            int read = System.in.read();
            System.out.println(read);

        } catch (
                Exception e
                )

        {
            trace("Exception:" + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
            closeOutputFile();
        }

        trace(EOL);
        trace(String.format("Processed: %d/%d @ %s", currentIndex + 1, totalWeixinIDs,
                (new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")).format(new Date())));
        trace(String.format("===== Total elapsed: %s, avg: %.1f", timer.addCheckpointAndGetTotalElapsed()
                , 1.0 * timer.calcTotalElapsedInMill() / 1000 / (currentIndex + 1)
        ));
    }

    private static String calcLeftEstimation(int processed, int totalWeixinIDs, long elapsedInMill) {
        if (processed == 0) {
            return "N/A";
        }
        long leftInMill = (totalWeixinIDs - processed) * elapsedInMill / processed;
        int hr = Math.round(leftInMill / 3600000);
        int min = Math.round(leftInMill % 3600000 / 60000);
        double sec = 1.0 * (leftInMill % 60000) / 1000;

        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.MILLISECOND, (int) leftInMill);

        return String.format("%02d:%02d:%02.1f(%s)", hr, min, sec
                , (new SimpleDateFormat("MM/dd HH:mm")).format(cal.getTime()));
    }

    private static String searchUnderOfficialAccountsMode(AppiumDriver driver, String stage, String weixinID, int currentIndex, int total) throws Exception {
        trace("Locate and click item (Official Accounts) ... ", false);
        WebElement elmOfficialAccounts = driver.findElement(By.xpath("//android.widget.TextView[@text='Official Accounts']"));
        elmOfficialAccounts.click();
        trace("OK.");

        trace(String.format("Locate and input weixin ID (%s) %d/%d ... ", weixinID, currentIndex + 1, total), false);
        new WebDriverWait(driver, 5).until(ExpectedConditions.presenceOfElementLocated(By.xpath("//android.widget.EditText[@text='Official Accounts']")));
        WebElement inputWeixinIDOfficial = driver.findElement(By.xpath("//android.widget.EditText[@text='Official Accounts']"));
        try {
            inputWeixinIDOfficial.clear();
        } catch (Exception ignored) {
        }
        inputWeixinIDOfficial.sendKeys(weixinID);
        trace("OK.");

        trace("Locate and click search button ... ", false);
        WebElement elmBtnSearch = driver.findElement(By.xpath("//android.widget.Button[@text='Search']"));
        elmBtnSearch.click();
        trace("Clicked.");

        List<By> lstCaseSearchOfficialAccountsResult = new ArrayList<By>(2);
        lstCaseSearchOfficialAccountsResult.add(By.xpath("//android.widget.TextView[@text='Recommended']"));
        lstCaseSearchOfficialAccountsResult.add(By.xpath("//android.widget.RelativeLayout"));
        Thread.sleep(WAIT_INTERVAL_M);
        trace("Wait for search result ... ", false);
        Integer choiceIndex = trySwitch(driver, lstCaseSearchOfficialAccountsResult, 10);
        if (choiceIndex == null) {
            throw new ElementNotFoundException("Weixin ID search result not found on activity:" + driver.currentActivity());
        }
        if (choiceIndex == 0) {
            trace("0: user not exist.");
            AccountGenerator.ArchiveWeixinIDNotExist(weixinID);
        } else if (choiceIndex == 1) {
            trace("1: found.");
            trace("Locate and click the first result ... ", false);
            WebElement elmFirstResult = driver.findElement(By.xpath("(//android.widget.FrameLayout[@content-desc='mm_activity.xml']//android.widget.FrameLayout//android.widget.LinearLayout//android.widget.LinearLayout[descendant::android.widget.RelativeLayout])[1]"));
            elmFirstResult.click();
            trace("OK.");
            stage = STAGE_PROFILE;
        } else {
            trace(choiceIndex.toString() + " invalid choice.");
        }
        return stage;
    }

    private static void prepareOutputFile() throws IOException {
        String folder = makeSureOutputDir();
        String fileName = String.format("WeixinMessagesInfo_%s.txt", (new SimpleDateFormat("yyyyMMdd_HHmmss")).format(new Date()));
        bwOutputFile = createFileWriter(folder, fileName);
    }

    private static BufferedWriter createFileWriter(String folder, String fileName) throws IOException {
        String filePath = folder + File.separator + fileName;
        File file = new File(filePath);
        if (!file.exists()) {
            boolean newFile = file.createNewFile();
            if (!newFile) {
                throw new IOException(String.format("Failed to create file %s", filePath));
            }
        }

        FileWriter fw = new FileWriter(file.getAbsoluteFile());
        return new BufferedWriter(fw);
    }

    private static String makeSureOutputDir() throws IOException {
        String folder = "." + File.separator + "output";
        File dir = new File(folder);
        if (!dir.exists()) {
            boolean mk = dir.mkdirs();
            if (!mk) {
                throw new IOException("Failed to create folder " + folder);
            }
        }
        return folder;
    }

    private static void closeOutputFile() throws IOException {
        if (bwOutputFile != null) {
            bwOutputFile.close();
        }
    }

    private static void writeMessageInfo(String s) throws IOException {
        bwOutputFile.write(s);
        bwOutputFile.flush();
    }

    private static BufferedWriter bwToStorage = null;

    private static void writeMessageInfoList(List<HashMap<String, String>> lstMsgInfo) throws IOException {
        if (bwToStorage == null) {
            String folder = makeSureOutputDir();
            String fileName = String.format("WXMsgInfoToStorage_%s.txt", (new SimpleDateFormat("yyyyMMdd_HHmmss")).format(new Date()));
            bwToStorage = createFileWriter(folder, fileName);
        }

//        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Gson gson = new GsonBuilder().serializeNulls().create();

        for (HashMap<String, String> msgInfo : lstMsgInfo) {
            String s = gson.toJson(msgInfo);
//            trace(s);
            bwToStorage.write(s);
            bwToStorage.newLine();
        }

        bwToStorage.flush();
    }

    private static List<HashMap<String, String>> fetchHistoryMessageInfoList(String weixinID, String lastRequestInfo,
                                                                             boolean dbgNoFetch // return empty result immediately
    )
            throws IOException, InterruptedException {
        List<HashMap<String, String>> messageInfoList = new ArrayList<HashMap<String, String>>(10);

        if (dbgNoFetch) {
            trace("DBG: return w/o real fetching.");
            Thread.sleep(WAIT_INTERVAL_S);
            return messageInfoList;
        }

        String response = fetchResponseByRequestInfoWithRetry(lastRequestInfo, 3);
        response = StringEscapeUtils.unescapeJava(StringEscapeUtils.unescapeHtml4(response));

        if (DBG_OUTPUT_MESSAGE_LIST_HTML) {
            trace(response);
            writeMessageInfo("MessageListResponse:" + response + EOL);
        }

        String msgListInJson = null;
        Pattern reg = Pattern.compile("msgList\\s*=\\s*'?(.+?\\]\\})'?;");
        Matcher m = reg.matcher(response);
        if (m.find()) {
            msgListInJson = m.group(1);
        }

        if (msgListInJson == null) {
            trace("Failed to parse history message list info. response as below:");
            trace(response);
            return null;
        }

        trace("Message meta-data info list ... ", false);
        trace(msgListInJson);

        JsonParser parser = new JsonParser();
        JsonObject msgListObj;
        try {
            msgListObj = parser.parse(msgListInJson).getAsJsonObject();
        } catch (Exception e) {
            trace("Failed to parse history message list info. Possible reason, update is required. response as below:");
            trace(response);
            return null;
        }

        StringBuilder sbMsg = new StringBuilder();
        JsonArray list = msgListObj.get("list").getAsJsonArray();
        int cntMessage = 0;
        for (int i = 0; i < list.size(); i++) {
            // Extract from JSON
            JsonObject msgInfo = list.get(i).getAsJsonObject();

            JsonObject commMsgInfo = msgInfo.get("comm_msg_info").getAsJsonObject();

            // comm_msg_info.id from original weixin JSON response
            String id = commMsgInfo.get("id").getAsString();
            String type = commMsgInfo.get("type").getAsString();
            if (!type.equals("49")) {
                trace("Skip type:" + type);
                continue;
            }
            String dateTime = commMsgInfo.get("datetime").getAsString();

            JsonObject appMsgExtInfo = msgInfo.get("app_msg_ext_info").getAsJsonObject();

            String title = appMsgExtInfo.get("title").getAsString();
            String contentUrl = appMsgExtInfo.get("content_url").getAsString();
            String fileid = appMsgExtInfo.get("fileid").getAsString();
            String is_multi = appMsgExtInfo.get("is_multi").getAsString();

            // Make some logical conversion
            Date d = new Date(Long.parseLong(dateTime) * 1000);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            String strDateTime = (sdf.format(d));
            String mid = extractMidFromContentUrl(contentUrl);
            String groupId = formGroupId(id, mid);

            sbMsg.append("id:").append(id).append(EOL);
            sbMsg.append("fileid:").append(fileid).append(EOL);
            sbMsg.append("DT:").append(strDateTime).append(EOL);
            sbMsg.append("Title:").append(title).append(EOL);
            sbMsg.append("URL:").append(contentUrl).append(EOL);

            Gson gson = new Gson();
            RequestInfo ri = gson.fromJson(lastRequestInfo, RequestInfo.class);
            ri.url = formReportUrl(lastRequestInfo, contentUrl);

            double sec = genSleepSecForNextFetch();
            trace(String.format("Wait %.1f\" then fetch message content for %s %d/%d %s ... ", sec, weixinID, i + 1, list.size(), title), false);
            Thread.sleep((long) (sec * 1000));
            String resTest = fetchResponseByRequestInfoWithRetry(gson.toJson(ri), 3);
            trace("OK.");
            String readNum = extractReadNum(resTest);
            String likeNum = extractLikeNum(resTest);
            writeMessageInfo(ri.url + EOL);
            String report = String.format("===== %d read %s like %s%s", i + 1, readNum, likeNum, EOL);
            writeMessageInfo(report);
            if (DBG_OUTPUT_MESSAGE_FULL_HTML) {
                writeMessageInfo(resTest);
            }
            sbMsg.append("URL_real:").append(ri.url).append(EOL);
            sbMsg.append("Report:").append(report);

            int msgItemIdx = 0;
            String crawlTs = String.valueOf(System.currentTimeMillis() / 1000L);
            addToMessageInfoMap(messageInfoList, weixinID, "" + msgItemIdx, groupId, id, dateTime,
                    title, contentUrl, fileid, is_multi, readNum, likeNum, crawlTs);
            if (++cntMessage >= MAX_MESSAGES_EACH_ACCOUNT) {
                break;
            }

            if (is_multi.equals("1")) {
                JsonArray multiAppMsgItemList = appMsgExtInfo.get("multi_app_msg_item_list").getAsJsonArray();

                for (int j = 0; j < multiAppMsgItemList.size(); j++) {
                    JsonObject msgInfoMulti = multiAppMsgItemList.get(j).getAsJsonObject();

                    String titleMulti = msgInfoMulti.get("title").getAsString();
                    String contentUrlMulti = msgInfoMulti.get("content_url").getAsString();
                    String fileidMulti = msgInfoMulti.get("fileid").getAsString();

                    sbMsg.append("fileidInMulti:").append(fileidMulti).append(EOL);
                    sbMsg.append("Title:").append(titleMulti).append(EOL);
                    sbMsg.append("URL:").append(contentUrlMulti).append(EOL);

                    ri.url = formReportUrl(lastRequestInfo, contentUrlMulti);
                    sec = genSleepSecForNextFetch();
                    trace(String.format("Wait %.1f\" then fetch message content for %s %d/%d - %d/%d %s ... ", sec, weixinID, i + 1, list.size(),
                            j + 1, multiAppMsgItemList.size(), titleMulti), false);
                    Thread.sleep((long) (sec * 1000));
                    resTest = fetchResponseByRequestInfoWithRetry(gson.toJson(ri), 3);
                    trace("OK");
                    String readNumMulti = extractReadNum(resTest);
                    String likeNumMulti = extractLikeNum(resTest);
                    writeMessageInfo(ri.url + EOL);
                    report = String.format("===== %d - %d read %s like %s%s", i + 1, j + 1, readNumMulti, likeNumMulti, EOL);
                    writeMessageInfo(report);
                    if (DBG_OUTPUT_MESSAGE_FULL_HTML) {
                        writeMessageInfo(resTest);
                    }
                    sbMsg.append("URL_real:").append(ri.url).append(EOL);
                    sbMsg.append("Report:").append(report);

                    String crawlTsMulti = String.valueOf(System.currentTimeMillis() / 1000L);
                    addToMessageInfoMap(messageInfoList, weixinID, "" + (j + 1), groupId, id, dateTime,
                            titleMulti, contentUrlMulti, fileidMulti, null, readNumMulti, likeNumMulti, crawlTsMulti);
                    if (++cntMessage >= MAX_MESSAGES_EACH_ACCOUNT) {
                        break;
                    }
                }
                if (cntMessage >= MAX_MESSAGES_EACH_ACCOUNT) {
                    break;
                }
            }
        }

        String msgListInfoAll = sbMsg.toString();
        trace(msgListInfoAll);
        writeMessageInfo("MessageListInfoAll:" + EOL + msgListInfoAll + EOL);

        return messageInfoList;
    }

    private static String extractMidFromContentUrl(String contentUrl) {
        Pattern reg = Pattern.compile("^.+mid=(\\d+)&.+$");
        Matcher m = reg.matcher(contentUrl);
        if (m.find()) {
            return m.group(1);
        } else {
            Pattern regAppMsgId = Pattern.compile("^.+appmsgid=(\\d+)&.+$");
            m = regAppMsgId.matcher(contentUrl);
            if (m.find()) {
                return m.group(1);
            }
        }

        return "";
    }

    private static String formGroupId(String commMsgInfoId, String mid) {
        if (GROUP_ID_USE_MID) {
            return mid;
        } else {
            return commMsgInfoId;
        }
    }

    private static double genSleepSecForNextFetch() {
        return 1.0 * WAIT_INTERVAL_XXS / 1000;
    }

    private static void addToMessageInfoMap(List<HashMap<String, String>> messageInfoList,
                                            String weixinID,
                                            String msgItemIdx, String msgGroupId, String commMsgInfoId, String publishTimestmp,
                                            String title, String contentUrl, String fileid, String isMulti,
                                            String readNum, String likeNum,
                                            String crawlTs
    ) throws IOException {
        HashMap<String, String> msgInfoMap = new HashMap<String, String>(10);
        msgInfoMap.put("WeixinID", weixinID);
        msgInfoMap.put("MessageItemIndex", msgItemIdx);
        msgInfoMap.put("MessageGroupId", msgGroupId);
        msgInfoMap.put("CommMsgInfoId", commMsgInfoId);
        msgInfoMap.put("PublishTimestamp", publishTimestmp);
        msgInfoMap.put("Title", title);
        msgInfoMap.put("ContentURL", contentUrl);
        msgInfoMap.put("WeixinFileID", fileid);
        msgInfoMap.put("IsMulti", isMulti);
        msgInfoMap.put("ReadNum", readNum);
        msgInfoMap.put("LikeNum", likeNum);
        msgInfoMap.put("CrawlTimestamp", crawlTs);
        msgInfoMap.put("PublishDatetime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Long.parseLong(publishTimestmp) * 1000L));
        msgInfoMap.put("CrawlDatetime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Long.parseLong(crawlTs) * 1000L));

//        List<HashMap<String, String>> lstTmp = new ArrayList<HashMap<String, String>>(1);
//        lstTmp.add(msgInfoMap);
//        writeMessageInfoList(lstTmp);

        messageInfoList.add(msgInfoMap);
    }

    private static String extractReadNum(String msgHtml) {
        Pattern reg = Pattern.compile("var\\s+readNum\\s*=\\s*'(.+)'");
        Matcher m = reg.matcher(msgHtml);
        if (m.find()) {
            return m.group(1);
        }

        return "";
    }

    private static String extractLikeNum(String msgHtml) {
        Pattern reg = Pattern.compile("var\\s+likeNum\\s*=\\s*'(.+)'");
        Matcher m = reg.matcher(msgHtml);
        if (m.find()) {
            return m.group(1);
        }

        return "";
    }

    private static String formReportUrl(String lastRequestInfo, String contentUrl) throws MalformedURLException {
        URL urlForMessage = new URL(contentUrl);
        String[] params = urlForMessage.getQuery().split("&");
        Map<String, String> map = new HashMap<String, String>(10);
        for (String param : params) {
            String[] kvs = param.split("=", 2);
            if (kvs.length == 2) {
                map.put(kvs[0], kvs[1]);
            }
        }
        Gson gson = new Gson();
        RequestInfo lastRi = gson.fromJson(lastRequestInfo, RequestInfo.class);
        URL urlForKey = new URL(lastRi.url);
        params = urlForKey.getQuery().split("&");
        for (String param : params) {
            String[] kvs = param.split("=");
            if (kvs.length == 2
                    && !map.containsKey(kvs[0])) {
                map.put(kvs[0], kvs[1]);
            }
        }

        return urlForMessage.getProtocol() + "://" + urlForMessage.getHost() + urlForMessage.getPath() + "?"
                + "__biz=" + map.get("__biz")
                + "&" + "mid=" + map.get("mid")
                + "&" + "idx=" + map.get("idx")
                + "&" + "sn=" + map.get("sn")
                + "&" + "scene=" + map.get("scene")
                + "&" + "uin=" + map.get("uin")
                + "&" + "key=" + map.get("key")
                + "&" + "devicetype=" + map.get("devicetype")
                + "&" + "version=" + map.get("version")
                + "&" + "lang=" + map.get("lang")
                ;
    }

    private static String fetchResponseByRequestInfoWithRetry(String requestInfo, int maxRetry) throws IOException, InterruptedException {
        int retry = 0;
        do {
            try {
                return fetchResponseByRequestInfo(requestInfo);
            } catch (Exception ignore) {
                trace("Fetching failed with retry = " + retry);
                if (retry == 0) {
                    trace("Request:" + requestInfo);
                }
                if (retry++ >= maxRetry) {
                    break;
                }
                Thread.sleep(WAIT_INTERVAL_L);
            }
        } while (true);

        return null;
    }

    private static String fetchResponseByRequestInfo(String requestInfo) throws IOException {
        String[] allowedHeader = {"host", "user-agent"
                , "proxy-connection", "accept", "x-requested-with"
//                , "accept-encoding"   // the original setting could lead to encoding errors
                , "accept-language", "accept-charset"
        };
        List<String> lstAllowedHeaders = Arrays.asList(allowedHeader);

        Gson gson = new Gson();
        RequestInfo ri = gson.fromJson(requestInfo, RequestInfo.class);

        URL url = new URL(ri.url);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setRequestMethod("GET");
        conn.setDoOutput(false);
        for (int i = 0; i < ri.headers.length; i++) {
            String[] kv = ri.headers[i].split("\t");
            if (kv.length == 2 && lstAllowedHeaders.contains(kv[0])) {
                conn.setRequestProperty(kv[0], kv[1]);
            }
        }
        conn.connect();

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append(EOL);
            }
        } catch (IOException ignore) {
        }
        String response = sb.toString();
        br.close();
        return response;
    }

    private static String getLastRequestFromProxyServer() throws IOException {
        java.net.Proxy proxy = new java.net.Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8088));
//        URL url = new URL("http://www.weiboyi.com/getLastRequest/");
        URL url = new URL("http://toufang.weiboyi.com:8080/test.html");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
        conn.setRequestMethod("GET");
        conn.setDoOutput(false);
        conn.connect();
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sbResponse = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sbResponse.append(line);
        }
        br.close();
        return sbResponse.toString();
    }

    private static WebElement waitOnElement(AppiumDriver driver, By by, int timeoutSec) throws InterruptedException {
        while (timeoutSec-- > 0) {
            try {
                return driver.findElement(by);
            } catch (NoSuchElementException ignore) {
            }
            Thread.sleep(WAIT_INTERVAL_XS);
        }

        return null;
    }

    private static Integer trySwitch(AppiumDriver driver, List<By> lstCaseSearchWeixinIDResult, int timeoutSec) throws InterruptedException {
        while (timeoutSec-- > 0) {
            for (int i = 0; i < lstCaseSearchWeixinIDResult.size(); i++) {
                try {
                    driver.findElement(lstCaseSearchWeixinIDResult.get(i));
                    return i;
                } catch (NoSuchElementException ignore) {
                } catch (WebDriverException ignore) {
                }
                if (timeoutSec > 0) {
                    Thread.sleep(WAIT_INTERVAL_XS);
                }
            }
        }

        return null;
    }

    private static void waitForActivity(AppiumDriver driver, int sec, String activity) throws InterruptedException {
        while (sec > 0) {
            if (activity.equals(driver.currentActivity())) {
                return;
            }
            Thread.sleep(WAIT_INTERVAL_S);
            sec--;
        }

        throw new TimeoutException();
    }

    private static void testXPathOnConsoleTill(String quit, BufferedReader br, AppiumDriver driver) throws IOException {
        while (true) {
            System.out.print("xpath (quit to exit):");

            String xpath = br.readLine();
            if (quit.equals(xpath)) {
                return;
            }

            List<WebElement> elms = driver.findElements(By.xpath(xpath));
            if (elms.size() == 0) {
                System.out.println("Not found.");
            } else {
                System.out.println("Found: " + elms.size());
                for (WebElement elm : elms) {
                    String s = elm.getLocation() + "\t" + elm.getText();
                    System.out.println(s);
                }
            }
        }
    }

    private static void waitOnActivityThenQuit(AppiumDriver driver, String activityToWait) throws WaitThenQuitException {
        try {
            while (driver != null) {
                if (activityToWait.equals(driver.currentActivity())) {
                    throw new WaitThenQuitException("waitOnActivityThenQuit");
                }
                Thread.sleep(WAIT_INTERVAL_S);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void tryPrintActivity(AppiumDriver driver, int seconds) {
        while (seconds-- > 0) {
            trace(driver.currentActivity());

            try {
                Thread.sleep(WAIT_INTERVAL_S);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void trace(String msg) {
        System.out.println(msg);
    }

    private static void trace(String msg, Boolean hasEOL) {
        if (hasEOL) {
            System.out.println(msg);
        } else {
            System.out.print(msg);
        }
    }

    private static void actionOnExceptionElementNotFound(Exception exp) {
        System.out.println(exp.getMessage());
    }
}
