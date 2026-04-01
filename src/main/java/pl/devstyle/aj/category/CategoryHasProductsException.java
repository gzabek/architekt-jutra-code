package pl.devstyle.aj.category;

import pl.devstyle.aj.core.error.BusinessConflictException;

public class CategoryHasProductsException extends BusinessConflictException {

    public CategoryHasProductsException(Long categoryId) {
        super("Category with id " + categoryId + " cannot be deleted because it has associated products");
    }
}
