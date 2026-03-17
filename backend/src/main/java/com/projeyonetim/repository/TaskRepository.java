package com.projeyonetim.repository;

import com.projeyonetim.model.Task;
import com.projeyonetim.model.Project;
import com.projeyonetim.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByProject(Project project);

    List<Task> findByProjectId(Long projectId);

    List<Task> findByAssignee(User user);

    List<Task> findByStatus(Task.Status status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.assignee = :user AND t.status NOT IN (:excludeStatuses)")
    long countOpenTasksByUser(User user, List<Task.Status> excludeStatuses);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.assignee = :user AND t.status = :status")
    long countByAssigneeAndStatus(User user, Task.Status status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.status = :status")
    long countByStatus(Task.Status status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.project.id = :projectId AND t.status = :status")
    long countByProjectIdAndStatus(Long projectId, Task.Status status);

    @Query("SELECT t FROM Task t WHERE t.project.id = :projectId ORDER BY t.orderIndex ASC")
    List<Task> findByProjectIdOrdered(Long projectId);
}
