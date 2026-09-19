package com.ontheway.global.security.jwt;

import com.ontheway.global.exception.ErrorCode;
import com.ontheway.global.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        String exceptionType = (String) request.getAttribute("exception");
        ErrorCode errorCode = "TOKEN_EXPIRED".equals(exceptionType)
                ? ErrorCode.TOKEN_EXPIRED
                : ErrorCode.INVALID_TOKEN;

        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                new ObjectMapper().writeValueAsString(
                        ApiResponse.fail(errorCode.getStatus().value(), errorCode.getMessage())
                )
        );
    }

}
