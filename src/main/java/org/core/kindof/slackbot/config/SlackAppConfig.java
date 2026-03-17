package org.core.kindof.slackbot.config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;

import com.slack.api.bolt.jakarta_servlet.SlackAppServlet;
import org.core.kindof.slackbot.service.team.SlackTeamCommandService;
import org.core.kindof.slackbot.service.welcome.SlackWelcomeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlackAppConfig {

    @Value("${SLACK_BOT_TOKEN}")
    private String botToken;

    @Value("${SLACK_SIGNING_SECRET}")
    private String signingSecret;

    @Bean
    public App initSlackApp(SlackTeamCommandService teamCommandService,
                            SlackWelcomeService welcomeService){
        AppConfig config = AppConfig.builder()
                .singleTeamBotToken(botToken)
                .signingSecret(signingSecret)
                .build();

        App app = new App(config);
        //"팀합류" 커맨드 등록
        app.command("/팀합류", teamCommandService::handleTeamJoin);

        app.blockAction("welcome_confirm_join", (req, ctx) ->{
            welcomeService.processTeamJoin(req,ctx);
            return ctx.ack();
        });

        return app;
    }

    @Bean
    public ServletRegistrationBean<SlackAppServlet> slackAppServletServletRegistration(App app){
        return new ServletRegistrationBean<>(new SlackAppServlet(app),"/slack/events");
    }
}
