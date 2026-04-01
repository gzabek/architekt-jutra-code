package pl.devstyle.aj.category;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.core.error.EntityNotFoundException;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return categoryRepository.findById(id)
                .map(CategoryResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Category", id));
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        var category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());
        return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public CategoryResponse update(Long id, UpdateCategoryRequest request) {
        var category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category", id));
        category.setName(request.name());
        category.setDescription(request.description());
        return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public void delete(Long id) {
        var category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category", id));

        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new CategoryHasProductsException(id);
        }
    }
}
