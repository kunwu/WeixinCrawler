#!/bin/bash

while true
do
    date
    echo Start WeixinCrawler ...
    mvn compile exec:java -Dexec.mainClass="com.weiboyi.utils.weixin.WeixinCrawler.App" -Dexec.args="_xpath searchOfficialsOnly"
    sleep 10
done
