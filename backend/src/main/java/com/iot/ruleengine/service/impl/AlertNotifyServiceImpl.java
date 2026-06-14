package com.iot.ruleengine.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.iot.ruleengine.entity.AlertNotifyConfig;
import com.iot.ruleengine.entity.AlertRecord;
import com.iot.ruleengine.repository.AlertRecordRepository;
import com.iot.ruleengine.service.AlertNotifyConfigService;
import com.iot.ruleengine.service.AlertNotifyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class AlertNotifyServiceImpl implements AlertNotifyService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlertNotifyConfigService alertNotifyConfigService;

    private final AlertRecordRepository alertRecordRepository;

    @Autowired
    public AlertNotifyServiceImpl(AlertNotifyConfigService alertNotifyConfigService,
                                  AlertRecordRepository alertRecordRepository) {
        this.alertNotifyConfigService = alertNotifyConfigService;
        this.alertRecordRepository = alertRecordRepository;
    }

    @Override
    @Async
    public void sendNotifications(AlertRecord alertRecord) {
        List<AlertNotifyConfig> configs = alertNotifyConfigService.listEnabledConfigs();
        if (configs.isEmpty()) {
            log.info("未找到已启用的告警通知配置，跳过通知发送, alertId={}", alertRecord.getId());
            return;
        }

        String level = alertRecord.getLevel() != null ? alertRecord.getLevel() : "info";
        String title = buildTitle(alertRecord);
        String content = buildContent(alertRecord);

        List<String> sentChannels = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (AlertNotifyConfig config : configs) {
            if (!isLevelEnabled(config, level)) {
                continue;
            }

            JSONObject configJson = parseConfigJson(config.getConfig());
            boolean result = false;

            try {
                switch (config.getChannel()) {
                    case "dingtalk":
                        result = sendDingTalk(
                                configJson.getString("webhookUrl"),
                                configJson.getString("secret"),
                                configJson.getString("messageType"),
                                title, content);
                        break;
                    case "wecom":
                        result = sendWeCom(configJson.getString("webhookUrl"), content);
                        break;
                    case "email":
                        result = sendEmail(
                                configJson.getString("host"),
                                configJson.getInteger("port"),
                                configJson.getString("username"),
                                configJson.getString("password"),
                                configJson.getString("from"),
                                configJson.getString("to"),
                                title, content);
                        break;
                    default:
                        log.warn("未知通知渠道: {}", config.getChannel());
                        continue;
                }
            } catch (Exception e) {
                log.error("发送通知异常, channel={}, alertId={}", config.getChannel(), alertRecord.getId(), e);
            }

            if (result) {
                successCount++;
                sentChannels.add(config.getChannel());
            } else {
                failCount++;
            }
        }

        int notifyStatus;
        if (successCount == 0 && failCount > 0) {
            notifyStatus = 3;
        } else if (failCount > 0) {
            notifyStatus = 1;
        } else if (successCount > 0) {
            notifyStatus = 2;
        } else {
            notifyStatus = 0;
        }

        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AlertRecord> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.eq("id", alertRecord.getId())
                .set("notify_channels", String.join(",", sentChannels))
                .set("notify_status", notifyStatus);
        alertRecordRepository.update(null, updateWrapper);

        log.info("告警通知发送完成, alertId={}, sentChannels={}, notifyStatus={}",
                alertRecord.getId(), sentChannels, notifyStatus);
    }

    @Override
    public boolean sendDingTalk(String webhookUrl, String secret, String messageType, String title, String content) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("钉钉Webhook URL未配置，跳过发送");
            return false;
        }

        try {
            String url = webhookUrl;
            if (secret != null && !secret.isEmpty()) {
                long timestamp = System.currentTimeMillis();
                String sign = generateDingTalkSign(timestamp, secret);
                url += "&timestamp=" + timestamp + "&sign=" + URLEncoder.encode(sign, "UTF-8");
            }

            JSONObject body = new JSONObject();
            if ("markdown".equals(messageType)) {
                body.put("msgtype", "markdown");
                JSONObject markdown = new JSONObject();
                markdown.put("title", title);
                markdown.put("text", content);
                body.put("markdown", markdown);
            } else {
                body.put("msgtype", "text");
                JSONObject text = new JSONObject();
                text.put("content", content);
                body.put("text", text);
            }

            String response = httpPost(url, body.toJSONString());
            JSONObject respJson = JSON.parseObject(response);
            if (respJson != null && respJson.getInteger("errcode") != null && respJson.getInteger("errcode") == 0) {
                log.info("钉钉通知发送成功");
                return true;
            } else {
                log.warn("钉钉通知发送失败, response={}", response);
                return false;
            }
        } catch (Exception e) {
            log.error("钉钉通知发送异常", e);
            return false;
        }
    }

    @Override
    public boolean sendWeCom(String webhookUrl, String content) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("企业微信Webhook URL未配置，跳过发送");
            return false;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("msgtype", "text");
            JSONObject text = new JSONObject();
            text.put("content", content);
            body.put("text", text);

            String response = httpPost(webhookUrl, body.toJSONString());
            JSONObject respJson = JSON.parseObject(response);
            if (respJson != null && respJson.getInteger("errcode") != null && respJson.getInteger("errcode") == 0) {
                log.info("企业微信通知发送成功");
                return true;
            } else {
                log.warn("企业微信通知发送失败, response={}", response);
                return false;
            }
        } catch (Exception e) {
            log.error("企业微信通知发送异常", e);
            return false;
        }
    }

    @Override
    public boolean sendEmail(String host, Integer port, String username, String password,
                             String from, String to, String subject, String content) {
        if (host == null || host.isEmpty() || to == null || to.isEmpty()) {
            log.warn("邮件服务器配置不完整，跳过发送");
            return false;
        }

        try {
            java.util.Properties props = new java.util.Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", port != null ? port : 465);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.connectiontimeout", "10000");

            javax.mail.Session session = javax.mail.Session.getInstance(props, new javax.mail.Authenticator() {
                @Override
                protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new javax.mail.PasswordAuthentication(username, password);
                }
            });

            javax.mail.internet.MimeMessage message = new javax.mail.internet.MimeMessage(session);
            message.setFrom(new javax.mail.internet.InternetAddress(from));
            message.setRecipients(javax.mail.Message.RecipientType.TO,
                    javax.mail.internet.InternetAddress.parse(to));
            message.setSubject(subject, "UTF-8");
            message.setContent(content, "text/html; charset=UTF-8");
            message.setSentDate(new Date());

            javax.mail.Transport.send(message);
            log.info("邮件通知发送成功, to={}", to);
            return true;
        } catch (Exception e) {
            log.error("邮件通知发送异常", e);
            return false;
        }
    }

    private String generateDingTalkSign(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signData);
    }

    private String httpPost(String urlStr, String body) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            InputStream is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (is == null) {
                return "{}";
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private boolean isLevelEnabled(AlertNotifyConfig config, String level) {
        if (config.getEnabledLevels() == null || config.getEnabledLevels().isEmpty()) {
            return true;
        }
        String[] levels = config.getEnabledLevels().split(",");
        for (String l : levels) {
            if (l.trim().equalsIgnoreCase(level)) {
                return true;
            }
        }
        return false;
    }

    private JSONObject parseConfigJson(String configStr) {
        if (configStr == null || configStr.isEmpty()) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(configStr);
        } catch (Exception e) {
            log.warn("解析通知配置JSON失败: {}", configStr);
            return new JSONObject();
        }
    }

    private String buildTitle(AlertRecord alertRecord) {
        String levelName = getLevelName(alertRecord.getLevel());
        return "[IoT告警-" + levelName + "] " + alertRecord.getMessage();
    }

    private String buildContent(AlertRecord alertRecord) {
        StringBuilder sb = new StringBuilder();
        String levelName = getLevelName(alertRecord.getLevel());
        sb.append("### IoT规则引擎告警通知\n\n");
        sb.append("**告警级别**: ").append(levelName).append("\n\n");
        sb.append("**告警消息**: ").append(alertRecord.getMessage()).append("\n\n");
        if (alertRecord.getRuleName() != null) {
            sb.append("**关联规则**: ").append(alertRecord.getRuleName()).append("\n\n");
        }
        if (alertRecord.getDeviceId() != null) {
            sb.append("**关联设备**: ").append(alertRecord.getDeviceId()).append("\n\n");
        }
        if (alertRecord.getDetail() != null) {
            sb.append("**详细信息**: ").append(alertRecord.getDetail()).append("\n\n");
        }
        if (alertRecord.getCreateTime() != null) {
            sb.append("**告警时间**: ").append(alertRecord.getCreateTime().format(DTF)).append("\n\n");
        }
        return sb.toString();
    }

    private String getLevelName(String level) {
        if (level == null) return "提示";
        switch (level) {
            case "critical": return "严重";
            case "warning": return "警告";
            case "info": return "提示";
            default: return level;
        }
    }
}
