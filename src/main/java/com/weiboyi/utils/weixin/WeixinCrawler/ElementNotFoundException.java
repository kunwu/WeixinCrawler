package com.weiboyi.utils.weixin.WeixinCrawler;

public class ElementNotFoundException extends Exception {

    private String searchCondition;

    public ElementNotFoundException(String searchCondition) {
        this.searchCondition = searchCondition;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + ":searchCondition:" + searchCondition;
    }
}
