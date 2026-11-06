package com.fayzo.crochet.shop.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fayzo.crochet.shop.model.Product;
import com.fayzo.crochet.shop.repository.ProductRepository;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(ProductRepository repository) {
        return args -> {
            // Clears previous database rows that have the old "localhost:5173" URLs
            repository.deleteAll();

            // ==================== FLOWERS ====================
            createProduct(repository,
                "Cute Pink Tulip",
                "Flowers",
                96.00,
                "100% Milk Cotton",
                "/assets/tulip.jpeg"
            );

            // ==================== BOUQUETS ====================
            createProduct(repository,
                "Everlasting Tulip Bouquet (5 Stems)",
                "Bouquets",
                1200,
                "Combed Milk Cotton",
                "/assets/bouquet.jpeg"
            );

            // ==================== KEYCHAINS ====================
            createProduct(repository,
                "Cute Daisy Keychain",
                "Keychains",
                110,
                "Chenille Velvet",
                "/assets/keychain.jpg"
            );

            // ==================== BOOKMARKS ====================
            createProduct(repository,
                "Little Bee Bookmark",
                "Bookmarks",
                120,
                "Mercerized Cotton Thread",
                "/assets/bee_bookmarker.jpeg"
            );
        };
    }

    private void createProduct(ProductRepository repo, String name, String category, double price, String yarn, String image) {
        Product p = new Product();
        p.setName(name);
        p.setCategory(category);
        p.setPrice(price);
        p.setYarnType(yarn);
        p.setImageUrl(image);
        p.setInStock(true);
        repo.save(p);
    }
}