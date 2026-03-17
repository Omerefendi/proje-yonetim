package com.projeyonetim.repository;

import com.projeyonetim.model.Project;
import com.projeyonetim.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByOwner(User owner);

    List<Project> findByStatus(Project.Status status);

    @Query("SELECT p FROM Project p WHERE p.owner = :user OR :user MEMBER OF p.members")
    List<Project> findByOwnerOrMember(User user);

    @Query("SELECT COUNT(p) FROM Project p WHERE p.status = :status")
    long countByStatus(Project.Status status);

    List<Project> findAllByOrderByStartDateAsc();

    @Query("SELECT COUNT(p) FROM Project p WHERE p.priority = :priority")
    long countByPriority(Project.Priority priority);
}
