package ru.yandex.practicum.mybankfront.advice;


import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@ControllerAdvice
public class FrontControllerAdvice {


    @ExceptionHandler(IllegalStateException.class)
    public String handleGatewayFallbackException(
            IllegalStateException exception,
            RedirectAttributes redirectAttributes
    ) {

        redirectAttributes.addFlashAttribute("errors", List.of(exception.getMessage()));

        return "redirect:/account";
    }

}
