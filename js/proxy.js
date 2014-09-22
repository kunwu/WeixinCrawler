try {
    var http = require('http'),
        httpProxy = require('http-proxy');

    var proxy = httpProxy.createProxyServer({});
    var requestInfoForHistoryList = null;

    var server = require('http').createServer(function (req, res) {

        var ts = new Date().toISOString().replace(/[-:Z]/g, '').replace('T', '_');

        console.log(ts + '|' + req.url);

        if (req.url.indexOf('http://monitor.uu.qq.com/analytics/upload') == 0) {
            console.log('skip tencent analytics.');
            res.writeHead(200);
            res.end();
        } else if (req.url.indexOf('http://mp.weixin.qq.com/mp/getmasssendmsg?') == 0) {
            var aryHeaders = [];
            var idx = 0;
            for (var k in req.headers) {
                if (req.headers.hasOwnProperty(k)) {
                    aryHeaders[idx++] = k + '\t' + req.headers[k];
                }
            }
            requestInfoForHistoryList = JSON.stringify({url: req.url, headers: aryHeaders});

            var fs = require('fs');
            var dir = './log';
            var path = dir + '/historyList_' + new Date().toISOString().replace(/(^\d{1,4})\D(\d{1,2})\D(\d{1,2}).*$/, '$1$2$3') + '.txt';

            fs.exists(dir, function (exists) {
                if (!exists) {
                    fs.mkdirSync(dir);
                }

                fs.appendFile(path, ts + '|' + requestInfoForHistoryList + '\n');
            });

            res.writeHead(200);
            res.write('<html><head><title>history</title></head><body>content stripped by proxy</body></html>');
            res.end();
        } else if (req.url == 'http://toufang.weiboyi.com:8080/test.html') {
            res.writeHead(200);
            res.write(requestInfoForHistoryList);
            res.end();
            requestInfoForHistoryList = '';
        } else {
            console.log('skip unknown traffic.')
//            proxy.web(req, res, { target: req.url });
            res.writeHead(200);
            res.end();
        }
    });

    proxy.on('error', function (e, req, res) {
        var errorMessage = "Error caught:" + e.code + ":" + e.message;
        console.log(errorMessage);
        res.writeHead(500, {
            'Content-Type': 'text/plain'
        });
        res.write(errorMessage);
        res.end();
    });

    console.log("listening on port 8088")
    server.listen(8088);

} catch (e) {
    console.log(e.getMessage());
    console.log(e.getStacktrace());
}
