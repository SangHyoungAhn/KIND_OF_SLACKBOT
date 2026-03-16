package org.core.kindof.slackbot.handler;

import com.slack.api.app_backend.views.payload.ViewSubmissionPayload;
import com.slack.api.bolt.context.builtin.ViewSubmissionContext;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.view.ViewState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.slack.api.methods.MethodsClient;

import java.util.List;
import java.util.Map;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.asContextElements;

@Service
@Slf4j
public class SlackMeetingHandler {

    public void handleMeetingSubmission(ViewSubmissionPayload payload, MethodsClient client){

        Map<String, Map<String, ViewState.Value>> values = payload.getView().getState().getValues();

        try{
            String title = values.get("title_block").get("title_input").getValue();
            String content = values.get("content_block").get("content_input").getValue();
            String time = values.get("time_block").get("time_select").getSelectedTime();
            List<String> selectedUserIds = values.get("users_block").get("users_select").getSelectedUsers();

            String senderId = payload.getUser().getId();
            sendMeetingDMs(senderId, selectedUserIds, title, content, time, client);

        }catch(Exception e){
            log.error(">>>> 데이터 추출 중 에러 발생: {}", e.getMessage());
        }

    }

    public void sendMeetingDMs(String senderId, List<String> userIds, String title, String content, String time, MethodsClient client){

        if (userIds == null || userIds.isEmpty()) {
            log.warn(">>>> 선택된 참석자가 없습니다.");
            return;
        }

        for(String targetUserId : userIds){
            try {
                client.chatPostMessage(r -> r
                                .channel(targetUserId)
                        .blocks(buildingMeetingCard(senderId, title, content, time))
                );
                log.info(">>>> DM 발송 성공: 대상={}", targetUserId);
            } catch (Exception e) {
                log.error("❌ DM 전송 실패 (대상: {}): {}", targetUserId, e.getMessage());
            }
        }
    }

    private List<LayoutBlock> buildingMeetingCard(String senderId, String title, String content, String time){
        return asBlocks(
                header(h -> h.text(plainText("📅 회의 알림이 도착했습니다!"))),
                section(s -> s.text(markdownText("*발신자:* <@" + senderId + ">"))),
                divider(),
                section(s -> s.fields(asSectionFields(
                        markdownText("*📌 회의 제목*\n" + title),
                        markdownText("*⏰ 시작 시간*\n" + time)
                ))),
                section(s -> s.text(markdownText("*📝 상세 안건 및 내용*\n" + content))),
                divider(),
                context(c -> c.elements(asContextElements(
                        markdownText(":bell: 이 메시지는 봇을 통해 발송되었습니다.")
                )))
        );
    }
}
