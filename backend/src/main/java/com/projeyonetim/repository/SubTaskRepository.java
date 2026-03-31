package com.projeyonetim.repository;

import com.projeyonetim.model.SubTask;
import com.projeyonetim.model.Task;
import com.projeyonetim.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubTaskRepository extends JpaRepository<SubTask, Long> {
    List<SubTask> findByTask(Task task);

    List<SubTask> findByTaskId(Long taskId);

    List<SubTask> findByAssignee(User user);

    @Query("SELECT COUNT(st) FROM SubTask st WHERE st.assignee = :user AND st.status NOT IN (:excludeStatuses)")
    long countOpenSubTasksByUser(User user, List<Task.Status> excludeStatuses);

    @Query("SELECT COUNT(st) FROM SubTask st WHERE st.assignee = :user AND st.status = :status")
    long countByAssigneeAndStatus(User user, Task.Status status);

    @Query("SELECT COUNT(st) FROM SubTask st WHERE st.task.project.id = :projectId")
    long countByProjectId(Long projectId);

    @Query("SELECT COUNT(st) FROM SubTask st WHERE st.task.project.id = :projectId AND st.status = :status")
    long countByProjectIdAndStatus(Long projectId, Task.Status status);

    @Query("SELECT DISTINCT st FROM SubTask st LEFT JOIN FETCH st.task t LEFT JOIN FETCH t.project LEFT JOIN FETCH st.assignee")
    List<SubTask> findAllWithTaskProjectAndAssignee();
}
