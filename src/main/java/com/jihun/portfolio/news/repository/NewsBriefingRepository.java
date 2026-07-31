package com.jihun.portfolio.news.repository;

import com.jihun.portfolio.news.domain.NewsBriefing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsBriefingRepository extends JpaRepository<NewsBriefing, String> {
}
