package com.devd.spring.bookstorecatalogservice.service.impl;

import com.devd.spring.bookstorecatalogservice.repository.ProductCategoryRepository;
import com.devd.spring.bookstorecatalogservice.repository.ProductRepository;
import com.devd.spring.bookstorecatalogservice.repository.ReviewRepository;
import com.devd.spring.bookstorecatalogservice.repository.dao.Product;
import com.devd.spring.bookstorecatalogservice.repository.dao.ProductCategory;
import com.devd.spring.bookstorecatalogservice.repository.dao.Review;
import com.devd.spring.bookstorecatalogservice.service.ProductService;
import com.devd.spring.bookstorecatalogservice.service.ReviewService;
import com.devd.spring.bookstorecatalogservice.web.CreateProductRequest;
import com.devd.spring.bookstorecatalogservice.web.ProductFiltersRequest;
import com.devd.spring.bookstorecatalogservice.web.ProductResponse;
import com.devd.spring.bookstorecatalogservice.web.UpdateProductRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.Predicate;
import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author: Devaraj Reddy,
 * Date : 2019-06-06
 */
@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    ProductRepository productRepository;

    @Autowired
    ProductCategoryRepository productCategoryRepository;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Override
    public String createProduct(@Valid CreateProductRequest createProductRequest) {

        Optional<ProductCategory> productCategoryOptional =
                productCategoryRepository.findById(createProductRequest.getProductCategoryId());

        ProductCategory productCategory = productCategoryOptional.orElseThrow(() -> new RuntimeException("ProductCategory doesn't exist!"));

        Product product = Product.builder()
                .productName(createProductRequest.getProductName())
                .description(createProductRequest.getDescription())
                .availableItemCount(createProductRequest.getAvailableItemCount())
                .price(createProductRequest.getPrice())
                .productCategory(productCategory)
                .imageId(createProductRequest.getImageId())
                .build();


        Product savedProduct = productRepository.save(product);
        return savedProduct.getProductId();
    }

    @Override
    public ProductResponse getProduct(String productId) {
        Optional<Product> productOptional =
                productRepository.findById(productId);

        Product product = productOptional.orElseThrow(() -> new RuntimeException("Product Id doesn't exist!"));
        ProductResponse productResponse = objectMapper.convertValue(product, ProductResponse.class);
        populateRatingForProduct(productId, productResponse);
        return productResponse;
    }

    //This way of setting rating for productResponse is not okay, But this is okay for now.
    private void populateRatingForProduct(String productId, ProductResponse productResponse) {
        List<Review> reviewsForProduct = reviewService.getReviewsForProduct(productId);
        if (reviewsForProduct.size() > 0) {
            double sum = reviewsForProduct.stream().mapToDouble(Review::getRatingValue).sum();
            double rating = sum / reviewsForProduct.size();
            productResponse.setAverageRating(BigDecimal.valueOf(rating));
        }

        productResponse.setNoOfRatings(Math.toIntExact(reviewRepository.countAllByProductId(productId)));
    }

    @Override
    public void deleteProduct(String productId) {

        productRepository.deleteById(productId);

    }

    @Override
    public void updateProduct(UpdateProductRequest updateProductRequest) {

        Optional<Product> productOptional =
                productRepository.findById(updateProductRequest.getProductId());

        //check weather product exists
        final Product productExisting = productOptional.orElseThrow(() -> new RuntimeException("Product Id doesn't exist!"));

        productExisting.setProductId(updateProductRequest.getProductId());

        if (updateProductRequest.getProductName() != null) {
            productExisting.setProductName(updateProductRequest.getProductName());
        }

        if (updateProductRequest.getDescription() != null) {
            productExisting.setDescription(updateProductRequest.getDescription());
        }

        if (updateProductRequest.getPrice() != null) {
            productExisting.setPrice(updateProductRequest.getPrice());
        }

        if (updateProductRequest.getImageId() != null) {
            productExisting.setImageId(updateProductRequest.getImageId());
        }

        if (updateProductRequest.getProductCategoryId() != null) {
            Optional<ProductCategory> productCategoryOptional =
                    productCategoryRepository.findById(updateProductRequest.getProductCategoryId());

            //check weather product category exists
            ProductCategory productCategory = productCategoryOptional.orElseThrow(() -> new RuntimeException("ProductCategory doesn't exist!"));
            productExisting.setProductCategory(productCategory);
        }

        if (updateProductRequest.getAvailableItemCount() != null) {
            productExisting.setAvailableItemCount(updateProductRequest.getAvailableItemCount());
        }

        productExisting.setCreatedAt(productExisting.getCreatedAt());

        productRepository.save(productExisting);
    }

    @Override
    public Page<Product> findAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }
    
    @Override
    public Page<ProductResponse> getAllProducts(String sort, Integer page, Integer size, String searchText, ProductFiltersRequest filters) {
        
        //set defaults
        if (size == null || size == 0) {
            size = 20;
        }
        
        //set defaults
        if (page == null || page == 0) {
            page = 0;
        }
        
        Pageable pageable;
        
        if (sort == null || sort.isEmpty()) {
            pageable = PageRequest.of(page, size);
        } else {
            Sort.Order order;
            
            try {
                String[] split = sort.split(",");
                
                Sort.Direction sortDirection = Sort.Direction.fromString(split[1]);
                order = new Sort.Order(sortDirection, split[0]).ignoreCase();
                pageable = PageRequest.of(page, size, Sort.by(order));
                
            } catch (Exception e) {
                throw new RuntimeException("Not a valid sort value, It should be 'fieldName,direction', example : 'productName,asc");
            }
            
        }

        Specification<Product> specification = Specification.where(
                (root, criteriaQuery, criteriaBuilder) -> {

                    List<Predicate> predicates = new ArrayList<>();

                    if (searchText != null) {
                        List<Predicate> predicateList = new ArrayList<>();
                        predicateList.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("productName")), "%" + searchText.toLowerCase() + "%"));
                        predicateList.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), "%" + searchText.toLowerCase() + "%"));

                        Predicate[] array = new Predicate[predicateList.size()];
                        predicates.add(criteriaBuilder.or(predicateList.toArray(array)));
                    }

                    if (filters.getMinPrice() != null) {
                        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"), filters.getMinPrice()));
                    }

                    if (filters.getMaxPrice() != null) {
                        predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"), filters.getMaxPrice()));
                    }

                    if (filters.getMinRating() != null) {
                        List<Predicate> predicateList = new ArrayList<>();
                        predicateList.add(criteriaBuilder.greaterThanOrEqualTo(root.get("averageRating"), filters.getMinRating()));
                        if (filters.getMinRating().equals(BigDecimal.ZERO)) {
                            predicateList.add(criteriaBuilder.isNull(root.get("averageRating"))); // Include no rating products
                        }

                        Predicate[] array = new Predicate[predicateList.size()];
                        predicates.add(criteriaBuilder.or(predicateList.toArray(array)));
                    }

                    if (filters.getMaxRating() != null) {
                        List<Predicate> predicateList = new ArrayList<>();
                        predicateList.add(criteriaBuilder.lessThanOrEqualTo(root.get("averageRating"), filters.getMaxRating()));
                        predicateList.add(criteriaBuilder.isNull(root.get("averageRating"))); // Include no rating products

                        Predicate[] array = new Predicate[predicateList.size()];
                        predicates.add(criteriaBuilder.or(predicateList.toArray(array)));
                    }

                    if (filters.getAvailability() != null && filters.getAvailability().equals(true)) {
                        predicates.add(criteriaBuilder.greaterThan(root.get("availableItemCount"), 0));
                    }

                    return criteriaBuilder.and(predicates.toArray(new Predicate[]{}));

                }
        );

        Page<Product> allProducts = productRepository.findAll(specification, pageable);

        return allProducts.map(Product::fromEntity);

    }
}
