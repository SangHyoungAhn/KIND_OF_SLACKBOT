package org.core.kindof.slackbot.config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;

import com.slack.api.bolt.jakarta_servlet.SlackAppServlet;
import com.slack.api.model.block.composition.OptionObject;
import org.core.kindof.slackbot.service.team.SlackTeamCommandService;
import org.core.kindof.slackbot.service.team.SlackTeamService;
import org.core.kindof.slackbot.service.team.SlackTeamWelcomeService;
import org.core.kindof.slackbot.service.welcome.SlackWelcomeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.multiStaticSelect;
import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*; // option, plainText 등 포함
import static com.slack.api.model.block.element.BlockElements.*;     // multiStaticSelect 등 포함
import static com.slack.api.model.view.Views.*;

@Configuration
public class SlackAppConfig {

    @Value("${SLACK_BOT_TOKEN}")
    private String botToken;

    @Value("${SLACK_SIGNING_SECRET}")
    private String signingSecret;

    @Bean
    public App initSlackApp(SlackTeamCommandService teamCommandService,
                            SlackTeamWelcomeService teamWelcomeService,
                            SlackTeamService teamService){
        AppConfig config = AppConfig.builder()
                .singleTeamBotToken(botToken)
                .signingSecret(signingSecret)
                .build();

        App app = new App(config);
        // 2. [명령어 연결] /팀합류 입력 시
        // 사용자가 커맨드를 치면 메뉴판(Blocks)을 ephemeral 메시지로 응답합니다.
        app.command("/팀합류", (req, ctx) -> {
            return teamCommandService.handleTeamJoin(req, ctx);
        });

        // 2. [액션] 버튼 클릭 시 -> 중앙 팝업(모달) 띄우기
        app.blockAction("open_team_modal", (req, ctx) -> {

            ctx.client().viewsOpen(r -> r
                    .triggerId(ctx.getTriggerId())
                    .view(view(v -> v
                            .type("modal")
                            .callbackId("team_join_modal") // 제출 시 식별값
                            .title(viewTitle(t -> t.type("plain_text").text("팀 합류하기")))
                            .submit(viewSubmit(s -> s.type("plain_text").text("🚀 합류하기")))
                            .close(viewClose(c -> c.type("plain_text").text("취소")))
                            .blocks(asBlocks(
                                    section(s -> s.text(markdownText("합류를 원하는 **본부와 팀**을 모두 선택해 주세요."))),
                                    input(i -> i
                                            .blockId("team_select_block") // 데이터 추출 시 키값
                                            .label(plainText("조직 선택"))
                                            .element(multiStaticSelect(sel -> sel
                                                    .actionId("welcome_team_select")
                                                    .placeholder(plainText("팀 이름을 검색해 보세요..."))
                                                    .options(teamWelcomeService.createTeamOptions()) // 기존 Enum 옵션 재사용
                                            ))
                                    )
                            ))
                    ))
            );
            return ctx.ack();
        });

        // 3. [모달 제출] 사용자가 모달에서 '합류하기' 버튼을 눌렀을 때
        app.viewSubmission("team_join_modal", (req, ctx) -> {
            // 실제 초대 로직 실행 (Service에서 처리)
            teamService.processModalSelection(req, ctx);
            return ctx.ack(); // 모달 닫기 신호
        });

        return app;
    }

    @Bean
    public ServletRegistrationBean<SlackAppServlet> slackAppServletServletRegistration(App app){
        return new ServletRegistrationBean<>(new SlackAppServlet(app),"/slack/events");
    }
}
