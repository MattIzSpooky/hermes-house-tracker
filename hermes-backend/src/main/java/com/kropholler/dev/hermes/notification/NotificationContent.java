package com.kropholler.dev.hermes.notification;

import java.util.List;
import java.util.UUID;

public record NotificationContent(String title, String body, List<UUID> listingIds) {}
