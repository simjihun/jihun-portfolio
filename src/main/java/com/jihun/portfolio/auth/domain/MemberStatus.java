package com.jihun.portfolio.auth.domain;

/**
 * 회원 승인 상태. 가입 직후에는 PENDING이며 이 상태로는 로그인할 수 없다.
 * 관리자가 APPROVED로 바꿔야 로그인이 가능해진다.
 */
public enum MemberStatus {
    PENDING,
    APPROVED,
    REJECTED
}
