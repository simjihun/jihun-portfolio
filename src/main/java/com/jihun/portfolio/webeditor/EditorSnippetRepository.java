package com.jihun.portfolio.webeditor;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EditorSnippetRepository extends JpaRepository<EditorSnippet, Long> {
    List<EditorSnippet> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
