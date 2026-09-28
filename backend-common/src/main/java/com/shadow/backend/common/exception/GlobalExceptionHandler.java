package com.shadow.backend.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.shadow.backend.common.response.Result;
import com.shadow.backend.common.response.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        log.warn("业务异常: code={}", ex.getCode());
        return ResponseEntity.ok(Result.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("数据完整性冲突: type={}", ex.getClass().getSimpleName());
        return ResponseEntity.ok(Result.fail(ResultCode.CONFLICT.getCode(), ResultCode.CONFLICT.getMsg()));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLoginException(NotLoginException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg()));
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<Result<Void>> handleSaTokenPermissionException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMsg()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<Result<Void>> handleValidationException(Exception ex) {
        String message = resolveValidationMessage(ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), message));
    }

    @ExceptionHandler({
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            NoResourceFoundException.class
    })
    public ResponseEntity<Result<Void>> handleHttpStatusException(Exception ex, HttpServletRequest request) {
        HttpStatus status = resolveHttpStatus(ex);
        log.warn("HTTP 异常: status={}, uri={}, type={}",
                status.value(), request.getRequestURI(), ex.getClass().getName());
        return ResponseEntity.status(status).body(Result.fail(status.value(), ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return ResponseEntity.status(status).body(Result.fail(status.value(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex, HttpServletRequest request) {
        log.error("系统异常: uri={}, type={}, stack={}",
                request.getRequestURI(), ex.getClass().getName(), ex.getStackTrace());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.INTERNAL_ERROR.getCode(), ResultCode.INTERNAL_ERROR.getMsg()));
    }

    private String resolveValidationMessage(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException validException) {
            return validException.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining("; "));
        }
        if (ex instanceof BindException bindException) {
            return bindException.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining("; "));
        }
        if (ex instanceof ConstraintViolationException violationException) {
            return violationException.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining("; "));
        }
        if (ex instanceof HandlerMethodValidationException methodValidationException) {
            String message = methodValidationException.getAllErrors().stream()
                    .map(MessageSourceResolvable::getDefaultMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("; "));
            return message.isBlank() ? ResultCode.BAD_REQUEST.getMsg() : message;
        }
        return ResultCode.BAD_REQUEST.getMsg();
    }

    private HttpStatus resolveHttpStatus(Exception ex) {
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            return HttpStatus.METHOD_NOT_ALLOWED;
        }
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            return HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        }
        if (ex instanceof NoResourceFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.BAD_REQUEST;
    }

}
