package com.TinasheGomo.JobPulse.service;

import com.TinasheGomo.JobPulse.dto.keyword.KeywordRequest;
import com.TinasheGomo.JobPulse.dto.keyword.KeywordResponse;
import com.TinasheGomo.JobPulse.entity.User;

import java.util.List;
import java.util.UUID;

public interface KeywordService {
    List<KeywordResponse> getKeywordsByUser(User user);
    KeywordResponse createKeyword(User user, KeywordRequest request);
    void deleteKeyword(User user, UUID keywordId);
}
