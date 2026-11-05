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
            if (repository.count() > 0) {
                return;
            }

            // ==================== BOOKMARKS ====================
            createProduct(repository,
                "Little Bee Bookmark",
                "Bookmarks",
                7.50,
                "Mercerized Cotton Thread",
                "http://localhost:5173/assets/bee_bookmarker.jpeg"
            );

            // ==================== FLOWERS ====================
            createProduct(repository,
                "Cute Pink Tulip",
                "Flowers",
                16.00,
                "100% Milk Cotton",
                "http://localhost:5173/assets/tulip.jpeg"
            );

            // ==================== BOUQUETS ====================
            createProduct(repository,
                "Everlasting Tulip Bouquet (5 Stems)",
                "Bouquets",
                38.50,
                "Combed Milk Cotton",
                "http://localhost:5173/assets/bouquet.jpeg"
            );
            // ==================== KEYCHAINS ====================
            createProduct(repository,
                "Cute Daisy Keychain",
                "Keychains",
                10.50,
                "Chenille Velvet",
                "http://localhost:5173/assets/keychain.jpg"
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