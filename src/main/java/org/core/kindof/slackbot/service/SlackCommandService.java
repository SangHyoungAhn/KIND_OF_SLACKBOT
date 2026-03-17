package org.core.kindof.slackbot.service;


import com.slack.api.bolt.App;
import com.slack.api.bolt.context.builtin.SlashCommandContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse;
import com.slack.api.methods.response.conversations.ConversationsInfoResponse;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.view.View;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.core.kindof.slackbot.handler.SlackMeetingHandler;
import org.eclipse.jetty.util.IO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;
import static com.slack.api.model.view.Views.*;

@Service
@Slf4j
public class SlackCommandService {

    private final App slackApp;
    private final SlackMeetingHandler meetingHandler;

    public SlackCommandService(App slackApp, SlackMeetingHandler meetingHandler ) {
        this.slackApp = slackApp;
        this.meetingHandler = meetingHandler;
        this.init();
    }

    @Value("${SLACK_ADMIN_CHANNEL_ID}")
    private String adminChannelId;

    /**
     * 서버가 시작될 때 슬래시 커맨드를 등록합니다.
     */
    @PostConstruct
    public void init() {

        registerChannelInfoCommand();

        registerSubmissionHandler();
        registerFixRequestCommand();

        registerMeetingAlarmCommand();
        registerMeetingSubmissionHandler();
        
        registerCleanupCommand();

    }

    private void registerCleanupCommand() {
        slackApp.command("/청소해줘", (req, ctx) -> {
            String channelId = req.getPayload().getChannelId();
            log.info(">>>> [COMMAND] /청소해줘 실행 (채널: {})", channelId);
            try {
                // 최근 메시지 50개 조회
                ConversationsHistoryResponse history = ctx.client().conversationsHistory(r -> r
                        .token(ctx.getBotToken())
                        .channel(channelId)
                        .limit(50)
                );
                if (history.isOk()) {
                    int deleteCount = 0;
                    for (var msg : history.getMessages()) {
                        // 1. 봇 ID가 있거나
                        // 2. 앱 ID가 있거나
                        // 3. 메시지 서브타입이 봇이거나
                        // 4. 메시지 안에 'blocks'가 들어있는 경우 (보통 상세 정보는 블록으로 구성됨)
                        boolean isBotMessage = msg.getBotId() != null
                                || msg.getAppId() != null
                                || "bot_message".equals(msg.getSubtype())
                                || (msg.getBlocks() != null && !msg.getBlocks().isEmpty());

                        if (isBotMessage) {
                            var result = ctx.client().chatDelete(d -> d
                                    .token(ctx.getBotToken())
                                    .channel(channelId)
                                    .ts(msg.getTs())
                            );

                            if(result.isOk()) {
                                deleteCount++;
                                log.info(">>>> [SUCCESS] 삭제 성공: {}", msg.getTs());
                            } else {
                                log.error(">>>> [FAIL] 삭제 실패: {}, 사유: {}", msg.getTs(), result.getError());
                            }
                        }
                    }
                    // 결과 보고 (본인에게만 보임)
                    return ctx.ack("🧹 봇 메시지 " + deleteCount + "개를 깔끔하게 청소했습니다!");
                } else {
                    // 여기서 에러 로그를 찍어야 왜 안되는지 알 수 있어요!
                    log.error(">>>> 히스토리 조회 실패: {}", history.getError());
                    return ctx.ack("❌ 청소 실패 원인: " + history.getError());
                }
            } catch (Exception e) {
                log.error(">>>> [CLEANUP ERROR] ", e);
                return ctx.ack("❌ 시스템 에러가 발생했습니다.");
            }
        });
    }

    private void registerChannelInfoCommand(){
        slackApp.command("/채널정보", (req, ctx) -> {
            String channelId = req.getPayload().getChannelId();
            String userName = req.getPayload().getUserName();

            log.info(">>>> [COMMAND] {} 님이 /채널정보를 실행했습니다.", userName);
            var blocks = getChannelInfoBlocks(ctx, channelId);

            // 1. 우선 슬랙 서버에 "명령어 확인했어"라고 빈 응답을 보냅니다. (필수)
            ctx.ack();

            // 2. 비동기로 '진짜 메시지'를 채널에 쏩니다.
            try {
                ctx.client().chatPostMessage(r -> r
                        .token(ctx.getBotToken())
                        .channel(channelId)
                        .blocks(blocks)
                        .text("🔍 채널 상세 정보가 도착했습니다.") // 알림용 텍스트
                );
            } catch (Exception e) {
                log.error(">>>> [ERROR] 채널 정보 전송 실패: ", e);
            }

            return ctx.ack(); // 이미 위에서 호출했지만 구조상 반환
        });
    }

