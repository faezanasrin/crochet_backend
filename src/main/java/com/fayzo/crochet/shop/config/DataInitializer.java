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

            // ==================== FOUNDER'S EXCLUSIVE ====================
            createProduct(repository,
                "Founder's Mystery Box 🌸 (Batch of 10)",
                "Mystery Box",
                349.00,
                "Assorted Milk Cotton & Velvet",
                "https://images.unsplash.com/photo-1549465220-1a8b9238cd48?w=600&auto=format&fit=crop&q=80"
            );
        };
    }

    private void createProduct(ProductRepository repo, String name, String category, double price, String yarn, String image) {
        if (repo.existsByNameIgnoreCase(name)) {
            return;
        }

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