package com.example.multitenant.repository;

import com.example.multitenant.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
    Long countByProjectId(Long projectId);
}