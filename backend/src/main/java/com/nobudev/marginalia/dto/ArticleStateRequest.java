package com.nobudev.marginalia.dto;

public record ArticleStateRequest(
    Boolean read,
    Boolean saved
) {}
