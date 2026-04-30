package com.team10.uber.user.dto;

import java.util.List;

public class UserActivityFeedResponseDTO {

    private List<AuthEventDTO> content;
    private int page;
    private int size;
    private long totalElements;

    public List<AuthEventDTO> getContent() {
        return content;
    }

    public void setContent(List<AuthEventDTO> content) {
        this.content = content;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}
