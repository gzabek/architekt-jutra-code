package pl.devstyle.aj.productvalidation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductValidationController {

    private final ProductValidationService service;

    public ProductValidationController(ProductValidationService service) {
        this.service = service;
    }

    @PostMapping("/{productId}/validate")
    public ResponseEntity<ValidationResult> validate(@PathVariable Long productId) {
        return ResponseEntity.ok(service.validate(productId));
    }
}
