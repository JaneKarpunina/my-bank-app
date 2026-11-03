package ru.yandex.practicum.mybankfront.advice;


import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@ControllerAdvice
public class FrontControllerAdvice {


    @ExceptionHandler(IllegalStateException.class)
    public String handleGatewayFallbackException(
            IllegalStateException exception,
            RedirectAttributes redirectAttributes
    ) {

        log.warn("Фронтенд перехватил исключение некорректного состояния (IllegalStateException). " +
                        "Маршрутизация UI будет перенаправлена на /account. Причина сбоя: {}",
                exception.getMessage(), exception);

        redirectAttributes.addFlashAttribute("errors", List.of(exception.getMessage()));

        return "redirect:/account";
    }

}
