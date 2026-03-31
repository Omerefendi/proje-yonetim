package com.projeyonetim.service;

import com.projeyonetim.model.Project;
import com.projeyonetim.model.Task;
import com.projeyonetim.repository.ProjectRepository;
import com.projeyonetim.repository.TaskRepository;
import com.projeyonetim.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectDocumentService projectDocumentService;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void getAllProjectsEnrichesProgressAndCompletionSummary() {
        Project project = buildProject(10L, "Alpha");
        Task task = buildTask(100L, project, 50);

        when(projectRepository.findAllByOrderByStartDateAsc()).thenReturn(List.of(project));
        when(taskRepository.findByProjectIdOrdered(10L)).thenReturn(List.of(task));

        List<Project> projects = projectService.getAllProjects();

        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).getTaskCount()).isEqualTo(1);
        assertThat(projects.get(0).getProgressPercent()).isEqualTo(50);
        assertThat(projects.get(0).getCompletionState()).isEqualTo("IN_PROGRESS");
    }

    private Project buildProject(Long id, String name) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        project.setStatus(Project.Status.IN_PROGRESS);
        project.setPriority(Project.Priority.MEDIUM);
        project.setStartDate(LocalDate.of(2026, 1, 1));
        project.setEndDate(LocalDate.of(2026, 12, 31));
        project.setColor("#0052CC");
        return project;
    }

    private Task buildTask(Long id, Project project, int completionPercent) {
        Task task = new Task();
        task.setId(id);
        task.setTitle("Analiz");
        task.setProject(project);
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setPriority(Task.Priority.MEDIUM);
        task.setStartDate(LocalDate.of(2026, 3, 1));
        task.setEndDate(LocalDate.of(2026, 3, 10));
        task.setCompletionPercent(completionPercent);
        return task;
    }
}
