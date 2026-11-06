package com.fayzo.crochet.shop.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import com.fayzo.crochet.shop.model.CustomerOrder;
import com.fayzo.crochet.shop.repository.CustomerRepository;
import com.fayzo.crochet.shop.repository.OrderRepository;
import com.fayzo.crochet.shop.service.CustomerSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OrderControllerCancellationTests {

    private OrderRepository orderRepository;
    private CustomerSessionService sessionService;
    private OrderController controller;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        sessionService = new CustomerSessionService();
        controller = new OrderController(
                orderRepository,
                mock(CustomerRepository.class),
                sessionService);
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
