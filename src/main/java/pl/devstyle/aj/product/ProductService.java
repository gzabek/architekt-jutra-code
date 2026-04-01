package pl.devstyle.aj.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.category.CategoryRepository;
import pl.devstyle.aj.core.error.EntityNotFoundException;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final DbProductQueryService dbProductQueryService;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          DbProductQueryService dbProductQueryService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.dbProductQueryService = dbProductQueryService;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll(Long categoryId, String search, String sort) {
        return findAll(categoryId, search, sort, null);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll(Long categoryId, String search, String sort, List<String> pluginFilters) {
        return dbProductQueryService.findAll(categoryId, search, sort, pluginFilters);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return productRepository.findByIdWithCategory(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Product", id));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category", request.categoryId()));

        var product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPhotoUrl(request.photoUrl());
        product.setPrice(request.price());
        product.setSku(request.sku());
        product.setCategory(category);

        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        var product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product", id));

        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category", request.categoryId()));

        product.setName(request.name());
        product.setDescription(request.description());
        product.setPhotoUrl(request.photoUrl());
        product.setPrice(request.price());
        product.setSku(request.sku());
        product.setCategory(category);

        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Transactional
    public void delete(Long id) {
        var product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product", id));
        productRepository.delete(product);
    }
}
