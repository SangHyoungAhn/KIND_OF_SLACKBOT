package org.core.kindof.slackbot.service;


import com.slack.api.bolt.App;
import com.slack.api.bolt.context.builtin.SlashCommandContext;
import com.slack.api.methods.SlackApiException;
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

    }

    private void registerChannelInfoCommand(){
        slackApp.command("/채널정보", (req, ctx) -> {
            String channelId = req.getPayload().getChannelId();
            String userName = req.getPayload().getUserName();
            // 아까 확인한 getNumOfMembers()를 사용하는 메서드 호출

            log.info(">>>> [COMMAND] {} 님이 /채널정보 모달을 실행했습니다.", userName);
            var blocks = getChannelInfoBlocks(ctx, channelId);

            return ctx.ack(res -> res
                    .responseType("in_channel") // 모두에게 보이게 설정
                    .blocks(blocks)
            );
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
