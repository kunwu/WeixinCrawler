CREATE TABLE `babysitter_weixin_message_info_fetch_history` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `weixin_id` varchar(45) NOT NULL,
  `message_group_id` bigint(20) unsigned NOT NULL COMMENT '每“组”微信文章的标识ID（微信原生数据）',
  `message_item_index` tinyint(4) unsigned NOT NULL,
  `is_multi` tinyint(3) unsigned DEFAULT NULL COMMENT '0 - 单图文，1 - 多图文，且第一条，NULL - 多图文的非第一条',
  `title` text,
  `content_url` text,
  `publish_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '文章发布的时间戳（微信原生数据）',
  `read_num` int(11) unsigned NOT NULL DEFAULT '0',
  `like_num` int(11) unsigned NOT NULL DEFAULT '0',
  `fetched_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00' COMMENT '抓取的时间戳',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uidx_gid_iid_ftm` (`weixin_id`,`message_group_id`,`message_item_index`,`fetched_time`)
) ENGINE=MyISAM AUTO_INCREMENT=699 DEFAULT CHARSET=utf8 COMMENT='从微信抓取消息的完整记录历史';
