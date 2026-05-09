package com.airag.common.handler;

import com.airag.common.exception.BusinessException;
import com.airag.common.result.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException exception,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        return buildResponse(request, response, 500, exception.getMessage(), exception, false);
    }

    @ExceptionHandler({
            BadCredentialsException.class,
            DisabledException.class
    })
    public Object handleCredentialException(Exception exception,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        return buildResponse(request, response, 401, exception.getMessage(), exception, false);
    }

    @ExceptionHandler(AuthenticationException.class)
    public Object handleAuthenticationException(AuthenticationException exception,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        return buildResponse(request, response, 401, "未登录或登录状态已失效", exception, false);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDeniedException(AccessDeniedException exception,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        return buildResponse(request, response, 403, "无权限访问", exception, false);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    public Object handleValidationException(Exception exception,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        return buildResponse(request, response, 400, "请求参数不合法", exception, false);
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception exception,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        return buildResponse(request, response, 500, "系统繁忙，请稍后重试", exception, true);
    }

    private Object buildResponse(HttpServletRequest request,
                                 HttpServletResponse response,
                                 int status,
                                 String message,
                                 Exception exception,
                                 boolean logAsError) {
        if (logAsError) {
            log.error("系统异常", exception);
        }

        if (isSseRequest(request)) {
            writeSseSafeError(response, status, message);
            return null;
        }

        return ApiResponse.of(status, message, null);
    }

    private boolean isSseRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.endsWith("/stream");
    }

    private void writeSseSafeError(HttpServletResponse response, int status, String message) {
        if (response == null || response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        try {
            response.getWriter().write(message);
            response.getWriter().flush();
        } catch (IOException ioException) {
            log.warn("Write SSE-safe error response failed", ioException);
        }
    }
}
