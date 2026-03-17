package com.projeyonetim.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.projeyonetim.dto.ApiResponse;
import com.projeyonetim.model.Project;
import com.projeyonetim.model.User;
import com.projeyonetim.model.Task;
import com.projeyonetim.model.SubTask;
import com.projeyonetim.service.ProjectService;
import com.projeyonetim.service.TaskService;
import com.projeyonetim.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserService userService;

    @Autowired
    private TaskService taskService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Project>>> getAllProjects() {
        List<Project> projects = projectService.getAllProjects();
        return ResponseEntity.ok(ApiResponse.success(projects));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Project>> getProject(@PathVariable Long id) {
        return projectService.getProjectById(id)
                .map(p -> ResponseEntity.ok(ApiResponse.success(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Project>> createProject(@RequestBody Map<String, Object> body) {
        try {
            Project project = new Project();
            project.setName((String) body.get("name"));
            project.setDescription((String) body.get("description"));
            project.setStartDate(LocalDate.parse((String) body.get("startDate")));
            project.setEndDate(LocalDate.parse((String) body.get("endDate")));
            if (body.get("color") != null)
                project.setColor((String) body.get("color"));
            if (body.get("status") != null)
                project.setStatus(Project.Status.valueOf((String) body.get("status")));
            if (body.get("priority") != null)
                project.setPriority(Project.Priority.valueOf((String) body.get("priority")));
            if (body.get("ownerId") != null) {
                Long ownerId = Long.valueOf(body.get("ownerId").toString());
                userService.getUserById(ownerId).ifPresent(project::setOwner);
            }
            Project saved = projectService.createProject(project);
            return ResponseEntity.ok(ApiResponse.success("Proje oluşturuldu", saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Project>> updateProject(@PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            Project updateData = new Project();
            updateData.setName((String) body.get("name"));
            updateData.setDescription((String) body.get("description"));
            updateData.setStartDate(LocalDate.parse((String) body.get("startDate")));
            updateData.setEndDate(LocalDate.parse((String) body.get("endDate")));
            if (body.get("color") != null)
                updateData.setColor((String) body.get("color"));
            if (body.get("status") != null)
                updateData.setStatus(Project.Status.valueOf((String) body.get("status")));
            if (body.get("priority") != null)
                updateData.setPriority(Project.Priority.valueOf((String) body.get("priority")));
            if (body.get("ownerId") != null) {
                Long ownerId = Long.valueOf(body.get("ownerId").toString());
                userService.getUserById(ownerId).ifPresent(updateData::setOwner);
            }
            Project updated = projectService.updateProject(id, updateData);
            return ResponseEntity.ok(ApiResponse.success("Proje güncellendi", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Project>> updateProjectStatus(@PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            Project project = projectService.getProjectById(id)
                    .orElseThrow(() -> new RuntimeException("Proje bulunamadı: " + id));
            Project.Status newStatus = Project.Status.valueOf(body.get("status"));
            project.setStatus(newStatus);
            Project updated = projectService.createProject(project); // save

            // If project is marked as COMPLETED, set all tasks and subtasks to 100%
            if (newStatus == Project.Status.COMPLETED) {
                List<Task> tasks = taskService.getTasksByProject(id);
                for (Task task : tasks) {
                    task.setCompletionPercent(100);
                    task.setStatus(Task.Status.COMPLETED);
                    taskService.createTask(task);
                    List<SubTask> subtasks = taskService.getSubTasksByTask(task.getId());
                    for (SubTask st : subtasks) {
                        st.setCompletionPercent(100);
                        st.setStatus(Task.Status.COMPLETED);
                        taskService.createSubTask(st);
                    }
                }
            }

            return ResponseEntity.ok(ApiResponse.success("Proje durumu güncellendi", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/{id}/name")
    public ResponseEntity<ApiResponse<Project>> updateProjectName(@PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            Project project = projectService.getProjectById(id)
                    .orElseThrow(() -> new RuntimeException("Proje bulunamadı: " + id));
            String newName = body.get("name");
            if (newName == null || newName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Proje adı boş olamaz"));
            }
            project.setName(newName.trim());
            Project updated = projectService.createProject(project); // save
            return ResponseEntity.ok(ApiResponse.success("Proje adı güncellendi", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/{id}/owner")
    public ResponseEntity<ApiResponse<Project>> updateProjectOwner(@PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            Project project = projectService.getProjectById(id)
                    .orElseThrow(() -> new RuntimeException("Proje bulunamadı: " + id));
            if (body.get("ownerId") != null && !body.get("ownerId").toString().isEmpty()) {
                Long ownerId = Long.valueOf(body.get("ownerId").toString());
                userService.getUserById(ownerId).ifPresent(project::setOwner);
            } else {
                project.setOwner(null);
            }
            Project updated = projectService.createProject(project);
            return ResponseEntity.ok(ApiResponse.success("Proje sorumlusu güncellendi", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/{id}/priority")
    public ResponseEntity<ApiResponse<Project>> updateProjectPriority(@PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            Project project = projectService.getProjectById(id)
                    .orElseThrow(() -> new RuntimeException("Proje bulunamadı: " + id));
            project.setPriority(Project.Priority.valueOf(body.get("priority")));
            Project updated = projectService.createProject(project);
            return ResponseEntity.ok(ApiResponse.success("Proje önceliği güncellendi", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/{id}/dates")
    public ResponseEntity<ApiResponse<Project>> updateProjectDates(@PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            Project project = projectService.getProjectById(id)
                    .orElseThrow(() -> new RuntimeException("Proje bulunamadı: " + id));
            if (body.get("startDate") != null)
                project.setStartDate(LocalDate.parse(body.get("startDate")));
            if (body.get("endDate") != null)
                project.setEndDate(LocalDate.parse(body.get("endDate")));
            Project updated = projectService.createProject(project);
            return ResponseEntity.ok(ApiResponse.success("Proje tarihleri güncellendi", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/{id}/description")
    public ResponseEntity<ApiResponse<Project>> updateProjectDescription(@PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            Project project = projectService.getProjectById(id)
                    .orElseThrow(() -> new RuntimeException("Proje bulunamadı: " + id));
            project.setDescription(body.get("description"));
            Project updated = projectService.createProject(project);
            return ResponseEntity.ok(ApiResponse.success("Proje açıklaması güncellendi", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable Long id) {
        try {
            projectService.deleteProject(id);
            return ResponseEntity.ok(ApiResponse.success("Proje silindi", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<Project>> addMember(@PathVariable Long id, @PathVariable Long userId) {
        try {
            Project project = projectService.addMember(id, userId);
            return ResponseEntity.ok(ApiResponse.success("Üye eklendi", project));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<Project>> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        try {
            Project project = projectService.removeMember(id, userId);
            return ResponseEntity.ok(ApiResponse.success("Üye kaldırıldı", project));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
