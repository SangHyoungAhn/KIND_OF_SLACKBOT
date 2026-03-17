package org.core.kindof.slackbot.service;

import com.slack.api.bolt.App;
import com.slack.api.model.event.MemberJoinedChannelEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.asContextElements;
@Service
@Slf4j
@RequiredArgsConstructor
public class SlackWelcomeService {

    private final App slackApp;

    // 상수로 정의한 ID를 아래 if문에서도 사용하도록 수정했습니다.
    private static final String DIGITAL_LOUNGE_CHANNEL_ID = "C0AJFQQ86Q3";

    @PostConstruct
    public void init() {
        registerWelcomeEvent();
    }

    private void registerWelcomeEvent() {
        slackApp.event(MemberJoinedChannelEvent.class, (payload, ctx) -> {
            var event = payload.getEvent();
            String channelId = event.getChannel();
            String userId = event.getUser();

            // 1. 우선 어떤 채널이든 입장 신호가 오면 무조건 로그를 찍습니다.
            log.info(">>>> [EVENT DETECTED] 입장 신호 도착! 채널: {}, 사용자: {}", channelId, userId);

            // 2. 상수로 비교 (문자열 직접 입력 대신 상수를 사용하세요!)
            if (DIGITAL_LOUNGE_CHANNEL_ID.equals(channelId)) {
                log.info(">>>> [MATCH SUCCESS] 디지털 라운지(C0AJFQQ86Q3) 일치 확인!");

                try {
                    var result = ctx.client().chatPostEphemeral(r -> r
                            .token(ctx.getBotToken())
                            .channel(channelId)
                            .user(userId)
                            .blocks(asBlocks(
                                    header(h -> h.text(plainText("🎉 동아닷컴의 새로운 가족을 환영합니다!"))),
                                    section(s -> s.text(markdownText("반가워요, <@" + userId + ">님!\n'디지털 라운지'에 오신 것을 환영해요."))),
                                    divider(),
                                    context(c -> c.elements(asContextElements(markdownText("이 메시지는 본인에게만 보입니다."))))
                            ))
                    );

                    if (result.isOk()) {
                        log.info(">>>> [SEND SUCCESS] 환영 인사가 정상 발송되었습니다.");
                    } else {
                        log.error(">>>> [SEND FAIL] 발송 실패: {}", result.getError());
                    }
                } catch (Exception e) {
                    log.error(">>>> [ERROR] API 호출 중 예외 발생", e);
                }
            } else {
                log.info(">>>> [MATCH SKIP] 지정된 채널이 아닙니다. (기대값: {}, 들어온값: {})", DIGITAL_LOUNGE_CHANNEL_ID, channelId);
            }
            return ctx.ack();
        });
    }
}