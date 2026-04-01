package com.projeyonetim.controller;

import com.projeyonetim.dto.ApiResponse;
import com.projeyonetim.model.Project;
import com.projeyonetim.model.User;
import com.projeyonetim.repository.ProjectRepository;
import com.projeyonetim.repository.SubTaskRepository;
import com.projeyonetim.repository.TaskRepository;
import com.projeyonetim.repository.UserRepository;
import com.projeyonetim.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private SubTaskRepository subTaskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private DashboardController controller;

    @Test
    void getStatsIncludesLiveProjectProgressAndTaskCounts() {
        Project project = new Project();
        project.setId(7L);
        project.setName("Radar");
        project.setStatus(Project.Status.IN_PROGRESS);
        project.setPriority(Project.Priority.HIGH);
        project.setStartDate(LocalDate.of(2026, 1, 1));
        project.setEndDate(LocalDate.of(2026, 12, 31));
        project.setColor("#16a34a");
        project.setTaskCount(1);
        project.setProgressPercent(50);
        project.setCompletionState("IN_PROGRESS");

        when(projectRepository.count()).thenReturn(1L);
        when(projectRepository.countByStatus(Project.Status.COMPLETED)).thenReturn(0L);
        when(projectRepository.countByStatus(Project.Status.IN_PROGRESS)).thenReturn(1L);
        when(projectRepository.countByStatus(Project.Status.PLANNING)).thenReturn(0L);
        when(projectRepository.countByStatus(Project.Status.ON_HOLD)).thenReturn(0L);
        when(projectRepository.countByPriority(Project.Priority.LOW)).thenReturn(0L);
        when(projectRepository.countByPriority(Project.Priority.MEDIUM)).thenReturn(0L);
        when(projectRepository.countByPriority(Project.Priority.HIGH)).thenReturn(1L);
        when(projectRepository.countByPriority(Project.Priority.CRITICAL)).thenReturn(0L);

        when(taskRepository.count()).thenReturn(1L);
        when(taskRepository.countByStatus(com.projeyonetim.model.Task.Status.COMPLETED)).thenReturn(0L);
        when(taskRepository.countByStatus(com.projeyonetim.model.Task.Status.CANCELLED)).thenReturn(0L);
        when(taskRepository.countByStatus(com.projeyonetim.model.Task.Status.IN_REVIEW)).thenReturn(0L);
        when(taskRepository.countByProjectId(7L)).thenReturn(1L);
        when(subTaskRepository.countByProjectId(7L)).thenReturn(0L);
        when(userRepository.findByActiveTrue()).thenReturn(List.of());
        when(projectService.getAllProjects()).thenReturn(List.of(project));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.getStats();
        Map<String, Object> stats = response.getBody().getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projectWorkloads = (List<Map<String, Object>>) stats.get("projectWorkloads");

        assertThat(projectWorkloads).hasSize(1);
        assertThat(projectWorkloads.get(0))
                .containsEntry("taskCount", 1L)
                .containsEntry("subTaskCount", 0L)
                .containsEntry("progressPercent", 50)
                .containsEntry("completionState", "IN_PROGRESS");
    }

    @Test
    void getStatsExcludesUsersWithoutAssignedWorkload() {
        User admin = new User();
        admin.setId(1L);
        admin.setFullName("Sistem Yöneticisi");
        admin.setUsername("admin");
        admin.setActive(true);

        User ahmet = new User();
        ahmet.setId(2L);
        ahmet.setFullName("Ahmet");
        ahmet.setUsername("ahmet");
        ahmet.setActive(true);

        when(projectRepository.count()).thenReturn(0L);
        when(projectRepository.countByStatus(Project.Status.COMPLETED)).thenReturn(0L);
        when(projectRepository.countByStatus(Project.Status.IN_PROGRESS)).thenReturn(0L);
        when(projectRepository.countByStatus(Project.Status.PLANNING)).thenReturn(0L);
        when(projectRepository.countByStatus(Project.Status.ON_HOLD)).thenReturn(0L);
        when(projectRepository.countByPriority(Project.Priority.LOW)).thenReturn(0L);
        when(projectRepository.countByPriority(Project.Priority.MEDIUM)).thenReturn(0L);
        when(projectRepository.countByPriority(Project.Priority.HIGH)).thenReturn(0L);
        when(projectRepository.countByPriority(Project.Priority.CRITICAL)).thenReturn(0L);

        when(taskRepository.count()).thenReturn(1L);
        when(taskRepository.countByStatus(com.projeyonetim.model.Task.Status.COMPLETED)).thenReturn(0L);
        when(taskRepository.countByStatus(com.projeyonetim.model.Task.Status.CANCELLED)).thenReturn(0L);
        when(taskRepository.countByStatus(com.projeyonetim.model.Task.Status.IN_REVIEW)).thenReturn(0L);

        when(userRepository.findByActiveTrue()).thenReturn(List.of(admin, ahmet));
        when(projectService.getAllProjects()).thenReturn(List.of());

        when(taskRepository.countByAssigneeAndStatus(admin, com.projeyonetim.model.Task.Status.TODO)).thenReturn(0L);
        when(taskRepository.countByAssigneeAndStatus(admin, com.projeyonetim.model.Task.Status.IN_PROGRESS)).thenReturn(0L);
        when(taskRepository.countByAssigneeAndStatus(admin, com.projeyonetim.model.Task.Status.IN_REVIEW)).thenReturn(0L);
        when(taskRepository.countByAssigneeAndStatus(admin, com.projeyonetim.model.Task.Status.COMPLETED)).thenReturn(0L);
        when(subTaskRepository.countByAssigneeAndStatus(admin, com.projeyonetim.model.Task.Status.TODO)).thenReturn(0L);
        when(subTaskRepository.countByAssigneeAndStatus(admin, com.projeyonetim.model.Task.Status.IN_PROGRESS)).thenReturn(0L);
        when(subTaskRepository.countByAssigneeAndStatus(admin, com.projeyonetim.model.Task.Status.IN_REVIEW)).thenReturn(0L);
        when(subTaskRepository.countByAssigneeAndStatus(admin, com.projeyonetim.model.Task.Status.COMPLETED)).thenReturn(0L);
        when(taskRepository.countOpenTasksByUser(eq(admin), anyList())).thenReturn(0L);
        when(subTaskRepository.countOpenSubTasksByUser(eq(admin), anyList())).thenReturn(0L);

        when(taskRepository.countByAssigneeAndStatus(ahmet, com.projeyonetim.model.Task.Status.TODO)).thenReturn(0L);
        when(taskRepository.countByAssigneeAndStatus(ahmet, com.projeyonetim.model.Task.Status.IN_PROGRESS)).thenReturn(1L);
        when(taskRepository.countByAssigneeAndStatus(ahmet, com.projeyonetim.model.Task.Status.IN_REVIEW)).thenReturn(0L);
        when(taskRepository.countByAssigneeAndStatus(ahmet, com.projeyonetim.model.Task.Status.COMPLETED)).thenReturn(0L);
        when(subTaskRepository.countByAssigneeAndStatus(ahmet, com.projeyonetim.model.Task.Status.TODO)).thenReturn(0L);
        when(subTaskRepository.countByAssigneeAndStatus(ahmet, com.projeyonetim.model.Task.Status.IN_PROGRESS)).thenReturn(0L);
        when(subTaskRepository.countByAssigneeAndStatus(ahmet, com.projeyonetim.model.Task.Status.IN_REVIEW)).thenReturn(0L);
        when(subTaskRepository.countByAssigneeAndStatus(ahmet, com.projeyonetim.model.Task.Status.COMPLETED)).thenReturn(0L);
        when(taskRepository.countOpenTasksByUser(eq(ahmet), anyList())).thenReturn(1L);
        when(subTaskRepository.countOpenSubTasksByUser(eq(ahmet), anyList())).thenReturn(0L);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.getStats();
        Map<String, Object> stats = response.getBody().getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> userWorkloads = (List<Map<String, Object>>) stats.get("userWorkloads");

        assertThat(userWorkloads).hasSize(1);
        assertThat(userWorkloads.get(0))
                .containsEntry("fullName", "Ahmet")
                .containsEntry("inProgress", 1L)
                .containsEntry("totalAll", 1L);
    }
}
