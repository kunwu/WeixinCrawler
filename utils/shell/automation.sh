#!/bin/bash

while true
do
    date
    echo Start WeixinCrawler ...
    mvn compile exec:java -Dexec.mainClass="com.weiboyi.utils.weixin.WeixinCrawler.App" -Dexec.args="_xpath searchOfficialsOnly"
    echo Check automation flag file existence.
    if [ ! -f ./automation.flag ]; then
        echo File ./automation.flag not existed. Quit.
        exit 0
    fi
    echo Sleep 10 seconds then repeat.
    sleep 10
done
