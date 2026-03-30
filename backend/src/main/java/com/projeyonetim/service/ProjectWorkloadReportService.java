package com.projeyonetim.service;

import com.projeyonetim.dto.ProjectWorkloadReportDto;
import com.projeyonetim.model.Project;
import com.projeyonetim.model.SubTask;
import com.projeyonetim.model.Task;
import com.projeyonetim.model.User;
import com.projeyonetim.repository.SubTaskRepository;
import com.projeyonetim.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProjectWorkloadReportService {

    private static final Set<Task.Status> OPEN_STATUSES =
            Set.of(Task.Status.TODO, Task.Status.IN_PROGRESS, Task.Status.IN_REVIEW);

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private SubTaskRepository subTaskRepository;

    @Transactional(readOnly = true)
    public ProjectWorkloadReportDto generateReport(Long personId, Long projectId, String statusFilter) {
        Map<RowKey, MutableRow> rows = new LinkedHashMap<>();

        for (Task task : taskRepository.findAllWithProjectAndAssignee()) {
            if (!matchesTaskFilters(task, personId, projectId, statusFilter)) {
                continue;
            }

            User assignee = task.getAssignee();
            Project project = task.getProject();
            RowKey key = new RowKey(assignee.getId(), project.getId());
            MutableRow row = rows.computeIfAbsent(key, ignored -> MutableRow.from(assignee, project));

            row.taskCount += 1;
            row.taskEstimatedHours += safeInt(task.getEstimatedHours());
            row.totalCompletionPercent += safeInt(task.getCompletionPercent());
            row.totalItemsTracked += 1;
            row.incrementStatus(task.getStatus());
            row.taskDetails.add(new ProjectWorkloadReportDto.ItemDetail(
                    "TASK",
                    task.getId(),
                    task.getTitle(),
                    null,
                    task.getStatus().name(),
                    task.getPriority().name(),
                    task.getEstimatedHours(),
                    task.getCompletionPercent(),
                    task.getStartDate(),
                    task.getEndDate()
            ));
        }

        for (SubTask subTask : subTaskRepository.findAllWithTaskProjectAndAssignee()) {
            if (!matchesSubTaskFilters(subTask, personId, projectId, statusFilter)) {
                continue;
            }

            User assignee = subTask.getAssignee();
            Project project = subTask.getTask().getProject();
            RowKey key = new RowKey(assignee.getId(), project.getId());
            MutableRow row = rows.computeIfAbsent(key, ignored -> MutableRow.from(assignee, project));

            row.subTaskCount += 1;
            row.subTaskEstimatedHours += safeInt(subTask.getEstimatedHours());
            row.totalCompletionPercent += safeInt(subTask.getCompletionPercent());
            row.totalItemsTracked += 1;
            row.incrementStatus(subTask.getStatus());
            row.subTaskDetails.add(new ProjectWorkloadReportDto.ItemDetail(
                    "SUBTASK",
                    subTask.getId(),
                    subTask.getTitle(),
                    subTask.getTask() != null ? subTask.getTask().getTitle() : null,
                    subTask.getStatus().name(),
                    subTask.getPriority().name(),
                    subTask.getEstimatedHours(),
                    subTask.getCompletionPercent(),
                    subTask.getStartDate(),
                    subTask.getEndDate()
            ));
        }

        List<ProjectWorkloadReportDto.Row> resultRows = rows.values().stream()
                .map(MutableRow::toDto)
                .sorted(Comparator
                        .comparingInt(ProjectWorkloadReportDto.Row::getTotalItemCount).reversed()
                        .thenComparingInt(ProjectWorkloadReportDto.Row::getTotalEstimatedHours).reversed()
                        .thenComparing(ProjectWorkloadReportDto.Row::getPersonName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ProjectWorkloadReportDto.Row::getProjectName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        ProjectWorkloadReportDto.Summary summary = buildSummary(resultRows);
        return new ProjectWorkloadReportDto(summary, resultRows);
    }

    private boolean matchesTaskFilters(Task task, Long personId, Long projectId, String statusFilter) {
        if (task.getAssignee() == null || task.getProject() == null) {
            return false;
        }
        if (personId != null && !personId.equals(task.getAssignee().getId())) {
            return false;
        }
        if (projectId != null && !projectId.equals(task.getProject().getId())) {
            return false;
        }
        return matchesStatusFilter(task.getStatus(), statusFilter);
    }

    private boolean matchesSubTaskFilters(SubTask subTask, Long personId, Long projectId, String statusFilter) {
        if (subTask.getAssignee() == null || subTask.getTask() == null || subTask.getTask().getProject() == null) {
            return false;
        }
        if (personId != null && !personId.equals(subTask.getAssignee().getId())) {
            return false;
        }
        if (projectId != null && !projectId.equals(subTask.getTask().getProject().getId())) {
            return false;
        }
        return matchesStatusFilter(subTask.getStatus(), statusFilter);
    }

    private boolean matchesStatusFilter(Task.Status status, String statusFilter) {
        String normalizedFilter = String.valueOf(statusFilter == null ? "ALL" : statusFilter)
                .trim()
                .toUpperCase(Locale.ROOT);

        if (normalizedFilter.isBlank() || "ALL".equals(normalizedFilter)) {
            return true;
        }
        if ("OPEN".equals(normalizedFilter)) {
            return OPEN_STATUSES.contains(status);
        }
        if ("DONE".equals(normalizedFilter)) {
            return status == Task.Status.COMPLETED;
        }
        return status.name().equals(normalizedFilter);
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private ProjectWorkloadReportDto.Summary buildSummary(List<ProjectWorkloadReportDto.Row> rows) {
        int totalTaskCount = rows.stream().mapToInt(ProjectWorkloadReportDto.Row::getTaskCount).sum();
        int totalSubTaskCount = rows.stream().mapToInt(ProjectWorkloadReportDto.Row::getSubTaskCount).sum();
        int totalItemCount = rows.stream().mapToInt(ProjectWorkloadReportDto.Row::getTotalItemCount).sum();
        int openItemCount = rows.stream().mapToInt(ProjectWorkloadReportDto.Row::getOpenItemCount).sum();
        int completedItemCount = rows.stream().mapToInt(ProjectWorkloadReportDto.Row::getCompletedItemCount).sum();
        int cancelledItemCount = rows.stream().mapToInt(ProjectWorkloadReportDto.Row::getCancelledItemCount).sum();
        int totalEstimatedHours = rows.stream().mapToInt(ProjectWorkloadReportDto.Row::getTotalEstimatedHours).sum();
        int uniquePersonnelCount = (int) rows.stream().map(ProjectWorkloadReportDto.Row::getPersonId).distinct().count();
        int uniqueProjectCount = (int) rows.stream().map(ProjectWorkloadReportDto.Row::getProjectId).distinct().count();

        return new ProjectWorkloadReportDto.Summary(
                rows.size(),
                uniquePersonnelCount,
                uniqueProjectCount,
                totalTaskCount,
                totalSubTaskCount,
                totalItemCount,
                openItemCount,
                completedItemCount,
                cancelledItemCount,
                totalEstimatedHours
        );
    }

    private record RowKey(Long personId, Long projectId) {
    }

    private static class MutableRow {
        private Long personId;
        private String personName;
        private String username;
        private String personRole;
        private Long projectId;
        private String projectName;
        private String projectStatus;
        private String projectColor;
        private int taskCount;
        private int subTaskCount;
        private int todoItemCount;
        private int inProgressItemCount;
        private int inReviewItemCount;
        private int completedItemCount;
        private int cancelledItemCount;
        private int taskEstimatedHours;
        private int subTaskEstimatedHours;
        private int totalCompletionPercent;
        private int totalItemsTracked;
        private final List<ProjectWorkloadReportDto.ItemDetail> taskDetails = new ArrayList<>();
        private final List<ProjectWorkloadReportDto.ItemDetail> subTaskDetails = new ArrayList<>();

        private static MutableRow from(User assignee, Project project) {
            MutableRow row = new MutableRow();
            row.personId = assignee.getId();
            row.personName = assignee.getFullName();
            row.username = assignee.getUsername();
            row.personRole = assignee.getRole() != null ? assignee.getRole().name() : null;
            row.projectId = project.getId();
            row.projectName = project.getName();
            row.projectStatus = project.getStatus() != null ? project.getStatus().name() : null;
            row.projectColor = project.getColor();
            return row;
        }

        private void incrementStatus(Task.Status status) {
            if (status == null) {
                return;
            }

            switch (status) {
                case TODO -> todoItemCount += 1;
                case IN_PROGRESS -> inProgressItemCount += 1;
                case IN_REVIEW -> inReviewItemCount += 1;
                case COMPLETED -> completedItemCount += 1;
                case CANCELLED -> cancelledItemCount += 1;
                default -> {
                }
            }
        }

        private ProjectWorkloadReportDto.Row toDto() {
            int totalItemCount = taskCount + subTaskCount;
            int openItemCount = todoItemCount + inProgressItemCount + inReviewItemCount;
            int totalEstimatedHours = taskEstimatedHours + subTaskEstimatedHours;
            int averageCompletionPercent = totalItemsTracked == 0 ? 0 : Math.round((float) totalCompletionPercent / totalItemsTracked);

            taskDetails.sort(Comparator
                    .comparing(ProjectWorkloadReportDto.ItemDetail::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ProjectWorkloadReportDto.ItemDetail::getTitle, String.CASE_INSENSITIVE_ORDER));
            subTaskDetails.sort(Comparator
                    .comparing(ProjectWorkloadReportDto.ItemDetail::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ProjectWorkloadReportDto.ItemDetail::getParentTaskTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(ProjectWorkloadReportDto.ItemDetail::getTitle, String.CASE_INSENSITIVE_ORDER));

            return new ProjectWorkloadReportDto.Row(
                    personId,
                    personName,
                    username,
                    personRole,
                    projectId,
                    projectName,
                    projectStatus,
                    projectColor,
                    taskCount,
                    subTaskCount,
                    totalItemCount,
                    openItemCount,
                    completedItemCount,
                    cancelledItemCount,
                    todoItemCount,
                    inProgressItemCount,
                    inReviewItemCount,
                    taskEstimatedHours,
                    subTaskEstimatedHours,
                    totalEstimatedHours,
                    averageCompletionPercent,
                    new ArrayList<>(taskDetails),
                    new ArrayList<>(subTaskDetails)
            );
        }
    }
}
