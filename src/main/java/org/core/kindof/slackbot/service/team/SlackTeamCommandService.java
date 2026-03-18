package org.core.kindof.slackbot.service.team;


import com.slack.api.bolt.context.builtin.SlashCommandContext;
import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.model.block.LayoutBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.core.kindof.slackbot.service.welcome.SlackWelcomeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackTeamCommandService {

    private final SlackTeamWelcomeService slackTeamWelcomeService;

    /**
     * /팀합류 커맨드 핸들러
     * 사용자가 커맨드를 입력하면 팀 선택 UI(Block Kit)를 ephemeral 메시지로 응답합니다.
     */
    public Response handleTeamJoin(SlashCommandRequest req, SlashCommandContext ctx){
        String userId = req.getPayload().getUserId();
        log.info("[COMMAND] /팀합류 호출됨 - User: {}", userId);

        List<LayoutBlock> blocks = slackTeamWelcomeService.createTeamJoinBlocks(userId, "🎊 동아닷컴 팀 채널 합류하기 🎊");

        return ctx.ack(blocks);
    }

}
