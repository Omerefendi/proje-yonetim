package com.projeyonetim.service;

import com.projeyonetim.dto.ProjectWorkloadReportDto;
import com.projeyonetim.model.Project;
import com.projeyonetim.model.Task;
import com.projeyonetim.model.User;
import com.projeyonetim.repository.ProjectRepository;
import com.projeyonetim.repository.SubTaskRepository;
import com.projeyonetim.repository.TaskRepository;
import com.projeyonetim.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectWorkloadReportServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private SubTaskRepository subTaskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectWorkloadReportService service;

    @Test
    void generatesZeroRowsForActiveUsersWithoutWorkload() {
        User ayse = buildUser(1L, "Ayse Akin");
        User burak = buildUser(2L, "Burak Demir");

        Project alpha = buildProject(10L, "Alpha");
        Task ayseTask = buildTask(100L, ayse, alpha);

        when(taskRepository.findAllWithProjectAndAssignee()).thenReturn(List.of(ayseTask));
        when(subTaskRepository.findAllWithTaskProjectAndAssignee()).thenReturn(List.of());
        when(userRepository.findByActiveTrue()).thenReturn(List.of(ayse, burak));

        ProjectWorkloadReportDto report = service.generateReport(null, null, "ALL");

        assertThat(report.getRows()).hasSize(2);
        assertThat(report.getRows())
                .extracting(ProjectWorkloadReportDto.Row::getPersonName, ProjectWorkloadReportDto.Row::getTotalItemCount, ProjectWorkloadReportDto.Row::getProjectId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Ayse Akin", 1, 10L),
                        org.assertj.core.groups.Tuple.tuple("Burak Demir", 0, null)
                );
        assertThat(report.getSummary().getRowCount()).isEqualTo(2);
        assertThat(report.getSummary().getUniquePersonnelCount()).isEqualTo(2);
        assertThat(report.getSummary().getUniqueProjectCount()).isEqualTo(1);
    }

    @Test
    void usesSelectedProjectForZeroRowsWhenProjectFilterIsProvided() {
        User ayse = buildUser(1L, "Ayse Akin");
        User burak = buildUser(2L, "Burak Demir");
        Project beta = buildProject(20L, "Beta");

        when(taskRepository.findAllWithProjectAndAssignee()).thenReturn(List.of());
        when(subTaskRepository.findAllWithTaskProjectAndAssignee()).thenReturn(List.of());
        when(userRepository.findByActiveTrue()).thenReturn(List.of(ayse, burak));
        when(projectRepository.findById(20L)).thenReturn(Optional.of(beta));

        ProjectWorkloadReportDto report = service.generateReport(null, 20L, "OPEN");

        assertThat(report.getRows()).hasSize(2);
        assertThat(report.getRows())
                .allSatisfy((row) -> {
                    assertThat(row.getProjectId()).isEqualTo(20L);
                    assertThat(row.getProjectName()).isEqualTo("Beta");
                    assertThat(row.getTotalItemCount()).isZero();
                });
        assertThat(report.getSummary().getUniqueProjectCount()).isEqualTo(1);
    }

    private User buildUser(Long id, String fullName) {
        User user = new User();
        user.setId(id);
        user.setUsername(fullName.toLowerCase().replace(" ", "."));
        user.setFullName(fullName);
        user.setRole(User.Role.USER);
        user.setActive(true);
        return user;
    }

    private Project buildProject(Long id, String name) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        project.setStatus(Project.Status.IN_PROGRESS);
        project.setColor("#0052CC");
        project.setStartDate(LocalDate.of(2026, 1, 1));
        project.setEndDate(LocalDate.of(2026, 12, 31));
        return project;
    }

    private Task buildTask(Long id, User assignee, Project project) {
        Task task = new Task();
        task.setId(id);
        task.setTitle("Analiz");
        task.setStatus(Task.Status.TODO);
        task.setPriority(Task.Priority.MEDIUM);
        task.setStartDate(LocalDate.of(2026, 3, 1));
        task.setEndDate(LocalDate.of(2026, 3, 10));
        task.setProject(project);
        task.setAssignee(assignee);
        task.setEstimatedHours(8);
        task.setCompletionPercent(0);
        return task;
    }
}
