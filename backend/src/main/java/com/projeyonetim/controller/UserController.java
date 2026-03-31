package com.projeyonetim.controller;

import com.projeyonetim.dto.ApiResponse;
import com.projeyonetim.model.User;
import com.projeyonetim.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<User>> getUser(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(u -> ResponseEntity.ok(ApiResponse.success(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Admin endpoints
    @PostMapping("/admin/users")
    public ResponseEntity<ApiResponse<User>> createUser(@RequestBody Map<String, Object> body) {
        try {
            User user = new User();
            user.setUsername((String) body.get("username"));
            user.setPassword((String) body.get("password"));
            user.setFullName((String) body.get("fullName"));
            user.setEmail((String) body.get("email"));
            if (body.get("role") != null)
                user.setRole(User.Role.valueOf((String) body.get("role")));
            User saved = userService.createUser(user);
            return ResponseEntity.ok(ApiResponse.success("Kullanıcı oluşturuldu", saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/admin/users/{id}")
    public ResponseEntity<ApiResponse<User>> updateUser(@PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            User updateData = new User();
            updateData.setUsername((String) body.get("username"));
            updateData.setFullName((String) body.get("fullName"));
            updateData.setEmail((String) body.get("email"));
            if (body.get("role") != null)
                updateData.setRole(User.Role.valueOf((String) body.get("role")));
            if (body.get("active") != null)
                updateData.setActive((Boolean) body.get("active"));
            if (body.get("password") != null)
                updateData.setPassword((String) body.get("password"));
            User updated = userService.updateUser(id, updateData);
            return ResponseEntity.ok(ApiResponse.success("Kullanıcı güncellendi", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(ApiResponse.success("Kullanıcı devre dışı bırakıldı", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
