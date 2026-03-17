package org.core.kindof.slackbot.constatnts;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TeamType {
    // === 본부 / 실 ===
    HQ_NEWS("NEWS_HQ", "[본부] 뉴스앤콘텐츠본부", "C0AM3JRBKUJ"),
    HQ_BIZ("BIZ_HQ", "[본부] 비즈니스본부", "C0AMXS761J4"),
    HQ_TECH("TECH_HQ", "[본부] 테크본부", "C0AMXRUNEL8"),
    DIV_PLANNING("PLAN_DIV", "[실] 경영기획실", "C0ALN4JLBE3"),

    // === 팀 ===
    TEAM_NEWS_TECH("NEWS_TECH", "└ 뉴스테크팀", "C0AM05F6TN1"),
    TEAM_DESIGN("DESIGN", "└ 디자인팀", "C0AMXTCK01E"),
    TEAM_DIGITAL_NEWS("DIGITAL_NEWS", "└ 디지털뉴스팀", "C0AMGGP3BND"),
    TEAM_MEDIA_TECH("MEDIA_TECH", "└ 미디어테크팀", "C0AM05D5XPX"),
    TEAM_VIDEO_X("VIDEO_X", "└ 비디오 X팀", "C0AMGGKHFK3"),
    TEAM_BIZ_MONEY("BIZ_MONEY", "└ 비즈앤머니팀", "C0AMGGGSU81"),
    TEAM_BIZ_TECH("BIZ_TECH", "└ 비즈테크팀", "C0AMGFB7T09"),
    TEAM_ENTER_INSIDE("ENTER_INSIDE", "└ 엔터인사이드팀", "C0AM06EGVPX"),
    TEAM_INNO_TECH("INNO_TECH", "└ 이노테크팀", "C0AMGFF3TAM"),
    TEAM_HR_ADMIN("HR_ADMIN", "└ 인사총무팀", "C0ALN3TUW8P"),
    TEAM_INFRA_TECH("INFRA_TECH", "└ 인프라테크팀", "C0AMGFH4PAM"),
    TEAM_FINANCE("FINANCE", "└ 재무회계팀", "C0ALX5RUJQK"),
    TEAM_CONTENT_HUB("CONTENT_HUB", "└ 콘텐츠허브팀", "C0AM76R6SH2"),
    TEAM_CROWD_ON("CROWD_ON", "└ 크라우드온팀", "C0ALN62A9E3"),
    TEAM_FACT_LINE("FACT_LINE", "└ 팩트라인팀", "C0AM06C68J1"),
    TEAM_C_BIZ("C_BIZ", "└ C-BIZ팀", "C0AM1GVNWJ2"),
    TEAM_M_BIZ("M_BIZ", "└ M-BIZ팀", "C0ALX6XVD7D"),
    TEAM_NEXT30("NEXT30", "└ NEXT30팀", "C0AM74D885A"),
    TEAM_S_BIZ("S_BIZ", "└ S-BIZ팀", "C0ALX70NFGT");

    private final String code;
    private final String name;
    private final String channelId;
}
