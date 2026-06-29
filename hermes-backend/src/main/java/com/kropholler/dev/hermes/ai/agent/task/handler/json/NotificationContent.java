package com.kropholler.dev.hermes.ai.agent.task.handler.json;

import java.util.List;
import java.util.UUID;

public record NotificationContent(String title, String body, List<UUID> listingIds) {}
