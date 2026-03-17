package com.projeyonetim.controller;

import com.projeyonetim.dto.ApiResponse;
import com.projeyonetim.model.Project;
import com.projeyonetim.model.Task;
import com.projeyonetim.model.User;
import com.projeyonetim.repository.ProjectRepository;
import com.projeyonetim.repository.TaskRepository;
import com.projeyonetim.repository.SubTaskRepository;
import com.projeyonetim.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private SubTaskRepository subTaskRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalProjects = projectRepository.count();
        long completedProjects = projectRepository.countByStatus(Project.Status.COMPLETED);
        long inProgressProjects = projectRepository.countByStatus(Project.Status.IN_PROGRESS);
        long planningProjects = projectRepository.countByStatus(Project.Status.PLANNING);
        long onHoldProjects = projectRepository.countByStatus(Project.Status.ON_HOLD);

        stats.put("totalProjects", totalProjects);
        stats.put("completedProjects", completedProjects);
        stats.put("inProgressProjects", inProgressProjects);
        stats.put("planningProjects", planningProjects);
        stats.put("onHoldProjects", onHoldProjects);

        long totalTasks = taskRepository.count();
        long completedTasks = taskRepository.countByStatus(Task.Status.COMPLETED);
        long cancelledTasks = taskRepository.countByStatus(Task.Status.CANCELLED);
        long plannedTasks = taskRepository.countByStatus(Task.Status.IN_REVIEW);
        long openTasks = totalTasks - completedTasks - cancelledTasks;

        stats.put("totalTasks", totalTasks);
        stats.put("completedTasks", completedTasks);
        stats.put("cancelledTasks", cancelledTasks);
        stats.put("plannedTasks", plannedTasks);
        stats.put("openTasks", openTasks);

        // User workload with status breakdown (tasks + subtasks)
        List<User> users = userRepository.findByActiveTrue();
        List<Task.Status> excludeStatuses = Arrays.asList(Task.Status.COMPLETED, Task.Status.CANCELLED);
        List<Map<String, Object>> userWorkloads = users.stream().map(user -> {
            Map<String, Object> workload = new HashMap<>();
            workload.put("userId", user.getId());
            workload.put("fullName", user.getFullName());
            workload.put("username", user.getUsername());

            // Per-status breakdown (tasks + subtasks combined)
            long todoTasks = taskRepository.countByAssigneeAndStatus(user, Task.Status.TODO);
            long todoSubTasks = subTaskRepository.countByAssigneeAndStatus(user, Task.Status.TODO);
            long inProgressTasks = taskRepository.countByAssigneeAndStatus(user, Task.Status.IN_PROGRESS);
            long inProgressSubTasks = subTaskRepository.countByAssigneeAndStatus(user, Task.Status.IN_PROGRESS);
            long inReviewTasks = taskRepository.countByAssigneeAndStatus(user, Task.Status.IN_REVIEW);
            long inReviewSubTasks = subTaskRepository.countByAssigneeAndStatus(user, Task.Status.IN_REVIEW);
            long completedTasks2 = taskRepository.countByAssigneeAndStatus(user, Task.Status.COMPLETED);
            long completedSubTasks = subTaskRepository.countByAssigneeAndStatus(user, Task.Status.COMPLETED);

            workload.put("todo", todoTasks + todoSubTasks);
            workload.put("inProgress", inProgressTasks + inProgressSubTasks);
            workload.put("inReview", inReviewTasks + inReviewSubTasks);
            workload.put("completed", completedTasks2 + completedSubTasks);

            // Totals
            long taskCount = taskRepository.countOpenTasksByUser(user, excludeStatuses);
            long subTaskCount = subTaskRepository.countOpenSubTasksByUser(user, excludeStatuses);
            workload.put("openTasks", taskCount);
            workload.put("openSubTasks", subTaskCount);
            workload.put("totalOpen", taskCount + subTaskCount);
            workload.put("totalAll", todoTasks + todoSubTasks + inProgressTasks + inProgressSubTasks
                    + inReviewTasks + inReviewSubTasks + completedTasks2 + completedSubTasks);
            return workload;
        }).collect(Collectors.toList());

        stats.put("userWorkloads", userWorkloads);
        stats.put("totalUsers", users.size());

        // Project workload (per-project task & subtask counts separately)
        List<Project> allProjects = projectRepository.findAllByOrderByStartDateAsc();
        List<Map<String, Object>> projectWorkloads = allProjects.stream()
                .filter(p -> p.getStatus() != Project.Status.CANCELLED)
                .map(project -> {
                    Map<String, Object> pw = new HashMap<>();
                    pw.put("projectId", project.getId());
                    pw.put("projectName", project.getName());
                    pw.put("projectStatus", project.getStatus().name());
                    pw.put("color", project.getColor());

                    long taskTodo = taskRepository.countByProjectIdAndStatus(project.getId(), Task.Status.TODO);
                    long taskInProgress = taskRepository.countByProjectIdAndStatus(project.getId(), Task.Status.IN_PROGRESS);
                    long taskInReview = taskRepository.countByProjectIdAndStatus(project.getId(), Task.Status.IN_REVIEW);
                    long taskCompleted = taskRepository.countByProjectIdAndStatus(project.getId(), Task.Status.COMPLETED);
                    long totalTaskCount = taskTodo + taskInProgress + taskInReview + taskCompleted;

                    long subTodo = subTaskRepository.countByProjectIdAndStatus(project.getId(), Task.Status.TODO);
                    long subInProgress = subTaskRepository.countByProjectIdAndStatus(project.getId(), Task.Status.IN_PROGRESS);
                    long subInReview = subTaskRepository.countByProjectIdAndStatus(project.getId(), Task.Status.IN_REVIEW);
                    long subCompleted = subTaskRepository.countByProjectIdAndStatus(project.getId(), Task.Status.COMPLETED);
                    long totalSubTaskCount = subTodo + subInProgress + subInReview + subCompleted;

                    pw.put("taskCount", totalTaskCount);
                    pw.put("subTaskCount", totalSubTaskCount);
                    pw.put("totalAll", totalTaskCount + totalSubTaskCount);
                    return pw;
                }).collect(Collectors.toList());
        stats.put("projectWorkloads", projectWorkloads);

        // Project owner workload (per-owner project status breakdown)
        Map<Long, Map<String, Object>> ownerMap = new LinkedHashMap<>();
        allProjects.stream()
                .filter(p -> p.getStatus() != Project.Status.CANCELLED && p.getOwner() != null)
                .forEach(project -> {
                    Long ownerId = project.getOwner().getId();
                    Map<String, Object> ow = ownerMap.computeIfAbsent(ownerId, k -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("ownerId", ownerId);
                        m.put("ownerName", project.getOwner().getFullName());
                        m.put("planning", 0L);
                        m.put("inProgress", 0L);
                        m.put("onHold", 0L);
                        m.put("completed", 0L);
                        m.put("totalProjects", 0L);
                        return m;
                    });
                    String statusKey;
                    switch (project.getStatus()) {
                        case PLANNING: statusKey = "planning"; break;
                        case IN_PROGRESS: statusKey = "inProgress"; break;
                        case ON_HOLD: statusKey = "onHold"; break;
                        case COMPLETED: statusKey = "completed"; break;
                        default: statusKey = null;
                    }
                    if (statusKey != null) {
                        ow.put(statusKey, (Long) ow.get(statusKey) + 1);
                    }
                    ow.put("totalProjects", (Long) ow.get("totalProjects") + 1);
                });
        stats.put("projectOwners", new ArrayList<>(ownerMap.values()));

        // Project priority distribution
        Map<String, Long> projectPriorities = new LinkedHashMap<>();
        projectPriorities.put("Düşük", projectRepository.countByPriority(Project.Priority.LOW));
        projectPriorities.put("Orta", projectRepository.countByPriority(Project.Priority.MEDIUM));
        projectPriorities.put("Yüksek", projectRepository.countByPriority(Project.Priority.HIGH));
        projectPriorities.put("Kritik", projectRepository.countByPriority(Project.Priority.CRITICAL));
        stats.put("projectPriorities", projectPriorities);

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/timeline")
    public ResponseEntity<ApiResponse<List<Project>>> getTimeline() {
        List<Project> projects = projectRepository.findAllByOrderByStartDateAsc();
        return ResponseEntity.ok(ApiResponse.success(projects));
    }
}
