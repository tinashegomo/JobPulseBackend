package com.TinasheGomo.JobPulse.service.impl;

import com.TinasheGomo.JobPulse.dto.keyword.KeywordRequest;
import com.TinasheGomo.JobPulse.dto.keyword.KeywordResponse;
import com.TinasheGomo.JobPulse.entity.Keyword;
import com.TinasheGomo.JobPulse.entity.User;
import com.TinasheGomo.JobPulse.exception.exceptions.NotFoundException;
import com.TinasheGomo.JobPulse.mapper.KeywordMapper;
import com.TinasheGomo.JobPulse.repository.KeywordRepository;
import com.TinasheGomo.JobPulse.service.KeywordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KeywordServiceImpl implements KeywordService {

    private final KeywordRepository keywordRepository;
    private final KeywordMapper keywordMapper;

    @Override
    public List<KeywordResponse> getKeywordsByUser(User user) {
        return keywordRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(keywordMapper::toResponse)
                .toList();
    }

    @Override
    public KeywordResponse createKeyword(User user, KeywordRequest request) {
        Keyword keyword = Keyword.builder()
                .user(user)
                .keyword(request.getKeyword().trim())
                .build();
        keywordRepository.save(keyword);
        return keywordMapper.toResponse(keyword);
    }

    @Override
    public void deleteKeyword(User user, UUID keywordId) {
        Keyword keyword = keywordRepository.findById(keywordId)
                .filter(k -> k.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new NotFoundException("Keyword not found"));
        keywordRepository.delete(keyword);
    }
}
