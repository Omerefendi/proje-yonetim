package com.projeyonetim;

import com.projeyonetim.model.User;
import com.projeyonetim.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProjeYonetimApplication implements CommandLineRunner {

    @Autowired
    private UserService userService;

    public static void main(String[] args) {
        SpringApplication.run(ProjeYonetimApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // Create default admin user if no users exist
        try {
            if (userService.getAllUsers().isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword("admin123");
                admin.setFullName("Sistem Yöneticisi");
                admin.setEmail("admin@projeyonetim.com");
                admin.setRole(User.Role.ADMIN);
                userService.createUser(admin);

                User manager = new User();
                manager.setUsername("manager");
                manager.setPassword("manager123");
                manager.setFullName("Proje Müdürü");
                manager.setEmail("manager@projeyonetim.com");
                manager.setRole(User.Role.MANAGER);
                userService.createUser(manager);

                User user1 = new User();
                user1.setUsername("ahmet");
                user1.setPassword("ahmet123");
                user1.setFullName("Ahmet Yılmaz");
                user1.setEmail("ahmet@projeyonetim.com");
                user1.setRole(User.Role.USER);
                userService.createUser(user1);

                System.out.println("✅ Varsayılan kullanıcılar oluşturuldu:");
                System.out.println("   Admin: admin / admin123");
                System.out.println("   Yönetici: manager / manager123");
                System.out.println("   Kullanıcı: ahmet / ahmet123");
            }
        } catch (Exception e) {
            System.out.println("Kullanıcı oluşturma atlandı: " + e.getMessage());
        }
    }
}
