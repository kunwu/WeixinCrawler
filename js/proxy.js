try {
    var http = require('http'),
        httpProxy = require('http-proxy');

    var proxy = httpProxy.createProxyServer({});
    var requestInfoForHistoryList = null;

    var server = require('http').createServer(function (req, res) {

        console.log('==> ' + req.url);

        if (req.url.indexOf('http://monitor.uu.qq.com/analytics/upload') == 0) {
            console.log('skip tencent analytics');
            res.writeHead(200);
            res.end();
        } else if (req.url.indexOf('http://mp.weixin.qq.com/mp/getmasssendmsg?') == 0) {
            console.log(req.url);
            var aryHeaders = [];
            var idx = 0;
            for (var k in req.headers) {
                if (req.headers.hasOwnProperty(k)) {
                    console.log(k + '->' + req.headers[k]);
                    aryHeaders[idx++] = k + '\t' + req.headers[k];
                }
            }
            requestInfoForHistoryList = JSON.stringify({url:req.url, headers:aryHeaders});
            res.writeHead(200);
            res.write('<html><head><title>history</title></head><body>content stripped by proxy</body></html>');
            res.end();
        } else if (req.url == 'http://toufang.weiboyi.com:8080/test.html') {
            console.log('Send:' + requestInfoForHistoryList);
            res.writeHead(200);
            res.write(requestInfoForHistoryList);
            res.end();
        } else {
            proxy.web(req, res, { target: req.url });
        }
    });

    console.log("listening on port 8088")
    server.listen(8088);
} catch (e) {
    console.log(e.getMessage());
    console.log(e.getStacktrace());
}
