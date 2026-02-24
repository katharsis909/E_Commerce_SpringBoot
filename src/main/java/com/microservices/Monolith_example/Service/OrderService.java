package com.microservices.Monolith_example.Service;

import com.microservices.Monolith_example.Model.Order;
import com.microservices.Monolith_example.Model.Product;
import com.microservices.Monolith_example.Repository.OrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    OrderRepository orderRepository;
    @Autowired
    OrderService(OrderRepository orderRepository)
    {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void placeOrder(Product product)
    {
        Order order = new Order();
//      order.setTimestamp(LocalÐateTime.now());
        //no need because of @CreationTimeStamp
        order.setProduct(product);
        orderRepository.save(order);
    }

}
