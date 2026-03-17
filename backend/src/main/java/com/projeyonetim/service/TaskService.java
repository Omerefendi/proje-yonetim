package com.projeyonetim.service;

import com.projeyonetim.model.Task;
import com.projeyonetim.model.SubTask;
import com.projeyonetim.model.Project;
import com.projeyonetim.repository.TaskRepository;
import com.projeyonetim.repository.SubTaskRepository;
import com.projeyonetim.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TaskService {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private SubTaskRepository subTaskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    public List<Task> getTasksByProject(Long projectId) {
        return taskRepository.findByProjectIdOrdered(projectId);
    }

    public Optional<Task> getTaskById(Long id) {
        return taskRepository.findById(id);
    }

    @Transactional
    public Task createTask(Task task) {
        return taskRepository.save(task);
    }

    @Transactional
    public Task updateTask(Long id, Task updateData) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Görev bulunamadı: " + id));
        task.setTitle(updateData.getTitle());
        task.setDescription(updateData.getDescription());
        task.setStatus(updateData.getStatus());
        task.setPriority(updateData.getPriority());
        task.setStartDate(updateData.getStartDate());
        task.setEndDate(updateData.getEndDate());
        task.setAssignee(updateData.getAssignee());
        task.setEstimatedHours(updateData.getEstimatedHours());
        task.setCompletionPercent(updateData.getCompletionPercent());
        return taskRepository.save(task);
    }

    @Transactional
    public void deleteTask(Long id) {
        taskRepository.deleteById(id);
    }

    // SubTask operations
    public Optional<SubTask> getSubTaskById(Long id) {
        return subTaskRepository.findById(id);
    }

    public List<SubTask> getSubTasksByTask(Long taskId) {
        return subTaskRepository.findByTaskId(taskId);
    }

    @Transactional
    public SubTask createSubTask(SubTask subTask) {
        SubTask saved = subTaskRepository.save(subTask);
        recalculateTaskCompletion(saved.getTask().getId());
        return saved;
    }

    @Transactional
    public SubTask updateSubTask(Long id, SubTask updateData) {
        SubTask subTask = subTaskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alt görev bulunamadı: " + id));
        subTask.setTitle(updateData.getTitle());
        subTask.setDescription(updateData.getDescription());
        subTask.setStatus(updateData.getStatus());
        subTask.setPriority(updateData.getPriority());
        subTask.setStartDate(updateData.getStartDate());
        subTask.setEndDate(updateData.getEndDate());
        subTask.setAssignee(updateData.getAssignee());
        subTask.setCompletionPercent(updateData.getCompletionPercent());
        subTask.setCompleted(updateData.isCompleted());
        SubTask saved = subTaskRepository.save(subTask);
        recalculateTaskCompletion(saved.getTask().getId());
        return saved;
    }

    @Transactional
    public void deleteSubTask(Long id) {
        SubTask subTask = subTaskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alt görev bulunamadı: " + id));
        Long taskId = subTask.getTask().getId();
        subTaskRepository.deleteById(id);
        subTaskRepository.flush();
        recalculateTaskCompletion(taskId);
    }

    /**
     * Recalculate task completion percent from the average of its subtasks.
     * If there are no subtasks, the task's completionPercent remains unchanged.
     */
    private void recalculateTaskCompletion(Long taskId) {
        List<SubTask> subtasks = subTaskRepository.findByTaskId(taskId);
        if (subtasks.isEmpty()) {
            return; // No subtasks, keep task's own completionPercent
        }
        int total = 0;
        for (SubTask st : subtasks) {
            total += (st.getCompletionPercent() != null ? st.getCompletionPercent() : 0);
        }
        int avg = Math.round((float) total / subtasks.size());
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setCompletionPercent(avg);
            taskRepository.save(task);
        }
    }
}
