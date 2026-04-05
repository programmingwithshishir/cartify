package com.cartify.auth;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;

@Controller
public class PageController {

    @GetMapping("/")
    public String home() {
        return "redirect:/login.html";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "redirect:/register.html";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "redirect:/login.html";
    }

    @GetMapping("/admin")
    public String adminPage() {
        return "redirect:/admin-login.html";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(HttpSession session) {
        Boolean isAdmin = (Boolean) session.getAttribute("isAdmin");
        if (Boolean.TRUE.equals(isAdmin)) {
            return "redirect:/admin-dashboard.html";
        }
        return "redirect:/admin-login.html";
    }

    @GetMapping("/admin/orders")
    public String adminOrdersPage(HttpSession session) {
        Boolean isAdmin = (Boolean) session.getAttribute("isAdmin");
        if (Boolean.TRUE.equals(isAdmin)) {
            return "redirect:/admin-orders.html";
        }
        return "redirect:/admin-login.html";
    }

    @GetMapping("/checkout")
    public String checkoutPage(HttpSession session) {
        Object customerId = session.getAttribute("customerId");
        if (customerId instanceof Long) {
            return "redirect:/checkout.html";
        }
        return "redirect:/login.html";
    }

    @GetMapping("/orders/tracking")
    public String orderTrackingPage(HttpSession session) {
        Object customerId = session.getAttribute("customerId");
        if (customerId instanceof Long) {
            return "redirect:/order-tracking.html";
        }
        return "redirect:/login.html";
    }
}
