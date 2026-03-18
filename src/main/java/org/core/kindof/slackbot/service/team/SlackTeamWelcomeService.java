package org.core.kindof.slackbot.service.team;

import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.OptionObject;
import lombok.extern.slf4j.Slf4j;
import org.core.kindof.slackbot.constatnts.TeamType;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.button;
import static com.slack.api.model.block.element.BlockElements.multiStaticSelect;

@Slf4j
@Service
public class SlackTeamWelcomeService {
    /**
     * 팀 선택 UI 블록 생성 (재사용 가능하도록 설계)
     * @param userId 호출한 사용자 ID (메시지 개인화용)
     * @param title 메시지 상단 제목 (상황에 따라 다르게 줄 수 있음)
     */

    public List<LayoutBlock> createTeamJoinBlocks(String userId, String title){
        log.info("[UI_GEN] Creating team join blocks for user: {}", userId);

        return asBlocks(
                header(h -> h.text(plainText(title))),
                section(s -> s.text(markdownText("안녕하세요 <@" + userId + ">님!\n아래 버튼을 눌러 합류할 팀을 선택해 주세요."))),
                actions(a -> a.elements(List.of(
                        button(b -> b
                                .actionId("open_team_modal") // 모달을 여는 ID로 변경
                                .text(plainText("🚀 팀 채널 선택하기"))
                                .style("primary")
                        )
                )))
        );
    }

    public List<OptionObject> createTeamOptions() {
        return Arrays.stream(TeamType.values())
                .map(team -> option(plainText(team.getName()), team.name()))
                .collect(Collectors.toList());
    }

}
