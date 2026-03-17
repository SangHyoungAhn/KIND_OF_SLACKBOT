package org.core.kindof.slackbot.service.welcome;

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
                                    // 1. 헤더: 강렬하고 환영하는 느낌
                                    header(h -> h.text(plainText("🎊 동아닷컴의 새로운 가족을 환영합니다! 🎊"))),
                                    // 2. 본문 1: 공간의 의미 설명
                                    section(s -> s.text(markdownText(
                                            "반가워요, <@" + userId + ">님! 👋\n" +
                                                    "이곳은 신입사원 여러분이 동아닷컴에 첫발을 내딛는 설레는 순간을 기록하고,\n" +
                                                    "기존 구성원들이 따뜻한 박수를 건네는 우리만의 소중한 공간 **'디지털 라운지'**입니다. ✨"
                                    ))),

                                    // 3. 본문 2: 부담을 덜어주는 부드러운 가이드
                                    section(s -> s.text(markdownText(
                                            "거창한 포부나 딱딱한 자기소개가 아니어도 괜찮아요. 😊\n" +
                                                    "오늘의 기분, 좋아하는 커피 취향, 혹은 아주 소소한 일상 이야기를 들려주세요.\n" +
                                                    "**우리가 나누는 이 가벼운 첫 대화가 바로 동아닷컴의 새로운 문화를 만드는 시작이 됩니다!** 🚀"
                                    ))),
                                    // 4. 구분선: 가독성을 위해 추가
                                    divider(),
                                    // 5. 푸터: 행동 유도 및 안내
                                    context(c -> c.elements(asContextElements(
                                            markdownText("💬 아래 입력창에 가벼운 안부 한마디를 남겨보세요! (이 메시지는 본인에게만 보입니다 🔒)")
                                    )))
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