package com.iot.ruleengine.service;

import com.iot.ruleengine.entity.AlertRecord;

public interface AlertNotifyService {

    void sendNotifications(AlertRecord alertRecord);

    boolean sendDingTalk(String webhookUrl, String secret, String messageType, String title, String content);

    boolean sendWeCom(String webhookUrl, String content);

    boolean sendEmail(String host, Integer port, String username, String password,
                      String from, String to, String subject, String content);
}
