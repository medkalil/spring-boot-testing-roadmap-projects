package com.example.jpa.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.example.jpa.entity.Category;
import com.example.jpa.entity.Product;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
/*
 * @DataJpaTest: 
 * 1) Configures an in-memory database (HSQLDB by default). here is H2 is it's decalred in pom.xml
 * 2) Registers only the JPA-related beans (EntityManager, TransactionManager).
 * 3) Auto-scans @Repository beans in the package and sub-packages.
 * 4) Rolls back transactions after each test (isolates tests).
 * 5) Disables full auto-configuration (like @SpringBootApplication).
*/

@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void shouldSaveAndFindProduct() {
        Product product = new Product("Product 1", BigDecimal.valueOf(100), 10, null);

        Product saved = productRepository.save(product);

        assertNotNull(saved.getId());
        assertEquals("Product 1", saved.getName());
        assertEquals(BigDecimal.valueOf(100), saved.getPrice());
        assertEquals(10, saved.getStock());

        Optional<Product> found = productRepository.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
        assertEquals(saved.getName(), found.get().getName());
        assertEquals(saved.getPrice(), found.get().getPrice());
        assertEquals(saved.getStock(), found.get().getStock());
    }

    @Test  
    void shouldFindProductByName(){
        productRepository.save(new Product("Laptop", BigDecimal.valueOf(100), 10, null));
        productRepository.save(new Product("Phone", BigDecimal.valueOf(200), 20, null));

        List<Product> laptopProduct = productRepository.findByName("Laptop");

        assertEquals(1, laptopProduct.size());
        assertEquals("Laptop", laptopProduct.get(0).getName());

        List<Product> phoneProduct = productRepository.findByName("Phone");
        assertEquals(1, phoneProduct.size());
        assertEquals("Phone", phoneProduct.get(0).getName());

        List<Product> otherProduct = productRepository.findByName("Other");
        assertEquals(0, otherProduct.size());
        assertTrue(otherProduct.isEmpty());
    }

    @Test
    void shouldFindProductsWithinPriceRange(){
        productRepository.save(new Product("Laptop", BigDecimal.valueOf(100), 10, null));
        productRepository.save(new Product("Phone", BigDecimal.valueOf(200), 20, null));
        productRepository.save(new Product("Table", BigDecimal.valueOf(300), 30, null));

        List<Product> products0to100 = productRepository.findByPriceBetween(BigDecimal.valueOf(0), BigDecimal.valueOf(100));
        assertEquals(1, products0to100.size());

        List<Product> products100to200 = productRepository.findByPriceBetween(BigDecimal.valueOf(100), BigDecimal.valueOf(200));
        assertEquals(2, products100to200.size());
        
        List<Product> products0to300 = productRepository.findByPriceBetween(BigDecimal.valueOf(0), BigDecimal.valueOf(300));
        assertEquals(3, products0to300.size());
        
        List<Product> products0to500 = productRepository.findByPriceBetween(BigDecimal.valueOf(0), BigDecimal.valueOf(500));
        assertEquals(3, products0to500.size());

        List<Product> products301to400 = productRepository.findByPriceBetween(BigDecimal.valueOf(301), BigDecimal.valueOf(400));
        assertEquals(0, products301to400.size());
        assertTrue(products301to400.isEmpty());
    }

    @Test
    void shouldFindProductsByStockGraterThen(){
        productRepository.save(new Product("Laptop", BigDecimal.valueOf(100), 10, null));   
        productRepository.save(new Product("Phone", BigDecimal.valueOf(200), 20, null));
        productRepository.save(new Product("Table", BigDecimal.valueOf(300), 30, null));

        List<Product> products1 = productRepository.findByStockGreaterThan(0);
        assertEquals(3, products1.size());
        
        List<Product> products2 = productRepository.findByStockGreaterThan(10);
        assertEquals(2, products2.size());

        List<Product> products3 = productRepository.findByStockGreaterThan(20);
        assertEquals(1, products3.size());

        List<Product> products4 = productRepository.findByStockGreaterThan(30);
        assertEquals(0, products4.size());
        assertTrue(products4.isEmpty());
    }

    @Test
    void shouldFindProductsByCategory(){
        Category electronics = new Category("Electronics");
        categoryRepository.save(electronics);
        
        productRepository.save(new Product("Laptop", BigDecimal.valueOf(100), 10, electronics));   
        productRepository.save(new Product("Phone", BigDecimal.valueOf(200), 20, electronics));

        List<Product> products1 = productRepository.findByCategory(electronics);
        assertEquals(2, products1.size());

        Category books = new Category("Books");
        categoryRepository.save(books);
        
        productRepository.save(new Product("Book", BigDecimal.valueOf(100), 10, books));

        List<Product> products2 = productRepository.findByCategory(books);
        assertEquals(1, products2.size());
    }

    // Transactions: @DataJpaTest tests are transactional by default and normally roll back after each test.
    @Test
    void shouldSaveProductAndNotPersistEvenAfterRollback(){
        Product product = new Product("Chair", BigDecimal.valueOf(100), 10, null);
        productRepository.save(product);
        
        List<Product> products = productRepository.findAll();
        assertEquals(1, products.size());
    }

    @Test
    void shouldNotReturnTheSavedProductAfterRollback(){  
        List<Product> products = productRepository.findAll();
        assertEquals(0, products.size());
        assertTrue(products.isEmpty());
    }

    // Phase 6.2 — Derived queries
        // findByNameContaining()
        // findByPriceLessThan()
        // findByStockLessThan()
        // findByCategoryName()
        // findByNameContainingIgnoreCase()
    
    @Test
    void shouldFindProductsByNameContaining() {
        productRepository.save(new Product("Laptop", BigDecimal.valueOf(100), 10, null));
        productRepository.save(new Product("Phone", BigDecimal.valueOf(200), 20, null));
        productRepository.save(new Product("Table", BigDecimal.valueOf(300), 30, null));

        List<Product> products1 = productRepository.findByNameContaining("Lap");
        assertEquals(1, products1.size());

        List<Product> products2 = productRepository.findByNameContaining("Ph");
        assertEquals(1, products2.size());

        List<Product> products3 = productRepository.findByNameContaining("Tab");
        assertEquals(1, products3.size());

        List<Product> products4 = productRepository.findByNameContaining("Other");
        assertEquals(0, products4.size());
        assertTrue(products4.isEmpty());
    }

    @Test
    void shouldFindProductsByNameContainingIgnoreCase() {
        productRepository.save(new Product("Laptop", BigDecimal.valueOf(100), 10, null));
        productRepository.save(new Product("Phone", BigDecimal.valueOf(200), 20, null));
        productRepository.save(new Product("Table", BigDecimal.valueOf(300), 30, null));

        List<Product> products1 = productRepository.findByNameContainingIgnoreCase("Lap");
        assertEquals(1, products1.size());

        List<Product> products2 = productRepository.findByNameContainingIgnoreCase("Ph");
        assertEquals(1, products2.size());

        List<Product> products3 = productRepository.findByNameContainingIgnoreCase("Tab");
        assertEquals(1, products3.size());

        List<Product> products4 = productRepository.findByNameContainingIgnoreCase("Other");
        assertEquals(0, products4.size());
        assertTrue(products4.isEmpty());

        List<Product> products5 = productRepository.findByNameContainingIgnoreCase("lap");
        assertEquals(1, products5.size());

        List<Product> products6 = productRepository.findByNameContainingIgnoreCase("phone");
        assertEquals(1, products6.size());

        List<Product> products7 = productRepository.findByNameContainingIgnoreCase("table");
        assertEquals(1, products7.size());

        List<Product> products8 = productRepository.findByNameContainingIgnoreCase("other");
        assertEquals(0, products8.size());
        assertTrue(products8.isEmpty());
    }

    @Test
    void shouldFindProductsByPriceLessThan() {
        productRepository.save(new Product("Laptop", BigDecimal.valueOf(100), 10, null));
        productRepository.save(new Product("Phone", BigDecimal.valueOf(200), 20, null));
        productRepository.save(new Product("Table", BigDecimal.valueOf(300), 30, null));

        List<Product> products1 = productRepository.findByPriceLessThan(BigDecimal.valueOf(100));
        assertEquals(0, products1.size());
        assertTrue(products1.isEmpty());

        List<Product> products2 = productRepository.findByPriceLessThan(BigDecimal.valueOf(200));
        assertEquals(1, products2.size());

        List<Product> products3 = productRepository.findByPriceLessThan(BigDecimal.valueOf(300));
        assertEquals(2, products3.size());

        List<Product> products4 = productRepository.findByPriceLessThan(BigDecimal.valueOf(400));
        assertEquals(3, products4.size());
    }

    @Test
    void shouldFindProductsByStockLessThan() {
        productRepository.save(new Product("Laptop", BigDecimal.valueOf(100), 10, null));
        productRepository.save(new Product("Phone", BigDecimal.valueOf(200), 20, null));
        productRepository.save(new Product("Table", BigDecimal.valueOf(300), 30, null));

        List<Product> products1 = productRepository.findByStockLessThan(10);
        assertEquals(0, products1.size());
        assertTrue(products1.isEmpty());

        List<Product> products2 = productRepository.findByStockLessThan(20);
        assertEquals(1, products2.size());

        List<Product> products3 = productRepository.findByStockLessThan(30);
        assertEquals(2, products3.size());

        List<Product> products4 = productRepository.findByStockLessThan(40);
        assertEquals(3, products4.size());
    }


    // Phase 6.3 — Relationships
    @Test
    void shouldFindProductsByCatgeory(){
        Category category = new Category("Books");
        categoryRepository.save(category);
        productRepository.save(new Product("Book", BigDecimal.valueOf(100), 10, category));
        productRepository.save(new Product("Book2", BigDecimal.valueOf(200), 20, category));
        
        List<Product> products = productRepository.findByCategory(category);
        assertEquals(2, products.size());
    }

    // nested:
    //  Product
    //    ↓
    // category
    //    ↓
    // name
    @Test
    void shouldNotFindProductsByCatgeoryName(){
        Category category = new Category("Books");
        categoryRepository.save(category);
        productRepository.save(new Product("Book", BigDecimal.valueOf(100), 10, category));
        productRepository.save(new Product("Book2", BigDecimal.valueOf(200), 20, category));
        
        List<Product> products1 = productRepository.findByCategoryName("Books");
        assertEquals(2, products1.size());
        
        List<Product> products2 = productRepository.findByCategoryName("Electronics");
        assertEquals(0, products2.size());
        assertTrue(products2.isEmpty());
    }

    // Phase 6.4 — Constraints
        // productRepository.flush(): the save() doesn't necessarily execute the SQL immediately. Hibernate may delay the SQL until flush/commit.
        // save()
        // ↓
        // Persistence context
        // ↓
        // flush()
        // ↓
        // SQL
        // ↓
        // Database constraint
    @Test
    void shouldNotSaveProductWithExistingSku(){
        Product product1 = new Product("Book", BigDecimal.valueOf(100), 10, "SKU123", null);
        productRepository.save(product1);
        
        productRepository.flush();
        
        assertThrows(DataIntegrityViolationException.class, () -> productRepository.save(new Product("Book2", BigDecimal.valueOf(200), 20, "SKU123", null)));
    }

    // Phase 6.5 — Custom queries
    @Test
    void shouldReturnExpensiveProducts(){
        productRepository.save(new Product("Book", BigDecimal.valueOf(100), 10, null));
        productRepository.save(new Product("Book2", BigDecimal.valueOf(200), 20, null));
        productRepository.save(new Product("Book3", BigDecimal.valueOf(300), 30, null));
        
        List<Product> products = productRepository.findExpensiveProducts(BigDecimal.valueOf(200));
        assertEquals(1, products.size());
        assertEquals("Book3", products.get(0).getName());
        assertEquals(BigDecimal.valueOf(300), products.get(0).getPrice());
    }

    @Test
    void shouldSearchProductsByPriceAndStock() {
        productRepository.save(new Product("Book", BigDecimal.valueOf(100), 10, null));
        productRepository.save(new Product("Book2", BigDecimal.valueOf(200), 20, null));
        productRepository.save(new Product("Book3", BigDecimal.valueOf(300), 30, null));
        
        List<Product> products = productRepository.searchProducts(BigDecimal.valueOf(100), BigDecimal.valueOf(300), 10);
        assertEquals(1, products.size());
    }

    // Phase 6.6 — Pagination and sorting
    @Test
    void shouldReturnPaginatedProducts() {
        // Arrange: create and save 10 products
        Category category = new Category("Electronics");
        category = categoryRepository.save(category);  // ← IMPORTANT

        for (int i = 1; i <= 10; i++) {
            Product p = new Product(
                    "Item " + i,
                    BigDecimal.valueOf(i * 100),
                    i,
                    "SKU" + i,
                    category
            );
            productRepository.save(p);
        }

        productRepository.flush(); // Ensure all data is in DB

        Pageable pageable = PageRequest.of(0, 5); // 0: currentPageIndex, 5: pageSize/items per page
        Page<Product> result = productRepository.findByCategory(category, pageable);
        
        assertEquals(5, result.getSize());    // 5 items per page
        assertEquals(2, result.getTotalPages()); // 10 items / 5 per page = 2 pages
        assertEquals(10, result.getTotalElements()); // 10 items total
        assertEquals(0, result.getNumber());      // first page (index 0)
    }

    @Test
    void shouldReturnPaginatedProductsSorted() {
        Category category = new Category("Electronics");
        category = categoryRepository.save(category);

        for (int i = 1; i <= 10; i++) {
            Product p = new Product(
                    "Item " + i,
                    BigDecimal.valueOf(i * 100),
                    i,
                    "SKU" + i,
                    category
            );
            productRepository.save(p);
        }

        productRepository.flush();

        Pageable pageable = PageRequest.of(0, 5, Sort.by("price").ascending()); 
        Page<Product> result = productRepository.findByCategory(category, pageable);
        
        assertEquals(5, result.getSize());   
        assertEquals(2, result.getTotalPages()); 
        assertEquals(10, result.getTotalElements());
        assertEquals(0, result.getNumber());  
        
        assertEquals("Item 1", result.getContent().get(0).getName());  
        assertEquals("Item 2", result.getContent().get(1).getName());  
        
    }

}