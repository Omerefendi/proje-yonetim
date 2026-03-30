package com.projeyonetim.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectWorkloadReportDto {
    private Summary summary;
    private List<Row> rows = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private int rowCount;
        private int uniquePersonnelCount;
        private int uniqueProjectCount;
        private int totalTaskCount;
        private int totalSubTaskCount;
        private int totalItemCount;
        private int openItemCount;
        private int completedItemCount;
        private int cancelledItemCount;
        private int totalEstimatedHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
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
        private int totalItemCount;
        private int openItemCount;
        private int completedItemCount;
        private int cancelledItemCount;
        private int todoItemCount;
        private int inProgressItemCount;
        private int inReviewItemCount;
        private int taskEstimatedHours;
        private int subTaskEstimatedHours;
        private int totalEstimatedHours;
        private int averageCompletionPercent;
        private List<ItemDetail> taskDetails = new ArrayList<>();
        private List<ItemDetail> subTaskDetails = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDetail {
        private String itemType;
        private Long itemId;
        private String title;
        private String parentTaskTitle;
        private String status;
        private String priority;
        private Integer estimatedHours;
        private Integer completionPercent;
        private LocalDate startDate;
        private LocalDate endDate;
    }
}
