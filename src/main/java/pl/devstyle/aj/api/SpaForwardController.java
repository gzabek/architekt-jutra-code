package pl.devstyle.aj.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Controller
public class SpaForwardController {

    @GetMapping(value = "/{path:[^\\.]*}")
    public String forwardSingle() {
        return "forward:/index.html";
    }

    @GetMapping(value = "/**/{path:[^\\.]*}")
    public String forwardNested() {
        return "forward:/index.html";
    }

    @ControllerAdvice
    static class SpaExceptionHandler {

        @ExceptionHandler(NoResourceFoundException.class)
        public String handleNoResource(HttpServletRequest request, NoResourceFoundException ex)
                throws NoResourceFoundException {
            String path = request.getRequestURI();
            if (path.startsWith("/api/") || path.startsWith("/assets/")) {
                throw ex;
            }
            return "forward:/index.html";
        }
    }
}
