package com.backend.account.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ProblemDetail> missing(NotFoundException e) {
    return error(404, e.getMessage());
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ProblemDetail> business(BusinessException e) {
    return error(409, e.getMessage());
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    org.springframework.orm.ObjectOptimisticLockingFailureException.class,
    org.springframework.dao.CannotAcquireLockException.class
  })
  ResponseEntity<ProblemDetail> conflict(Exception e) {
    return error(409, "Registro duplicado o referencia inválida");
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    java.time.DateTimeException.class,
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ProblemDetail> bad(Exception e) {
    return error(400, "Solicitud inválida: revise los campos y sus formatos");
  }

  private ResponseEntity<ProblemDetail> error(int code, String message) {
    return ResponseEntity.status(code)
        .body(ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(code), message));
  }
}
