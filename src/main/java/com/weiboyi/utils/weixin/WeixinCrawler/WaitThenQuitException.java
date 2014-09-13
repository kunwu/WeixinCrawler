package com.weiboyi.utils.weixin.WeixinCrawler;

/**
 * Created by kunwu on 8/15/14.
 */
public class WaitThenQuitException extends Exception {

    String _reason;

    public WaitThenQuitException(String reason) {
        _reason = reason;
    }
}
