<?php

error_reporting(E_ALL & ~E_NOTICE);

date_default_timezone_set("Asia/Hong_Kong");

if ($argc < 2) {
    echo "Usage:" . PHP_EOL;
    echo "    {$argv[0]} input_file";
    die;
}

$jsonLines = array();
for ($i = 1; $i < count($argv); $i++) {
    $filePath = $argv[$i];
    if (!file_exists($filePath)) {
        logger::error("File not found:" . $filePath);
        continue;
    }

    $jsonLinesOneFile = array();
    foreach (explode(PHP_EOL, file_get_contents($filePath)) as $line) {
        $line = trim($line);
        if (empty($line)) {
            continue;
        }
        $tryJson = json_decode($line);
        if (json_last_error() != JSON_ERROR_NONE) {
            continue;
        }
        $jsonLinesOneFile[] = $line;
    }
    logger::info("Load " . count($jsonLinesOneFile) . " JSON lines from file " . $filePath);
    $jsonLines = array_merge($jsonLines, $jsonLinesOneFile);
}

logger::info("" . count($jsonLines) . " JSON lines loaded in total from " . (count($argv) - 1) . " files.");

$dbLink = new mysqli("localhost", "root", "", "babysitter");
$dbLink->connect();
$dbLink->set_charset("utf8");

$fileJson = $argv[1];

logger::info("JSON lines loaded: " . count($jsonLines));

$sqlList = array();
foreach ($jsonLines as $json) {
    if (empty($json)) {
        continue;
    }
    $msgInfoOneEntry = json_decode($json);

    $weixinID = $dbLink->real_escape_string($msgInfoOneEntry->WeixinID);
    if (empty($weixinID)) {
        continue;
    }
    $readNum = is_numeric($msgInfoOneEntry->ReadNum) ? $msgInfoOneEntry->ReadNum : 0;
    $likeNum = is_numeric($msgInfoOneEntry->LikeNum) ? $msgInfoOneEntry->LikeNum : 0;
    $title = utils::removeEmoji($dbLink->real_escape_string($msgInfoOneEntry->Title));
    $contentURL = utils::removeEmoji($dbLink->real_escape_string($msgInfoOneEntry->ContentURL));
    if (isset($msgInfoOneEntry->IsMulti)) {
        $isMulti = $msgInfoOneEntry->IsMulti;
    } else {
        $isMulti = 'null';
    }

    if ($msgInfoOneEntry->MessageGroupId == 35408) {
        print_r($msgInfoOneEntry);
    }

    $mid = utils::extractMid($contentURL);
    $appmsgid = utils::extractAppMsgId($contentURL);
    if (empty($msgInfoOneEntry->MessageGroupId)) {
        $MessageGroupId = $appmsgid;
    } else {
        $MessageGroupId = $msgInfoOneEntry->MessageGroupId;
    }
    if (isset($msgInfoOneEntry->CommMsgInfoId)) {
        $commMsgInfoId = $msgInfoOneEntry->CommMsgInfoId;
    } else {
        $commMsgInfoId = $MessageGroupId;
        $MessageGroupId = empty($mid) ? $appmsgid : $mid;
    }
    if (empty($MessageGroupId)) {
        $MessageGroupId = 'null';
    }
    if (empty($commMsgInfoId)) {
        $commMsgInfoId = 'null';
    }

    $sql = "
INSERT INTO babysitter_weixin_message_info_fetch_history
(weixin_id, comm_msg_info_id, message_group_id, message_item_index, is_multi, title, content_url, publish_time, read_num, like_num, fetched_time)
VALUES
(
'{$weixinID}'
, {$commMsgInfoId}
, {$MessageGroupId}
, {$msgInfoOneEntry->MessageItemIndex}
, {$isMulti}
, '{$title}'
, '{$contentURL}'
, from_unixtime({$msgInfoOneEntry->PublishTimestamp})
, {$readNum}
, {$likeNum}
, from_unixtime({$msgInfoOneEntry->CrawlTimestamp})
);
    ";

    $sqlList[] = trim(preg_replace("/[\\r\\n]/", " ", $sql));
}
$dbLink->close();

$sqlOutput = PHP_EOL . implode(PHP_EOL, $sqlList);
logger::info($sqlOutput);

logger::info("END.");

class utils
{
    public static function removeNonUtf8($string)
    {
        $regex = <<<'END'
/
  (
    (?: [\x00-\x7F]                 # single-byte sequences   0xxxxxxx
    |   [\xC0-\xDF][\x80-\xBF]      # double-byte sequences   110xxxxx 10xxxxxx
    |   [\xE0-\xEF][\x80-\xBF]{2}   # triple-byte sequences   1110xxxx 10xxxxxx * 2
    |   [\xF0-\xF7][\x80-\xBF]{3}   # quadruple-byte sequence 11110xxx 10xxxxxx * 3
    ){1,100}                        # ...one or more times
  )
| .                                 # anything else
/x
END;
        return preg_replace($regex, '$1', $string);
    }

    public static function removeEmoji($text)
    {
        // Match Emoticons
        $regexEmoticons = '/[\x{1F600}-\x{1F64F}]/u';
        $clean_text = preg_replace($regexEmoticons, '', $text);

        // Match Miscellaneous Symbols and Pictographs
        $regexSymbols = '/[\x{1F300}-\x{1F5FF}]/u';
        $clean_text = preg_replace($regexSymbols, '', $clean_text);

        // Match Transport And Map Symbols
        $regexTransport = '/[\x{1F680}-\x{1F6FF}]/u';
        $clean_text = preg_replace($regexTransport, '', $clean_text);

        // Match Miscellaneous Symbols
        $regexMisc = '/[\x{2600}-\x{26FF}]/u';
        $clean_text = preg_replace($regexMisc, '', $clean_text);

        // Match Dingbats
        $regexDingbats = '/[\x{2700}-\x{27BF}]/u';
        $clean_text = preg_replace($regexDingbats, '', $clean_text);

        // Match flags (iOS)
        $regexTransport = '/[\x{1F1E0}-\x{1F1FF}]/u';
        $clean_text = preg_replace($regexTransport, '', $clean_text);

        return $clean_text;
    }

    public static function extractAppMsgId($contentURL)
    {
        if (strpos($contentURL, '&appmsgid=') === false) {
            return '';
        }
        return preg_replace('/^.+appmsgid=(\d+)&.+$/', '\1', $contentURL);
    }

    public static function extractMid($contentURL)
    {
        if (strpos($contentURL, '&mid=') === false) {
            return '';
        }
        return preg_replace('/^.+mid=(\d+)&.+$/', '\1', $contentURL);
    }
}

class logger
{
    private static $logFilePath = null;

    public static function info($msg)
    {
        $type = "INFO";
        self::write($msg, $type);
    }

    private static function makeSureLogFileExists()
    {
        if (self::$logFilePath == null) {
            $folder = __DIR__ . DIRECTORY_SEPARATOR . "log";
            if (!file_exists($folder)) {
                mkdir($folder);
            }

            self::$logFilePath = $folder . DIRECTORY_SEPARATOR . basename(__FILE__, ".php") . date('_Ymd') . ".log";
            touch(self::$logFilePath);
        }
    }

    public static function error($msg)
    {
        self::write($msg, "ERROR");
    }

    /**
     * @param $msg
     * @param $type
     */
    public static function write($msg, $type)
    {
        $output = $type . "|" . date('Ymd_His|') . $msg . PHP_EOL;

        self::makeSureLogFileExists();
        file_put_contents(self::$logFilePath, $output, FILE_APPEND);

        echo $output;
    }
}
