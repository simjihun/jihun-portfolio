package com.jihun.portfolio.message.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 발송 요청 바디.
 */
public record MessageSendRequest(

        @NotBlank(message = "수신 번호는 필수입니다")
        @Size(max = 20)
        String receiver,

        @NotBlank(message = "메시지 내용은 필수입니다")
        @Size(max = 500)
        String content
) {
}