    private void registerFixRequestCommand(){
        slackApp.command("/부탁해",(req,ctx) ->{
            String triggerId = req.getPayload().getTriggerId();
            String userName = req.getPayload().getUserName();

            ctx.client().viewsOpen(r-> r
                    .triggerId(triggerId)
                    .view(buildFixModal())
            );

            log.info(">>>> [COMMAND] {} 님이 /부탁해 모달을 실행했습니다.", userName);
            return ctx.ack();
        });
    }

    private void registerMeetingAlarmCommand(){
        slackApp.command("/회의알림발송", (req,ctx)->{
            String triggerId = req.getPayload().getTriggerId();
            String userName = req.getPayload().getUserName();

            ctx.client().viewsOpen(r-> r
                    .triggerId(triggerId)
                    .view(buildingMeetingNotificationModal(userName))
            );

            log.info(">>>> [COMMAND] {} 님이 /회의알림발송 모달을 실행했습니다.", userName);
            return ctx.ack();
        });
    }

    private void registerSubmissionHandler(){
        slackApp.viewSubmission("submit-fix-modal", (req, ctx) -> {
            log.info(">>>> [SUCCESS] 제출 신호 수신 성공!");

            // 1. 데이터 추출 (보내주신 Suggestion 구조 적용)
            var stateValues = req.getPayload().getView().getState().getValues();
            String content = stateValues.get("content_block").get("content_input").getValue();

            String userId = req.getPayload().getUser().getId();
            String userName = req.getPayload().getUser().getName();

            // 2. 비즈니스 로직 실행 (전송 등)
            try {
                sendReportToAdmin(content, userId, userName);
                sendThanktoDM(userId);
                log.info(">>>> 전송 완료: 작성자={}, 내용={}", userName, content);
            } catch (Exception e) {
                log.error(">>>> 처리 중 에러 발생: {}", e.getMessage());
            }

            // 3. 슬랙에 "확인 완료" 응답 (이게 있어야 모달이 닫히고 뱅글뱅글 도는 게 멈춥니다)
            return ctx.ack();
        });
    }

    private void registerMeetingSubmissionHandler(){
        slackApp.viewSubmission("submit-meeting-notification", (req, ctx)->{
            log.info(">>>> [SUCCESS] 회의 알림 제출 신호 수신!");

            meetingHandler.handleMeetingSubmission(req.getPayload(), ctx.client());

            return ctx.ack();
        });
    }

    private void sendReportToAdmin(String content, String userId, String userName) throws IOException, SlackApiException {

        slackApp.client().chatPostMessage(r-> r
                .channel(adminChannelId)
                .blocks(asBlocks(
                        header(h -> h.text(plainText("🚨 새로운 제안 도착!"))),
                        section(s -> s.text(markdownText(":writing_hand: *작성자:* <@" + userId + "> (" + userName + ")"))),
                        section(s -> s.text(markdownText(":speech_balloon: *내용:*\n" + content))),
                        divider(),
                        context(c -> c.elements(asContextElements(
                                markdownText("제안을 검토해주세요 :sparkles:")
                        )))
                ))
        );
    }

    private void sendThanktoDM(String userId) throws IOException, SlackApiException {
        slackApp.client().chatPostMessage(r -> r
                .channel(userId) // 사용자 ID를 채널로 지정하면 DM이 전송됩니다.
                .text("소중한 의견 감사합니다! 동료님의 제안이 더 좋은 회사를 만듭니다. 😊")
        );
    }

