package com.ontheway.global.security.jwt;

import com.ontheway.dto.request.MemberLoginRequestDto;
import com.ontheway.dto.response.MemberLoginResponseDto;
import com.ontheway.global.exception.ErrorCode;
import com.ontheway.global.response.ApiResponse;
import com.ontheway.infra.cache.RefreshTokenStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;

public class JwtLoginFilter extends UsernamePasswordAuthenticationFilter {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final ObjectMapper objectMapper;

    public JwtLoginFilter(AuthenticationManager authenticationManager,
                        JwtTokenProvider jwtTokenProvider,
                        RefreshTokenStore refreshTokenStore,
                        ObjectMapper objectMapper) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.objectMapper = objectMapper;

        setFilterProcessesUrl("/user/login");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response) throws AuthenticationException {

        try {
            MemberLoginRequestDto dto =
                    objectMapper.readValue(
                            request.getInputStream(),
                            MemberLoginRequestDto.class
                    );

            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(
                            dto.getAccountId(),
                            dto.getPassword()
                    );

            return authenticationManager.authenticate(authenticationToken);

        } catch (IOException e) {
            // unsuccessfulAuthentication 으로 이동하기 위해
            throw new AuthenticationServiceException("로그인 요청을 처리할 수 없습니다.", e);
        }
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request,
                                    HttpServletResponse response,
                                            FilterChain chain,
                                    Authentication authResult ) throws IOException {

        String accountId = authResult.getName();
        String accessToken = jwtTokenProvider.createAccessToken(accountId);
        String refreshToken = jwtTokenProvider.createRefreshToken(accountId);

        refreshTokenStore.saveOnLogin(accountId, refreshToken);

        MemberLoginResponseDto result = MemberLoginResponseDto.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .createdAt(LocalDateTime.now())
                        .build();

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);

        response.getWriter().write(
                objectMapper.writeValueAsString(
                        ApiResponse.success(result)
                )
        );
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request,
                                    HttpServletResponse response,
                                            AuthenticationException failed ) throws IOException {

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        response.getWriter().write(
                objectMapper.writeValueAsString(
                        ApiResponse.fail(ErrorCode.LOGIN_FAILED.getStatus().value(),
                                ErrorCode.LOGIN_FAILED.getMessage()
                        )
                )
        );
    }
}
