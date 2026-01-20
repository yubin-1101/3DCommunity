package com.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomMessageRequest {
    private Long roomId;
    private Long userId; // 추가: 명시적인 사용자 ID
    private String content;
}