    private View buildFixModal(){
        return view(v -> v
                .type("modal")
                .callbackId("submit-fix-modal")
                .title(viewTitle(t -> t.type("plain_text").text("🛠️ 부탁해요")))
                .submit(viewSubmit(s -> s.type("plain_text").text("의견 보내기 \uD83D\uDE80")))
                .close(viewClose(c -> c.type("plain_text").text("취소")))
                .blocks(asBlocks(
                        // 1. 환영 인사 및 상단 이미지/아이콘 느낌
                        section(s -> s.text(markdownText("안녕하세요,  동료님! :wave:"))),
                        // 2. 메인 안내 문구
                        section(s -> s.text(markdownText(
                                ":sparkles: *여러분의 제안이 우리 회사를 바꿉니다.*\n"
                        ))),
                        divider(), // 첫 번째 구분선
                        input(i -> i
                                .blockId("content_block")
                                .element(plainTextInput(p -> p.actionId("content_input").multiline(true)))
                                .label(plainText("✍\uFE0F 개선 제안 및 부탁할 점"))
                        )
                ))
        );
    }

    private View buildingMeetingNotificationModal(String userName){
        return view(v-> v
                .type("modal")
                .callbackId("submit-meeting-notification")
                .title(viewTitle(t -> t.type("plain_text").text("📅 회의 알림 발송")))
                .submit(viewSubmit(s -> s.type("plain_text").text("발송하기 🚀")))
                .close(viewClose(c -> c.type("plain_text").text("취소")))
                .blocks(asBlocks(
                        // 회의 제목
                        input(i -> i.blockId("title_block").element(plainTextInput(p -> p.actionId("title_input").placeholder(plainText("예: 주간회의")))).label(plainText("📌 회의 제목"))),
                        // 회의 안건
                        input(i -> i.blockId("content_block").element(plainTextInput(p -> p.actionId("content_input").multiline(true).placeholder(plainText("주요 논의 사항을 적어주세요.")) )).label(plainText("📝 회의 안건 및 상세 내용"))),
                        // 참석자 선택 (멀티 유저 셀렉트)
                        input(i -> i.blockId("users_block").element(multiUsersSelect(u -> u.actionId("users_select").placeholder(plainText("참석할 동료들을 선택하세요")))).label(plainText("👥 참석자 선택"))),
                        divider(),
                        // 시간 설정
                        section(s -> s.blockId("time_block").text(markdownText("⏰ *회의 시작 시간 설정*")).accessory(timePicker(t -> t.actionId("time_select").initialTime("14:00")))),
                        context(c -> c.elements(asContextElements(markdownText(":bell: 선택된 분들께 개별 DM으로 알림이 전송됩니다."))))
                ))
        );
    }

    public List<LayoutBlock> getChannelInfoBlocks (SlashCommandContext ctx, String channelId) throws IOException, SlackApiException {

        //Call Slack Api Conversation
        ConversationsInfoResponse response = ctx.client().conversationsInfo(
                r -> r.channel(channelId).includeNumMembers(true));

        if(!response.isOk()){
            return List.of(section(s -> s.text(markdownText("❌ 채널 정보를 가져오지 못했습니다: " + response.getError()))));
        }

        var channel = response.getChannel();

        Integer memberCount = channel.getNumOfMembers();
        if(memberCount == null){
            memberCount = 0;
        }

        return asBlocks(
                // 1. 헤더: 돋보기 이모지로 리포트 느낌 강조
                header(h -> h.text(plainText(":mag: 채널 상세 정보"))),

                // 2. 메인 정보: 채널명 (슬랙 이모지 사용)
                section(s -> s.text(markdownText(":slack: *채널명:* `#" + channel.getName() + "`"))),

                divider(),

                // 3. 그리드 정보: 이모지를 활용해 가독성 높임
                section(s -> s.fields(asSectionFields(
                        markdownText(":busts_in_silhouette: *멤버 수*\n" + channel.getNumOfMembers() + "명"),
                        markdownText(":lock: *공개 여부*\n" + (channel.isPrivate() ? "비공개" : "공개")),
                        markdownText(":id: *채널 ID*\n`" + channel.getId() + "`"),
                        markdownText(":calendar: *생성일*\n<!date^" + channel.getCreated() + "^{date_short}|알 수 없음>")
                ))),

                // 4. 채널 주제 섹션
                section(s -> s.text(markdownText(":speech_balloon: *채널 주제*\n" +
                        (channel.getTopic().getValue().isEmpty() ? "_설정된 주제가 없습니다._" : channel.getTopic().getValue())))),

                divider()

        );
    }
}
