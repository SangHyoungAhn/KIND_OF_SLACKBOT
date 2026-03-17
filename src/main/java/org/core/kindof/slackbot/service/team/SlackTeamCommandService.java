package org.core.kindof.slackbot.service.team;


import com.slack.api.bolt.context.builtin.SlashCommandContext;
import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.model.block.LayoutBlock;
import lombok.RequiredArgsConstructor;
import org.core.kindof.slackbot.service.welcome.SlackWelcomeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SlackTeamCommandService {

    private final SlackWelcomeService welcomeService;

    /**
     *  /팀합류 커맨드 전담처리
     */
    public Response handleTeamJoin(SlashCommandRequest req, SlashCommandContext ctx){
        String userId = req.getPayload().getUserId();

        List<LayoutBlock> blocks = welcomeService.createWelcomeBlocks(userId);

        return ctx.ack(blocks);
    }

}
