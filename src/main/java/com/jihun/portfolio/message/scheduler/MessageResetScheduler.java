package com.jihun.portfolio.message.scheduler;

import com.jihun.portfolio.message.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매일 자정, 그동안 쌓인 발송 이력을 초기화한다.
 * 무료 포트폴리오 데모 특성상 누구나 부하 테스트를 돌릴 수 있어,
 * 데이터가 무한정 쌓이는 것을 막기 위한 운영 정책이다.
 * 서버 시작 시점에는 실행하지 않고, 오직 매일 00:00(Asia/Seoul)에만 동작한다.
 */
@Component
public class MessageResetScheduler {

    private static final Logger log = LoggerFactory.getLogger(MessageResetScheduler.class);

    private final MessageRepository messageRepository;

    public MessageResetScheduler(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void resetDaily() {
        long count = messageRepository.count();
        messageRepository.deleteAllInBatch();
        log.info("[message] 자정 초기화 완료 - 삭제된 발송 이력 {}건", count);
    }
}
