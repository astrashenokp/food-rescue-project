package com.example.foodrescue.infrastructure.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebErrorAdvice {

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNotFound(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/html")) {
            ModelAndView view = new ModelAndView("error/404");
            view.setStatus(HttpStatus.NOT_FOUND);
            return view;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Ресурс не знайдено");
        problem.setTitle("Не знайдено");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
