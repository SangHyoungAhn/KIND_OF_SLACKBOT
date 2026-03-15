package org.core.kindof.slackbot.service;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;

import lombok.extern.slf4j.Slf4j;
import org.core.kindof.slackbot.constatnts.SlackConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SlackService {
    @Value("${slack.token}")
    private String token;

    public void sendMessage(String message, String channel) {
        String channelAddress = "";
        if("error".equals(channel)){
            channelAddress = SlackConstants.ERROR;
        }else if("batch".equals(channel)){
            channelAddress = SlackConstants.BATCH;
        }else if("convert".equals(channel)){
            channelAddress = SlackConstants.CONVERT;
        }else if("test".equals(channel)){
            channelAddress = SlackConstants.TEST;
        }

        try{
            MethodsClient methods = Slack.getInstance().methods(token);

            ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                    .channel(channelAddress)
                    .text(message)
                    .build();

            methods.chatPostMessage(request);
            log.info("Slack - Test Message 전송완료 : {}", message);
        }catch(Exception e){
            log.warn("Slack Error - {}", e.getMessage());
        }
    }


}
