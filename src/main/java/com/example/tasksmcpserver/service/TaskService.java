package com.example.tasksmcpserver.service;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    @McpTool(name = "createTask", description = "Creates a new task with a given title")
    public String createTask(@McpToolParam(description = "The title of the task") String title) {
        // Your logic to create task
        String id = UUID.randomUUID().toString().substring(0, 8);
        Task task = new Task(id, title, "TODO");
        tasks.put(id, task);

        return String.format("{\"id\": \"%s\", \"title\": \"%s\", \"status\": \"%s\"}",
                task.id(), task.title(), task.status());
    }

    @McpTool(name = "listTasks", description = "Lists all tasks currently in the task manager")
    public String listTasks() {
        if (tasks.isEmpty()) {
            return "No tasks found.";
        }

        return tasks.values().stream()
                .map(t -> String.format("- [%s] %s (ID: %s)", t.status(), t.title(), t.id()))
                .collect(Collectors.joining("\n"));
    }

    @McpTool(name = "getTaskStatus", description = "Retrieves the status of a task by its ID")
    public String getTaskStatus(@McpToolParam(description = "Retrieves the status of a task by its ID") String taskId) {
        // Your logic to fetch task status
        Task task = tasks.get(taskId);
        if (task == null) {
            return "Error: Task with ID " + taskId + " not found.";
        }
        return String.format("{\"id\": \"%s\", \"title\": \"%s\", \"status\": \"%s\"}",
                task.id(), task.title(), task.status());
    }

    @McpTool(name = "updateTaskStatus", description = "Update the status of an existing task by its ID")
    public String updateTaskStatus(
            @McpToolParam(description = "The ID of the task") String taskId,
            @McpToolParam(description = "The new status (e.g., TODO, IN_PROGRESS, DONE)") String newStatus) {
        Task task = tasks.get(taskId);
        if (task == null) {
            return "Error: Task with ID " + taskId + " not found.";
        }

        Task updatedTask = new Task(task.id(), task.title(), newStatus.toUpperCase());
        tasks.put(taskId, updatedTask);
        return "Task " + taskId + " status updated to " + updatedTask.status();
    }

    @McpTool(name = "deleteTask", description = "Deletes a task by its ID")
    public String deleteTask(@McpToolParam(description = "The ID of the task") String taskId) {
        Task removed = tasks.remove(taskId);
        if (removed == null) {
            return "Error: Task with ID " + taskId + " not found.";
        }
        return "Task '" + removed.title() + "' (ID: " + taskId + ") deleted successfully.";
    }


    // ---------------------------

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public Collection<Task> getAllTasks() {
        return new ArrayList<>(tasks.values());
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // Send the current state immediately
        try {
            emitter.send(SseEmitter.event().name("tasks").data(getAllTasks()));
        } catch (Exception e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    private void notifyClients() {
        List<Task> snapshot = new ArrayList<>(tasks.values());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("tasks").data(snapshot));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }
}
