package com.xclaw.dto;

import lombok.Data;

@Data
public class CreateXclawRequest {
    private String name;
    private String description;
    private String configJson; // optional custom config
    private String type; // openclaw, hermes
    private Long nodeId; // deploy to this node (null = local)
}
