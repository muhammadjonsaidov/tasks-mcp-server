package com.example.tasksmcpserver.service;

public record Task(String id, String title, String description, TaskStatus status) {}