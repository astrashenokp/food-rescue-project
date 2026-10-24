package com.example.foodrescue.infrastructure.error;

import com.example.foodrescue.common.ForbiddenActionException;
import com.example.foodrescue.common.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(basePackages = {
        "com.example.foodrescue.lot.web",
        "com.example.foodrescue.volunteer.web",
        "com.example.foodrescue.delivery.web"
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebBusinessErrorAdvice {

    @ExceptionHandler(NotFoundException.class)
    public ModelAndView notFound(NotFoundException ex) {
        ModelAndView view = new ModelAndView("error/404");
        view.setStatus(HttpStatus.NOT_FOUND);
        return view;
    }

    @ExceptionHandler(ForbiddenActionException.class)
    public ModelAndView forbidden(ForbiddenActionException ex) {
        ModelAndView view = new ModelAndView("error/403");
        view.setStatus(HttpStatus.FORBIDDEN);
        return view;
    }
}
