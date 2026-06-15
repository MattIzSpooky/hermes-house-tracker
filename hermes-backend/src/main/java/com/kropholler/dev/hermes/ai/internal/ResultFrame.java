package com.kropholler.dev.hermes.ai.internal;

import com.kropholler.dev.hermes.ai.ChatListingCard;
import java.util.List;

public record ResultFrame(String type, List<ChatListingCard> listings) {}
