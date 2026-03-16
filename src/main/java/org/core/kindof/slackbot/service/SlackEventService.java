package org.core.kindof.slackbot.service;

import com.slack.api.bolt.App;
import com.slack.api.methods.MethodsClient;
import com.slack.api.model.Message;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.event.MessageBotEvent;
import com.slack.api.model.event.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.core.kindof.slackbot.handler.SlackMeetingHandler;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SlackEventService {

    private final App slackApp;
    private final SlackMeetingHandler meetingHandler;

    public SlackEventService(App slackApp, SlackMeetingHandler meetingHandler) {
        this.slackApp = slackApp;
        this.meetingHandler = meetingHandler;
        this.init();
    }

    private void init() {
        // 1. [사람] 메시지 감지
        slackApp.event(MessageEvent.class, (payload, ctx) -> {
            MessageEvent event = payload.getEvent();
            handleEvent(event.getText(), event.getBlocks(), ctx.client());
            return ctx.ack();
        });

        // 2. [봇/워크플로우] 메시지 감지
        slackApp.event(MessageBotEvent.class, (payload, ctx) -> {
            log.info(">>>> [DETECT] 봇(워크플로우) 메시지 감지!");
            MessageBotEvent event = payload.getEvent();
            handleEvent(event.getText(), event.getBlocks(), ctx.client());
            return ctx.ack();
        });
    }

    // 공통 필터링 로직
    private void handleEvent(String text, List<LayoutBlock> blocks, com.slack.api.methods.MethodsClient client) {
        // 텍스트가 없으면 블록 데이터를 문자열로 변환해서라도 내용을 확인합니다.
        String contentText = (text != null) ? text : (blocks != null ? blocks.toString() : "");

        if (contentText.contains("날짜/시간") && contentText.contains("참석") && contentText.contains("안건")) {
            log.info(">>>> [SUCCESS] 워크플로우 조건 일치! 분석 시작.");
            processWorkflowLogic(contentText, client); // 여기서 핵심 로직 호출!
        }
    }

    // ⭐ 핵심 로직 (상형님이 만드셨던 handleWorkflowMessage의 내용)
    private void processWorkflowLogic(String fullText, com.slack.api.methods.MethodsClient client) {
        log.info(">>>> [DEBUG] 분석할 전체 텍스트: {}", fullText);

        // 1. 참석자 ID 리스트 추출 (<@U12345> 패턴)
        List<String> userIds = new ArrayList<>();
        Pattern userPattern = Pattern.compile("<@([A-Z0-9]+)>");
        Matcher userMatcher = userPattern.matcher(fullText);
        while (userMatcher.find()) {
            userIds.add(userMatcher.group(1));
        }

        log.info(">>>> [DEBUG] 추출된 사용자 ID 리스트: {}", userIds);

        if (userIds.isEmpty()) {
            log.warn(">>>> [STOP] 멘션된 사용자가 없어 발송을 중단합니다.");
            return;
        }

        // 2. 안건 내용 추출
        String content = "상세 내용을 확인해 주세요.";
        if (fullText.contains("안건")) {
            String[] split = fullText.split("안건");
            if (split.length > 1) {
                content = split[1].trim();
            }
        }

        // 3. DM 발송 (Handler 재사용)
        meetingHandler.sendMeetingDMs("Workflow_Bot", userIds, "📅 워크플로우 회의 알림", content, "메시지 내 일시 참조", client);
        log.info(">>>> [FINISH] {}명에게 알림 발송 완료!", userIds.size());
    }
}
