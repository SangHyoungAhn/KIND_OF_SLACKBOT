package org.core.kindof.slackbot.config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;

import com.slack.api.bolt.jakarta_servlet.SlackAppServlet;
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
    public App initSlackApp(){
        AppConfig config = AppConfig.builder()
                .singleTeamBotToken(botToken)
                .signingSecret(signingSecret).build();

        return new App(config);
    }

    @Bean
    public ServletRegistrationBean<SlackAppServlet> slackAppServletServletRegistration(App app){
        return new ServletRegistrationBean<>(new SlackAppServlet(app),"/slack/events");
    }
}
