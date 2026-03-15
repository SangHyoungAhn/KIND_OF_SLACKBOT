package org.core.kindof.slackbot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.core.kindof.slackbot.service.SlackService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/slack")
@RequiredArgsConstructor
public class SlackController {
    private final SlackService slackService;

    @GetMapping("/test")
    public void test(){
        slackService.sendMessage("테스트 메시지 전송", "test");
        log.info("Slack Test");
    }
}
