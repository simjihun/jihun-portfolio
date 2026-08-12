package com.jihun.portfolio.auth.domain;

/** 회원 권한. USER는 승인 후 비공개 영역(마이페이지) 이용, ADMIN은 회원 승인·관리 권한을 가진다. */
public enum MemberRole {
    USER,
    ADMIN
}
