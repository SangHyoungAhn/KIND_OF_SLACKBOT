package org.core.kindof.slackbot.service.team;


import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.context.builtin.ViewSubmissionContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload.Action.SelectedOption;
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.model.view.ViewState;
import com.slack.api.model.view.ViewState.*;
import lombok.extern.slf4j.Slf4j;
import org.core.kindof.slackbot.constatnts.TeamType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SlackTeamService {

    /**
     * 사용자가 선택한 팀들에 실제로 초대하는 핵심 로직
     */

    public void processTeamJoin(BlockActionRequest req, ActionContext ctx) throws IOException {

        //1. 사용자가 메뉴에서 선택한 Value(Enum Name)' 리스트를 추출
        List<String> selectedEnumNames = req.getPayload().getActions().stream()
                .filter(action->"welcome_team_select".equals(action.getActionId()))
                .flatMap(action -> action.getSelectedOptions().stream())
                .map(SelectedOption::getValue)
                .collect(Collectors.toList());

        String userId = req.getPayload().getUser().getId();

        //2. 아무것도 선택 안 하고 버트만 눌렀을 때의 방어로직
        if(selectedEnumNames.isEmpty()){
            ctx.respond("⚠️ 합류할 팀을 하나 이상 선택해 주세요!");
            return;
        }

        StringBuilder resultMessage = new StringBuilder("✅ **팀 합류 처리가 완료되었습니다!**\n");

        //3. 선택한 각 팀 순회하며 초대실행
        for(String enumName : selectedEnumNames){
            TeamType team = TeamType.valueOf(enumName);
            try{

                //채널에 사용자 초대
                ctx.client().conversationsInvite(i->i
                        .token(ctx.getBotToken())
                        .channel(team.getChannelId())
                        .users(List.of(userId)));

                resultMessage.append("- ").append(team.getName()).append(" (합류 성공!)\n");
                log.info("[TEAM_SUCCESS] User: {}, Team: {}", userId, team.getName());

            }catch(Exception e){
                // {reason} 대신 진짜 에러 원인을 로그에 찍습니다.
                log.error("❌ 팀 합류 실패 상세 원인: {}", e.getMessage());
                resultMessage.append("- ").append(team.getName()).append(" : 실패 (원인: ").append(e.getMessage()).append(")\n");}
        }

        // 4. 최종 결과를 사용자에게 Ephemeral 메시지로 응답
        ctx.respond(resultMessage.toString() + "\n해당 채널들을 확인해 보세요! 🚀");
    }

    public Response processModalSelection(ViewSubmissionRequest req, ViewSubmissionContext ctx) {
        // 1. 모달의 'state'에서 선택된 값들을 추출 (경로: View -> State -> Values -> BlockId -> ActionId)
        List<ViewState.SelectedOption> selectedOptions = req.getPayload().getView().getState().getValues()
                .get("team_select_block")     // SlackAppConfig에서 설정한 blockId
                .get("welcome_team_select")   // SlackAppConfig에서 설정한 actionId
                .getSelectedOptions();

        List<String> selectedEnumNames = selectedOptions.stream()
                .map(ViewState.SelectedOption::getValue)
                .collect(Collectors.toList());

        String userId = req.getPayload().getUser().getId();
        StringBuilder resultMessage = new StringBuilder("<@" + userId + ">님의 합류한 팀입니다:\n");

        // 2. 초대 로직 실행 (기존과 동일)
        for (String enumName : selectedEnumNames) {
            try {
                TeamType team = TeamType.valueOf(enumName);
                String channelId = team.getChannelId();

                // [추가] 봇이 먼저 해당 채널에 들어갑니다 (이미 있으면 무시됨)
                ctx.client().conversationsJoin(j -> j
                        .token(ctx.getBotToken())
                        .channel(channelId)
                );

                // 그 다음 사용자를 초대합니다
                ctx.client().conversationsInvite(i -> i
                        .token(ctx.getBotToken())
                        .channel(channelId)
                        .users(List.of(userId))
                );

                resultMessage.append("✅ ").append(team.getName()).append(" : 합류 성공!\n");
            } catch (Exception e) {
                log.error("❌ {} 합류 실패: {}", enumName, e.getMessage());
                // 에러 원인을 더 구체적으로 적어주면 디버깅이 편합니다.
                resultMessage.append("⚠️ ").append(enumName).append(" : 실패 (원인: ").append(e.getMessage()).append(")\n");
            }
        }

        // 3. 결과 전송 (모달은 닫히고, 메시지는 DM이나 채널로 보냅니다)
        try {
            ctx.client().chatPostEphemeral(r -> r
                    .token(ctx.getBotToken())
                    .channel(userId) // 사용자 개인에게만 전송
                    .user(userId)
                    .text(resultMessage.toString())
            );
        } catch (Exception e) {
            log.error("결과 메시지 전송 실패", e);
        }

        return ctx.ack(); // 모달을 닫으라는 신호
    }
}
