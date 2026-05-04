package com.xclaw.dto;

import lombok.Data;

@Data
public class CreateXclawRequest {
    private String name;
    private String description;
    private String configJson; // optional custom config
}
