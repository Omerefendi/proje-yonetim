package com.projeyonetim.controller;

import com.projeyonetim.dto.ApiResponse;
import com.projeyonetim.model.Task;
import com.projeyonetim.model.SubTask;
import com.projeyonetim.model.Project;
import com.projeyonetim.service.TaskService;
import com.projeyonetim.service.ProjectService;
import com.projeyonetim.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserService userService;

    // ---- TASKS ----
    @GetMapping("/projects/{projectId}/tasks")
    public ResponseEntity<ApiResponse<List<Task>>> getTasksByProject(@PathVariable Long projectId) {
        List<Task> tasks = taskService.getTasksByProject(projectId);
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<ApiResponse<Task>> getTask(@PathVariable Long id) {
        return taskService.getTaskById(id)
                .map(t -> ResponseEntity.ok(ApiResponse.success(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<ApiResponse<Task>> createTask(@PathVariable Long projectId,
            @RequestBody Map<String, Object> body) {
        try {
            Project project = projectService.getProjectById(projectId)
                    .orElseThrow(() -> new RuntimeException("Proje bulunamadı"));
            Task task = new Task();
            task.setTitle((String) body.get("title"));
            task.setDescription((String) body.get("description"));
            task.setProject(project);
            task.setStartDate(LocalDate.parse((String) body.get("startDate")));
            task.setEndDate(LocalDate.parse((String) body.get("endDate")));
            if (body.get("status") != null)
                task.setStatus(Task.Status.valueOf((String) body.get("status")));
            if (body.get("priority") != null)
                task.setPriority(Task.Priority.valueOf((String) body.get("priority")));
            if (body.get("assigneeId") != null) {
                Long assigneeId = Long.valueOf(body.get("assigneeId").toString());
                userService.getUserById(assigneeId).ifPresent(task::setAssignee);
            }
            if (body.get("estimatedHours") != null)
                task.setEstimatedHours(Integer.valueOf(body.get("estimatedHours").toString()));
            if (body.get("completionPercent") != null)
                task.setCompletionPercent(Integer.valueOf(body.get("completionPercent").toString()));
            Task saved = taskService.createTask(task);
            return ResponseEntity.ok(ApiResponse.success("Görev oluşturuldu", saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<ApiResponse<Task>> updateTask(@PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            Task updateData = new Task();
            updateData.setTitle((String) body.get("title"));
            updateData.setDescription((String) body.get("description"));
            updateData.setStartDate(LocalDate.parse((String) body.get("startDate")));
            updateData.setEndDate(LocalDate.parse((String) body.get("endDate")));
            if (body.get("status") != null)
                updateData.setStatus(Task.Status.valueOf((String) body.get("status")));
            if (body.get("priority") != null)
                updateData.setPriority(Task.Priority.valueOf((String) body.get("priority")));
            if (body.get("assigneeId") != null) {
                Long assigneeId = Long.valueOf(body.get("assigneeId").toString());
                userService.getUserById(assigneeId).ifPresent(updateData::setAssignee);
            }
            if (body.get("estimatedHours") != null)
                updateData.setEstimatedHours(Integer.valueOf(body.get("estimatedHours").toString()));
            if (body.get("completionPercent") != null)
                updateData.setCompletionPercent(Integer.valueOf(body.get("completionPercent").toString()));
            Task updated = taskService.updateTask(id, updateData);
            return ResponseEntity.ok(ApiResponse.success("Görev güncellendi", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/tasks/{id}/status")
    public ResponseEntity<ApiResponse<Task>> updateTaskStatus(@PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            Task task = taskService.getTaskById(id)
                    .orElseThrow(() -> new RuntimeException("Görev bulunamadı: " + id));
            task.setStatus(Task.Status.valueOf(body.get("status")));
            Task updated = taskService.createTask(task);
            return ResponseEntity.ok(ApiResponse.success("Görev durumu güncellendi", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable Long id) {
        try {
            taskService.deleteTask(id);
            return ResponseEntity.ok(ApiResponse.success("Görev silindi", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ---- SUBTASKS ----
    @GetMapping("/tasks/{taskId}/subtasks")
    public ResponseEntity<ApiResponse<List<SubTask>>> getSubTasks(@PathVariable Long taskId) {
        List<SubTask> subTasks = taskService.getSubTasksByTask(taskId);
        return ResponseEntity.ok(ApiResponse.success(subTasks));
    }

    @PostMapping("/tasks/{taskId}/subtasks")
    public ResponseEntity<ApiResponse<SubTask>> createSubTask(@PathVariable Long taskId,
            @RequestBody Map<String, Object> body) {
        try {
            Task task = taskService.getTaskById(taskId)
                    .orElseThrow(() -> new RuntimeException("Görev bulunamadı"));
            SubTask subTask = new SubTask();
            subTask.setTitle((String) body.get("title"));
            subTask.setDescription((String) body.get("description"));
            subTask.setTask(task);
            subTask.setStartDate(LocalDate.parse((String) body.get("startDate")));
            subTask.setEndDate(LocalDate.parse((String) body.get("endDate")));
            if (body.get("status") != null)
                subTask.setStatus(Task.Status.valueOf((String) body.get("status")));
            if (body.get("priority") != null)
                subTask.setPriority(Task.Priority.valueOf((String) body.get("priority")));
            if (body.get("assigneeId") != null) {
                Long assigneeId = Long.valueOf(body.get("assigneeId").toString());
                userService.getUserById(assigneeId).ifPresent(subTask::setAssignee);
            }
            if (body.get("completionPercent") != null)
                subTask.setCompletionPercent(Integer.valueOf(body.get("completionPercent").toString()));
            SubTask saved = taskService.createSubTask(subTask);
            return ResponseEntity.ok(ApiResponse.success("Alt görev oluşturuldu", saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/subtasks/{id}")
    public ResponseEntity<ApiResponse<SubTask>> updateSubTask(@PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            SubTask updateData = new SubTask();
            updateData.setTitle((String) body.get("title"));
            updateData.setDescription((String) body.get("description"));
            updateData.setStartDate(LocalDate.parse((String) body.get("startDate")));
            updateData.setEndDate(LocalDate.parse((String) body.get("endDate")));
            if (body.get("status") != null)
                updateData.setStatus(Task.Status.valueOf((String) body.get("status")));
            if (body.get("priority") != null)
                updateData.setPriority(Task.Priority.valueOf((String) body.get("priority")));
            if (body.get("assigneeId") != null) {
                Long assigneeId = Long.valueOf(body.get("assigneeId").toString());
                userService.getUserById(assigneeId).ifPresent(updateData::setAssignee);
            }
            if (body.get("completed") != null)
                updateData.setCompleted((Boolean) body.get("completed"));
            if (body.get("completionPercent") != null)
                updateData.setCompletionPercent(Integer.valueOf(body.get("completionPercent").toString()));
            SubTask updated = taskService.updateSubTask(id, updateData);
            return ResponseEntity.ok(ApiResponse.success("Alt görev güncellendi", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/subtasks/{id}/status")
    public ResponseEntity<ApiResponse<SubTask>> updateSubTaskStatus(@PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            SubTask subTask = taskService.getSubTaskById(id)
                    .orElseThrow(() -> new RuntimeException("Alt görev bulunamadı: " + id));
            subTask.setStatus(Task.Status.valueOf(body.get("status")));
            SubTask updated = taskService.createSubTask(subTask);
            return ResponseEntity.ok(ApiResponse.success("Alt görev durumu güncellendi", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/subtasks/{id}/completion")
    public ResponseEntity<ApiResponse<SubTask>> updateSubTaskCompletion(@PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            SubTask subTask = taskService.getSubTaskById(id)
                    .orElseThrow(() -> new RuntimeException("Alt görev bulunamadı: " + id));
            subTask.setCompletionPercent(Integer.valueOf(body.get("completionPercent").toString()));
            SubTask updated = taskService.createSubTask(subTask);
            return ResponseEntity.ok(ApiResponse.success("Alt görev tamamlanma oranı güncellendi", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/subtasks/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSubTask(@PathVariable Long id) {
        try {
            taskService.deleteSubTask(id);
            return ResponseEntity.ok(ApiResponse.success("Alt görev silindi", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
