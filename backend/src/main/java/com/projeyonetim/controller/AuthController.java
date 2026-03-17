package com.projeyonetim.controller;

import com.projeyonetim.dto.ApiResponse;
import com.projeyonetim.dto.LoginRequest;
import com.projeyonetim.dto.LoginResponse;
import com.projeyonetim.model.User;
import com.projeyonetim.security.JwtUtil;
import com.projeyonetim.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        try {
            User user = userService.getUserByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));

            if (!user.isActive()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Hesabınız devre dışı bırakılmış."));
            }

            if (!userService.validatePassword(user, request.getPassword())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Geçersiz kullanıcı adı veya şifre."));
            }

            String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
            LoginResponse response = new LoginResponse(token, user.getUsername(),
                    user.getFullName(), user.getRole().name(), user.getId());

            return ResponseEntity.ok(ApiResponse.success("Giriş başarılı", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Giriş başarısız: " + e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);
            User user = userService.getUserByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));
            return ResponseEntity.ok(ApiResponse.success(user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
