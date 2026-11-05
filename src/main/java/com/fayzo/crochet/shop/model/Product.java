package com.fayzo.crochet.shop.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String category;       // e.g., "Amigurumi", "Tote Bags", "Wearables"
    private Double price;
    private String yarnType;       // e.g., "Chenille Velvet", "Milk Cotton"
    private String imageUrl;
    private boolean inStock;

    public Product() {}

    public Product(String name, String category, Double price, String yarnType, String imageUrl, boolean inStock) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.yarnType = yarnType;
        this.imageUrl = imageUrl;
        this.inStock = inStock;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public String getYarnType() { return yarnType; }
    public void setYarnType(String yarnType) { this.yarnType = yarnType; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public boolean isInStock() { return inStock; }
    public void setInStock(boolean inStock) { this.inStock = inStock; }
}