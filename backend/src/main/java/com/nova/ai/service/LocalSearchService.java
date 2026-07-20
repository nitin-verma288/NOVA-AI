package com.nova.ai.service;

import com.nova.ai.dto.LocalSearchResponse;
import com.nova.ai.entity.User;
import java.util.List;

public interface LocalSearchService {
    List<LocalSearchResponse> searchLocal(User user, String query, String rootPath);
}
