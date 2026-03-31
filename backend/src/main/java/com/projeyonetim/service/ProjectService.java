package com.projeyonetim.service;

import com.projeyonetim.model.Project;
import com.projeyonetim.model.Task;
import com.projeyonetim.model.User;
import com.projeyonetim.repository.ProjectRepository;
import com.projeyonetim.repository.TaskRepository;
import com.projeyonetim.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectDocumentService projectDocumentService;

    public List<Project> getAllProjects() {
        List<Project> projects = projectRepository.findAllByOrderByStartDateAsc();
        projects.forEach(this::enrichProjectSummary);
        return projects;
    }

    public Optional<Project> getProjectById(Long id) {
        Optional<Project> project = projectRepository.findById(id);
        project.ifPresent(this::enrichProjectSummary);
        return project;
    }

    public List<Project> getProjectsByStatus(Project.Status status) {
        return projectRepository.findByStatus(status);
    }

    public List<Project> getProjectsByUser(User user) {
        return projectRepository.findByOwnerOrMember(user);
    }

    @Transactional
    public Project createProject(Project project) {
        return projectRepository.save(project);
    }

    @Transactional
    public Project updateProject(Long id, Project updateData) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proje bulunamadı: " + id));
        project.setName(updateData.getName());
        project.setDescription(updateData.getDescription());
        project.setStatus(updateData.getStatus());
        project.setPriority(updateData.getPriority());
        project.setStartDate(updateData.getStartDate());
        project.setEndDate(updateData.getEndDate());
        project.setColor(updateData.getColor());
        if (updateData.getOwner() != null) {
            project.setOwner(updateData.getOwner());
        }
        return projectRepository.save(project);
    }

    @Transactional
    public void deleteProject(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proje bulunamadı: " + id));
        projectDocumentService.deleteAllForProject(id);
        projectRepository.delete(project);
    }

    @Transactional
    public Project addMember(Long projectId, Long userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Proje bulunamadı"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));
        if (!project.getMembers().contains(user)) {
            project.getMembers().add(user);
        }
        return projectRepository.save(project);
    }

    @Transactional
    public Project removeMember(Long projectId, Long userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Proje bulunamadı"));
        project.getMembers().removeIf(u -> u.getId().equals(userId));
        return projectRepository.save(project);
    }

    public long countByStatus(Project.Status status) {
        return projectRepository.countByStatus(status);
    }

    public void enrichProjectSummary(Project project) {
        if (project == null || project.getId() == null) {
            return;
        }

        List<Task> tasks = taskRepository.findByProjectIdOrdered(project.getId());
        int taskCount = tasks.size();
        project.setTaskCount(taskCount);

        if (taskCount == 0) {
            project.setProgressPercent(0);
            project.setCompletionState("NO_TASKS");
            return;
        }

        int totalPercent = tasks.stream()
                .mapToInt(task -> task.getCompletionPercent() != null ? task.getCompletionPercent() : 0)
                .sum();
        int averagePercent = Math.round((float) totalPercent / taskCount);

        project.setProgressPercent(averagePercent);

        if (averagePercent >= 100) {
            project.setCompletionState("COMPLETED");
        } else if (averagePercent <= 0) {
            project.setCompletionState("NOT_STARTED");
        } else {
            project.setCompletionState("IN_PROGRESS");
        }
    }
}
