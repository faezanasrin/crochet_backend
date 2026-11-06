package com.fayzo.crochet.shop.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.List;

import com.fayzo.crochet.shop.model.Customer;
import com.fayzo.crochet.shop.model.CustomerOrder;
import com.fayzo.crochet.shop.model.Product;
import com.fayzo.crochet.shop.repository.CustomerRepository;
import com.fayzo.crochet.shop.repository.OrderRepository;
import com.fayzo.crochet.shop.repository.ProductRepository;
import com.fayzo.crochet.shop.service.CustomerSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OrderControllerCancellationTests {

    private OrderRepository orderRepository;
    private CustomerRepository customerRepository;
    private ProductRepository productRepository;
    private CustomerSessionService sessionService;
    private OrderController controller;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        customerRepository = mock(CustomerRepository.class);
        productRepository = mock(ProductRepository.class);
        sessionService = new CustomerSessionService();
        controller = new OrderController(
                orderRepository,
                customerRepository,
                productRepository,
                sessionService);
    }

    @Test
    void placesUpiOrderUsingCatalogPriceAndServerCalculatedDelivery() {
        Customer customer = new Customer();
        customer.setEmail("buyer@example.com");
        customer.setName("Buyer");
        Product product = new Product(
                "Crochet flower", "Flowers", 100.0, "Cotton", "", true);
        product.setId(5L);
        when(customerRepository.findByEmailIgnoreCase("buyer@example.com"))
                .thenReturn(Optional.of(customer));
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(CustomerOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Map<String, Object> item = Map.of(
                "productId", 5,
                "quantity", 2,
                "price", 1,
                "name", "Tampered name");
        Map<String, Object> orderData = Map.of(
                "paymentMethod", "UPI",
                "utrNumber", "123456789012",
                "subtotal", 999,
                "deliveryCharge", 0,
                "totalAmount", 999,
                "items", List.of(item));
        String authorization = customerAuthorization("buyer@example.com");

        ResponseEntity<?> response = controller.placeOrder(authorization, orderData);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        CustomerOrder savedOrder = assertInstanceOf(CustomerOrder.class, response.getBody());
        assertEquals("UPI", savedOrder.getPaymentMethod());
        assertEquals("123456789012", savedOrder.getUtrNumber());
        assertEquals("123456789012", savedOrder.getPaymentReference());
        assertEquals(200.0, savedOrder.getSubtotal());
        assertEquals(49.0, savedOrder.getDeliveryCharge());
        assertEquals(249.0, savedOrder.getTotalAmount());
        verify(orderRepository).save(savedOrder);
    }

    @Test
    void waivesDeliveryAtTheFreeDeliveryThreshold() {
        Customer customer = new Customer();
        customer.setEmail("buyer@example.com");
        Product product = new Product(
                "Crochet bouquet", "Bouquets", 499.0, "Cotton", "", true);
        product.setId(7L);
        when(customerRepository.findByEmailIgnoreCase("buyer@example.com"))
                .thenReturn(Optional.of(customer));
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(CustomerOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = controller.placeOrder(
                customerAuthorization("buyer@example.com"),
                Map.of(
                        "paymentMethod", "UPI",
                        "utrNumber", "123456789012",
                        "items", List.of(
                                Map.of("productId", 7, "quantity", 1))));

        CustomerOrder savedOrder = assertInstanceOf(CustomerOrder.class, response.getBody());
        assertEquals(499.0, savedOrder.getSubtotal());
        assertEquals(0.0, savedOrder.getDeliveryCharge());
        assertEquals(499.0, savedOrder.getTotalAmount());
    }

    @Test
    void rejectsCashOnDeliveryOrders() {
        Customer customer = new Customer();
        customer.setEmail("buyer@example.com");
        when(customerRepository.findByEmailIgnoreCase("buyer@example.com"))
                .thenReturn(Optional.of(customer));

        ResponseEntity<?> response = controller.placeOrder(
                customerAuthorization("buyer@example.com"),
                Map.of("paymentMethod", "Cash on Delivery"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    void customerCanCancelTheirPendingOrder() {
        CustomerOrder order = order("buyer@example.com", "PENDING");
        when(orderRepository.findById(12L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(CustomerOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        String authorization = customerAuthorization("buyer@example.com");

        ResponseEntity<?> response = controller.cancelOrder(12L, authorization);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CANCELLED", order.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void customerCannotCancelAnotherCustomersOrder() {
        CustomerOrder order = order("owner@example.com", "PENDING");
        when(orderRepository.findById(12L)).thenReturn(Optional.of(order));
        String authorization = customerAuthorization("other@example.com");

        ResponseEntity<?> response = controller.cancelOrder(12L, authorization);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("PENDING", order.getStatus());
        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    void adminSessionCannotUseCustomerCancellationRoute() {
        String authorization = "Bearer " + sessionService.createAdminSession();

        ResponseEntity<?> response = controller.cancelOrder(12L, authorization);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(orderRepository, never()).findById(12L);
    }

    @Test
    void customerCannotCancelAnOrderThatIsAlreadyInProgress() {
        CustomerOrder order = order("buyer@example.com", "CROCHETING");
        when(orderRepository.findById(12L)).thenReturn(Optional.of(order));
        String authorization = customerAuthorization("buyer@example.com");

        ResponseEntity<?> response = controller.cancelOrder(12L, authorization);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CROCHETING", order.getStatus());
        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    void adminCanRejectAnOrder() {
        CustomerOrder order = order("buyer@example.com", "PENDING");
        when(orderRepository.findById(12L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(CustomerOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        String authorization =
                "Bearer " + sessionService.createAdminSession();

        ResponseEntity<?> response = controller.updateOrderStatus(
                12L, authorization, Map.of("status", "REJECTED"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("REJECTED", order.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void adminCanPermanentlyDeleteAnOrder() {
        CustomerOrder order = order("buyer@example.com", "COMPLETED");
        when(orderRepository.findById(12L)).thenReturn(Optional.of(order));
        String authorization =
                "Bearer " + sessionService.createAdminSession();

        ResponseEntity<?> response = controller.deleteOrder(12L, authorization);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(orderRepository).delete(order);
    }

    @Test
    void customerCannotDeleteAnOrder() {
        String authorization = customerAuthorization("buyer@example.com");

        ResponseEntity<?> response = controller.deleteOrder(12L, authorization);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(orderRepository, never()).findById(12L);
    }

    @Test
    void customerCanDeleteTheirOwnCompletedOrder() {
        CustomerOrder order = order("buyer@example.com", "COMPLETED");
        when(orderRepository.findById(12L)).thenReturn(Optional.of(order));
        String authorization = customerAuthorization("buyer@example.com");

        ResponseEntity<?> response =
                controller.deleteOwnFinishedOrder(12L, authorization);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(orderRepository).delete(order);
    }

    @Test
    void customerCannotDeleteAnotherCustomersFinishedOrder() {
        CustomerOrder order = order("owner@example.com", "COMPLETED");
        when(orderRepository.findById(12L)).thenReturn(Optional.of(order));
        String authorization = customerAuthorization("other@example.com");

        ResponseEntity<?> response =
                controller.deleteOwnFinishedOrder(12L, authorization);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(orderRepository, never()).delete(order);
    }

    @Test
    void customerCannotDeleteAnActiveOrder() {
        CustomerOrder order = order("buyer@example.com", "DISPATCHED");
        when(orderRepository.findById(12L)).thenReturn(Optional.of(order));
        String authorization = customerAuthorization("buyer@example.com");

        ResponseEntity<?> response =
                controller.deleteOwnFinishedOrder(12L, authorization);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(orderRepository, never()).delete(order);
    }

    private CustomerOrder order(String customerEmail, String status) {
        CustomerOrder order = new CustomerOrder();
        order.setCustomerEmail(customerEmail);
        order.setStatus(status);
        return order;
    }

    private String customerAuthorization(String email) {
        return "Bearer " + sessionService.createCustomerSession(email);
    }
}
