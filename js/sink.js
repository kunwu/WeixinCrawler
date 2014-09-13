var http = require('http');

http.createServer(function (request, response) {
    if (request.url.indexOf('http://mp.weixin.qq.com/mp/getmasssendmsg?') == 0) {
        console.log(request.url);
        for (var k in request.headers) {
            if (request.headers.hasOwnProperty(k)) {
                console.log(k + '->' + request.headers[k]);
            }
        }
    }
    response.writeHead(200, request.headers);
    response.write('completed.');
    response.end();
}).listen(8088);

console.log("Start.");