package com.jihun.portfolio.message.controller;

import com.jihun.portfolio.message.controller.dto.MessageSendRequest;
import com.jihun.portfolio.message.domain.Message;
import com.jihun.portfolio.message.domain.MessageStatus;
import com.jihun.portfolio.message.queue.MessageQueue;
import com.jihun.portfolio.message.repository.MessageRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 메시지 발송 REST API.
 *
 * 핵심 설계: API는 발송을 "직접" 하지 않는다.
 * DB에 저장하고 큐에 넣은 뒤 즉시 응답한다(비동기 처리).
 */
@RestController
@RequestMapping("/api/message")
public class MessageController {

    private final MessageRepository messageRepository;
    private final MessageQueue messageQueue;

    public MessageController(MessageRepository messageRepository, MessageQueue messageQueue) {
        this.messageRepository = messageRepository;
        this.messageQueue = messageQueue;
    }

    /** 메시지 발송 요청 접수 */
    @PostMapping("/messages")
    public ResponseEntity<Message> send(@Valid @RequestBody MessageSendRequest request) {
        Message message = new Message(request.receiver(), request.content());
        messageRepository.save(message);
        messageQueue.enqueue(message.getId());
        return ResponseEntity.ok(message);
    }

    /**
     * 발송 이력 조회 — 상태 필터 + 수신번호 검색 + 페이징
     * 예) /api/message/messages?status=FAILED&receiver=1234&page=0&size=15
     */
    @GetMapping("/messages")
    public Page<Message> list(@RequestParam(required = false) MessageStatus status,
                              @RequestParam(required = false) String receiver,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "15") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        boolean hasReceiver = receiver != null && !receiver.isBlank();

        if (status != null && hasReceiver) {
            return messageRepository.findByStatusAndReceiverContainingOrderByIdDesc(status, receiver, pageable);
        }
        if (status != null) {
            return messageRepository.findByStatusOrderByIdDesc(status, pageable);
        }
        if (hasReceiver) {
            return messageRepository.findByReceiverContainingOrderByIdDesc(receiver, pageable);
        }
        return messageRepository.findAllByOrderByIdDesc(pageable);
    }

    /** 실패 메시지 수동 재발송 */
    @PostMapping("/messages/{id}/resend")
    public ResponseEntity<?> resend(@PathVariable Long id) {
        Message message = messageRepository.findById(id).orElse(null);
        if (message == null) {
            return ResponseEntity.notFound().build();
        }
        if (message.getStatus() != MessageStatus.FAILED) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "실패(FAILED) 상태의 메시지만 재발송할 수 있습니다"));
        }
        message.resetForResend();
        messageRepository.save(message);
        messageQueue.enqueue(message.getId());
        return ResponseEntity.ok(message);
    }
}
