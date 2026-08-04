package com.jihun.portfolio.game.janggi;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 장기 대국 진행 서비스 (사람 vs AI).
 * 오목과 동일하게 재접속이 필요 없는 단일 세션이라 DB 대신 메모리에만 보관한다.
 *
 * 사람 수와 AI 수는 별도 API 호출로 분리되어 있다(move → ai-move). 한 요청에서 둘 다 처리해버리면
 * 사람이 장군을 부른 직후의 "장군 상태"가 응답에 한 번도 노출되지 않고 AI의 응수로 바로 덮여버려서
 * 프론트에서 장군 표시를 볼 틈이 없다 — 그래서 프론트가 사람 수 응답을 먼저 렌더링한 뒤, 잠깐 텀을
 * 두고 별도로 ai-move를 호출하는 구조로 만들었다.
 */
@Service
public class JanggiService {

    private final Map<String, JanggiGame> games = new ConcurrentHashMap<>();
    private final JanggiAiService aiService;

    public JanggiService(JanggiAiService aiService) {
        this.aiService = aiService;
    }

    /** 새 대국 생성. 장기는 초(O)가 항상 선수이므로, AI가 초라면 생성 즉시 첫 수를 둔다(대국 시작 시점이라 장군 표시 문제 없음). */
    public JanggiGame create(String humanColor, String difficulty, Integer timeLimitSeconds,
                              String humanFormation, String aiFormation) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String aiColor = "H".equals(humanColor) ? "O" : "H";
        String formationH = "H".equals(humanColor) ? humanFormation : aiFormation;
        String formationO = "H".equals(humanColor) ? aiFormation : humanFormation;
        JanggiGame g = new JanggiGame(id, humanColor, aiColor, difficulty, timeLimitSeconds, formationH, formationO);
        games.put(id, g);
        if (g.getCurrentPlayer().equals(g.getAiColor())) {
            applyAiMove(g);
        }
        g.touchTurn();
        return g;
    }

    public JanggiGame get(String id) {
        JanggiGame g = games.get(id);
        if (g == null) throw new IllegalArgumentException("대국을 찾을 수 없습니다");
        return g;
    }

    /** 선택한 기물이 이번 턴에 이동 가능한 칸 목록. 프론트가 클릭 2단계 흐름(선택→이동)에 사용한다. */
    public List<JanggiRules.Move> legalMovesFrom(String id, int x, int y) {
        JanggiGame g = get(id);
        if (!"PLAYING".equals(g.getStatus())) return List.of();
        List<JanggiRules.Move> result = new ArrayList<>();
        for (JanggiRules.Move m : JanggiRules.legalMoves(g.getBoard(), g.getCurrentPlayer())) {
            if (m.fromX() == x && m.fromY() == y) result.add(m);
        }
        return result;
    }

    /** 사람의 수만 적용한다. AI 응수는 프론트가 잠깐 텀을 두고 별도로 /ai-move를 호출해 처리한다. */
    public JanggiGame move(String id, int fromX, int fromY, int toX, int toY) {
        JanggiGame g = get(id);
        if (!"PLAYING".equals(g.getStatus())) return g;
        if (!g.getCurrentPlayer().equals(g.getHumanColor())) {
            throw new IllegalArgumentException("AI 차례입니다");
        }
        applyMove(g, fromX, fromY, toX, toY, g.getHumanColor());
        return g;
    }

    /** AI 차례일 때만 AI의 수를 둔다. 사람 차례거나 대국이 끝났으면 아무 것도 하지 않고 현재 상태를 반환한다. */
    public JanggiGame aiMove(String id) {
        JanggiGame g = get(id);
        if ("PLAYING".equals(g.getStatus()) && g.getCurrentPlayer().equals(g.getAiColor())) {
            applyAiMove(g);
        }
        return g;
    }

    /** 제한시간이 지난 상태에서 호출되면, 사람 차례를 쉬운 난이도 로직으로 자동 진행한다(AI 응수는 별도 호출). */
    public JanggiGame timeout(String id) {
        JanggiGame g = get(id);
        if (!"PLAYING".equals(g.getStatus())) return g;
        if (!g.getCurrentPlayer().equals(g.getHumanColor())) return g;
        if (g.getTimeLimitSeconds() == null) return g;

        long elapsedSec = (System.currentTimeMillis() - g.getTurnStartedAt()) / 1000;
        if (elapsedSec < g.getTimeLimitSeconds()) return g;

        JanggiRules.Move mv = aiService.pickMove(g.getBoard(), g.getHumanColor(), g.getAiColor(), "EASY");
        if (mv != null) {
            applyMove(g, mv.fromX(), mv.fromY(), mv.toX(), mv.toY(), g.getHumanColor());
        }
        return g;
    }

    private void applyAiMove(JanggiGame g) {
        JanggiRules.Move mv = aiService.pickMove(g.getBoard(), g.getAiColor(), g.getHumanColor(), g.getDifficulty());
        if (mv == null) {
            g.setStatus("H".equals(g.getHumanColor()) ? "H_WIN" : "O_WIN"); // AI가 둘 수 없음 = 사람 승리
            return;
        }
        applyMove(g, mv.fromX(), mv.fromY(), mv.toX(), mv.toY(), g.getAiColor());
    }

    private void applyMove(JanggiGame g, int fromX, int fromY, int toX, int toY, String player) {
        String[][] board = g.getBoard();
        if (!JanggiRules.inBounds(fromX, fromY) || !JanggiRules.inBounds(toX, toY)) {
            throw new IllegalArgumentException("범위를 벗어났습니다");
        }
        String piece = board[fromY][fromX];
        if (piece == null || !piece.startsWith(player)) {
            throw new IllegalArgumentException("자신의 기물이 아닙니다");
        }
        boolean legal = false;
        for (JanggiRules.Move m : JanggiRules.legalMoves(board, player)) {
            if (m.fromX() == fromX && m.fromY() == fromY && m.toX() == toX && m.toY() == toY) { legal = true; break; }
        }
        if (!legal) throw new IllegalArgumentException("허용되지 않는 수입니다");

        board[toY][toX] = board[fromY][fromX];
        board[fromY][fromX] = null;
        g.setMoveCount(g.getMoveCount() + 1);
        g.setLastMove(fromX, fromY, toX, toY);

        String next = "H".equals(player) ? "O" : "H";
        g.setCheckColor(JanggiRules.isInCheck(board, next) ? next : null); // 다음 둘 사람이 장군에 걸렸는지 기록

        List<JanggiRules.Move> nextMoves = JanggiRules.legalMoves(board, next);
        if (nextMoves.isEmpty()) {
            g.setStatus("H".equals(player) ? "H_WIN" : "O_WIN");
        } else {
            g.setCurrentPlayer(next);
            g.touchTurn();
        }
    }
}
