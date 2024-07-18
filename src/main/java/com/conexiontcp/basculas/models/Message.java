package com.conexiontcp.basculas.models;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Message {
    private MessageType type;
    private String content;
    private String sender;
    private String host;
    private int puerto;
}
