package com.jihun.portfolio.auth.config;

import com.jihun.portfolio.auth.service.MemberDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * 비공개 회원 영역(로그인/마이페이지/관리자) 인증·인가 설정.
 * 공개 포트폴리오(뉴스·주식·게임 등 기존 기능)는 이 설정과 무관하게 계속 전체 공개다 —
 * /login, /mypage, /admin 등 이번에 추가한 경로만 인증 대상이 된다.
 *
 * 새로 추가되는 개인용 실험 기능은 전부 /admin/<기능>/<세부기능> 아래에 붙인다(관리자 전용).
 * "/admin/**" 매칭이 이미 있어서 새 기능 페이지를 추가할 때 이 설정을 따로 건드릴 필요는 없다 —
 * WebConfig에 라우팅만 추가하면 자동으로 관리자 인증이 적용된다.
 *
 * [알려진 단순화 사항] CSRF 보호를 전체 비활성화했다. 이 프로젝트는 템플릿 엔진 없이 정적 HTML +
 * fetch로 API를 호출하는 구조라, 정석대로 하려면 매 요청에 CSRF 토큰을 헤더로 실어야 하는데
 * 지금 단계에서는 기능 구현을 우선했다. 결제·금전 처리가 없는 개인 연습용 비공개 영역이라 리스크는
 * 낮지만, 나중에 강화하려면 /api/csrf-token 같은 엔드포인트로 토큰을 받아 프론트에서 헤더에 실어
 * 보내는 방식으로 다시 켤 수 있다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.encryption-key:local-dev-only-change-me}")
    private String rememberMeKey;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(MemberDetailsService memberDetailsService,
                                                              PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(memberDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            DaoAuthenticationProvider authenticationProvider,
                                            MemberDetailsService memberDetailsService) throws Exception {
        http
            .authenticationProvider(authenticationProvider)
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin", "/admin/**", "/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/mypage", "/mypage/**", "/api/auth/password/change", "/api/auth/me").authenticated()
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .defaultSuccessUrl("/mypage", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            // 로그인 상태를 24시간 유지(브라우저를 닫았다 열어도 유지). 개인 전용 사이트라
            // 체크박스 없이 항상 remember-me 쿠키를 발급한다(alwaysRemember).
            .rememberMe(rm -> rm
                .key(rememberMeKey)
                .tokenValiditySeconds(24 * 3600)
                .alwaysRemember(true)
                .userDetailsService(memberDetailsService)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID", "remember-me")
                .permitAll()
            )
            .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/api/**")
            ));
        return http.build();
    }
}
