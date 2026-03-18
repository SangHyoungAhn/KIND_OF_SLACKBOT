package org.core.kindof.slackbot.service;

import com.slack.api.bolt.App;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.block.Blocks;
import com.slack.api.model.block.composition.BlockCompositions;
import java.util.List;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.event.MessageBotEvent;
import com.slack.api.model.event.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.core.kindof.slackbot.handler.SlackMeetingHandler;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.button;

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
            MessageBotEvent event = payload.getEvent();
            String text = event.getText();

            // 1. RT 등록 메시지인지 확인
            if (text != null && text.matches("(?s).*새로운 RT.*등록되었습니다.*")) {
                log.info(">>>> [MATCH] RT 등록 메시지 감지!");

                try {
                    // 중복 방지를 위해 Set 사용 (한 명을 두 번 멘션해도 DM은 한 번만!)
                    Set<String> managers = new LinkedHashSet<>();

                    // 2. [섹션 추출] *담당자* 섹션만 따로 떼어내기
                    // (?s): 줄바꿈 포함 / \\*담당자\\*: 시작점 / (?=\\*상태\\*): 다음 항목인 '*상태*' 직전까지
                    Pattern sectionPattern = Pattern.compile("(?s)\\*담당자\\*(.*?)(?=\\*상태\\*)", Pattern.DOTALL);
                    Matcher sectionMatcher = sectionPattern.matcher(text);

                    if (sectionMatcher.find()) {
                        String assigneeSection = sectionMatcher.group(1);
                        log.info(">>>> [DEBUG] 담당자 섹션 분석: {}", assigneeSection);

                        // 3. [멘션 추출] 해당 섹션 내의 모든 유저 ID(<@U...>) 추출
                        Pattern userPattern = Pattern.compile("<@([A-Z0-9]+)>");
                        Matcher userMatcher = userPattern.matcher(assigneeSection);

                        while (userMatcher.find()) {
                            managers.add(userMatcher.group(1));
                        }
                    }

                    // 4. 담당자가 존재할 때만 후속 작업 진행
                    if (!managers.isEmpty()) {
                        log.info(">>>> [EXTRACT 성공] 담당자 {}명 발견: {}", managers.size(), managers);

                        // A. 원본 채널 스레드에 [확인 버튼] 달기 (담당자 전원 멘션 포함)
                        String mentionTargets = managers.stream().map(id -> "<@" + id + ">").collect(Collectors.joining(", "));

                        ctx.client().chatPostMessage(r -> r
                                .token(ctx.getBotToken())
                                .channel(event.getChannel())
                                .threadTs(event.getTs())
                                .text("🚀 RT 확인 요청 알림")
                                .blocks(asBlocks(
                                        section(s -> s.text(markdownText("담당자분들(" + mentionTargets + ")은 확인 후 아래 버튼을 눌러주세요! 👇"))),
                                        actions(a -> a.elements(List.of(
                                                button(b -> b.text(plainText("RT 확인 완료", true)).actionId("btn_rt_confirm").style("primary"))
                                        )))
                                ))
                        );

                        // B. 추출된 모든 담당자에게 각각 개인 DM 발송
                        for (String managerId : managers) {
                            ctx.client().chatPostMessage(m -> m
                                    .token(ctx.getBotToken())
                                    .channel(managerId)
                                    .text("📢 새로운 RT 할당 알림")
                                    .blocks(asBlocks(
                                            section(s -> s.text(markdownText("📢 *상형님, 새로운 RT가 할당되었습니다!*\n채널의 스레드에서 *[확인 완료]* 버튼을 눌러주세요.")))
                                    ))
                            );
                        }
                    } else {
                        log.warn(">>>> [SKIP] 담당자 구역에서 멘션을 찾지 못했습니다.");
                    }

                } catch (Exception e) {
                    log.error(">>>> [ERROR] 다수 담당자 처리 중 에러: ", e);
                }
            }
            return ctx.ack();
        });

        // 담당자가 버튼을 눌렀을 때 실행되는 로직
        slackApp.blockAction("btn_rt_confirm", (req, ctx) -> {
            log.info(">>>> [SUCCESS] 버튼 클릭 신호 도착!");

            String userId = req.getPayload().getUser().getId();
            String channelId = req.getPayload().getChannel().getId();
            String messageTs = req.getPayload().getContainer().getMessageTs(); // 버튼 메시지의 TS

            try {
                String currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

                ctx.client().chatUpdate(u -> u
                        .token(ctx.getBotToken())
                        .channel(channelId)
                        .ts(messageTs)
                        .text("🔴 RT 확인 완료")
                        .blocks(List.of(
                                section(s -> s.text(
                                        markdownText(
                                                "🔴 *RT 확인 완료* (담당자: <@" + userId + ">님 / 시각: " + currentTime + ")"
                                        )
                                ))
                        ))
                );
            } catch (Exception e) {
                log.error(">>>> 버튼 상태 업데이트 실패: ", e);
            }

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

    private void processWorkflowLogic(String fullText, com.slack.api.methods.MethodsClient client) {
        log.info(">>>> [DEBUG] 분석할 전체 텍스트: {}", fullText);

        // 1. 참석자 ID 추출 (중복 제거용 Set)
        Set<String> participantSet = new LinkedHashSet<>();
        Pattern userPattern = Pattern.compile("<@([A-Z0-9]+)>");
        Matcher userMatcher = userPattern.matcher(fullText);

        while (userMatcher.find()) {
            participantSet.add(userMatcher.group(1));
        }

        // 2. 리스트 변환 (중복이 제거된 최종 명단)
        List<String> userIds = new ArrayList<>(participantSet);

        if (userIds.isEmpty()) {
            log.warn(">>>> [STOP] 멘션된 사용자가 없어 발송을 중단합니다.");
            return;
        }

        // 3. 요청자 ID 추출
        String requesterId = "Workflow_BOT";
        Pattern reqPattern = Pattern.compile("요청자\\s*\\n*<@([A-Z0-9]+)>");
        Matcher reqMatcher = reqPattern.matcher(fullText);
        if(reqMatcher.find()){
            requesterId = reqMatcher.group(1);
        }

        // 4. 일시(시작 시간) 추출
        String startTime = "시간정보 없음";
        Pattern timePattern = Pattern.compile("\\*날짜/시간\\*\\s*\\n*(.*)");
        Matcher timeMatcher = timePattern.matcher(fullText);
        if (timeMatcher.find()) {
            startTime = timeMatcher.group(1).split("\n")[0].trim();
        }

        // 5. 안건 내용 추출
        String content = "상세 내용을 확인해 주세요.";
        Pattern contentPattern = Pattern.compile("\\*안건\\*\\s*\\n*(.*)");
        Matcher contentMatcher = contentPattern.matcher(fullText);
        if (contentMatcher.find()) {
            content = contentMatcher.group(1).trim();
        }

        // 6. 제목 추출 (첫 줄이 이모지라면 두 번째 줄 등을 고려해야 할 수 있음)
        // 현재는 콘솔 결과에 따라 첫 줄을 제목으로 사용
        String title = fullText.split("\n")[0].replaceAll("[:*]", "").trim();

        // 7. DM 발송 (Handler 호출)
        meetingHandler.sendMeetingDMs(
                requesterId,
                userIds,
                title,
                content,
                startTime,
                client
        );

        log.info(">>>> [SUCCESS] DM 발송 요청 완료! 요청자: {}, 참석자 수: {}", requesterId, userIds.size());
    }
}
